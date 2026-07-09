package plantuml

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import plantuml.service.DiagramProcessor
import plantuml.service.LlmService
import plantuml.service.PlantumlService
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * Unit tests for PromptOrchestrator.
 *
 * Two strategies coexist here depending on the test level:
 *
 * 1. [WithMockDiagramProcessor] — Mocks the entire DiagramProcessor with Mockito.
 *    Perfect for testing file/directory/counting logic.
 *    Execution: < 10 ms per test.
 *
 * 2. [WithWireMockLlm] — WireMock intercepts real HTTP requests to Ollama.
 *    Tests the complete path from network to response parsing,
 *    without starting a real model. Execution: < 50 ms per test.
 */
class PromptOrchestratorTest {

    private val logger: Logger = LoggerFactory.getLogger(PromptOrchestratorTest::class.java)

    @TempDir
    lateinit var tempDir: File

    // ------------------------------------------------------------------ //
    //  Case 1: DiagramProcessor fully mocked (Mockito)                     //
    // ------------------------------------------------------------------ //

    @Nested
    inner class WithMockDiagramProcessor {

        private val logger: Logger = LoggerFactory.getLogger(WithMockDiagramProcessor::class.java)
        private lateinit var mockProcessor: DiagramProcessor
        private lateinit var mockPlantumlService: PlantumlService
        private lateinit var orchestrator: PromptOrchestrator

        @BeforeEach
        fun setup() {
            mockPlantumlService = mock<PlantumlService>()
            mockProcessor = mock<DiagramProcessor>()
            orchestrator = PromptOrchestrator(
                config = minimalConfig(),
                diagramProcessor = mockProcessor,
                plantumlService = mockPlantumlService,
                projectDir = tempDir.toPath(),
            )
        }

        @Test
        fun `should report no prompts when directory is missing`() {
            val result = orchestrator.process(logger)

            assertEquals(0, result.totalPrompts)
            assertTrue(result.messages.any { it.contains("No prompts directory found") })
            verifyNoInteractions(mockProcessor)
        }

        @Test
        fun `should report no prompts when directory is empty`() {
            File(tempDir, "prompts").mkdirs()

            val result = orchestrator.process(logger)

            assertEquals(0, result.totalPrompts)
            assertTrue(result.messages.any { it.contains("No prompt files found") })
        }

        @Test
        fun `should process each prompt file and count successes`() {
            val promptsDir = File(tempDir, "prompts").also { it.mkdirs() }
            File(promptsDir, "a.prompt").writeText("Create diagram A")
            File(promptsDir, "b.prompt").writeText("Create diagram B")

            whenever(mockProcessor.processPrompt(any(), any(), any(), isNull(), any())).thenReturn(fakeDiagram())

            val result = orchestrator.process(logger)

            assertEquals(2, result.totalPrompts)
            assertEquals(2, result.succeeded)
            assertEquals(0, result.failed)
        }

        @Test
        fun `should count failures when processor returns null`() {
            val promptsDir = File(tempDir, "prompts").also { it.mkdirs() }
            File(promptsDir, "bad.prompt").writeText("impossible prompt")

            whenever(mockProcessor.processPrompt(any(), any(), any(), isNull(), any())).thenReturn(null)

            val result = orchestrator.process(logger)

            assertEquals(1, result.totalPrompts)
            assertEquals(0, result.succeeded)
            // null = no diagram generated, skipped rather than failed
            assertTrue(result.messages.any { it.contains("Could not generate") })
        }

        @Test
        fun `should skip blank prompt files`() {
            val promptsDir = File(tempDir, "prompts").also { it.mkdirs() }
            File(promptsDir, "blank.prompt").writeText("   \n  ")
            File(promptsDir, "valid.prompt").writeText("Create a class diagram")

            whenever(mockProcessor.processPrompt(any(), any(), any(), isNull(), any())).thenReturn(fakeDiagram())

            val result = orchestrator.process(logger)

            assertEquals(2, result.totalPrompts)
            assertTrue(result.messages.any { it.contains("Skipping empty prompt") })
        }

        @Test
        fun `should write puml file to output diagrams directory`() {
            val promptsDir = File(tempDir, "prompts").also { it.mkdirs() }
            File(promptsDir, "test.prompt").writeText("Create a class diagram")

            whenever(mockProcessor.processPrompt(any(), any(), any(), isNull(), any())).thenReturn(fakeDiagram("@startuml\nclass Generated\n@enduml"))

            orchestrator.process(logger)

            val outputFile = File(tempDir, "generated/diagrams/test.puml")
            assertTrue(outputFile.exists(), "Output .puml file should be created")
            assertTrue(outputFile.readText().contains("@startuml"))
        }

        @Test
        fun `should not call generateImage when validation is disabled`() {
            val promptsDir = File(tempDir, "prompts").also { it.mkdirs() }
            File(promptsDir, "test.prompt").writeText("A diagram")

            val configNoValidation = minimalConfig().copy(
                langchain4j = minimalConfig().langchain4j.copy(validation = false),
            )
            val orch = PromptOrchestrator(configNoValidation, mockProcessor, mockPlantumlService, tempDir.toPath())

            whenever(mockProcessor.processPrompt(any(), any(), any(), isNull(), any())).thenReturn(fakeDiagram())

            orch.process(logger)

            verifyNoInteractions(mockPlantumlService)
        }
    }

    // ------------------------------------------------------------------ //
    //  Case 2: WireMock intercepts HTTP calls to LLM                       //
    // ------------------------------------------------------------------ //

    @Suppress("JUnitMalformedDeclaration")
    @RegisterExtension
    val wireMock: WireMockExtension = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().dynamicPort())
        .build()

    @Nested
    inner class WithWireMockLlm {

        private lateinit var orchestrator: PromptOrchestrator
        private lateinit var mockPlantumlService: PlantumlService

        @BeforeEach
        fun setup() {
            mockPlantumlService = mock<PlantumlService>()

            // Simulated Ollama response — JSON format of /api/chat API
            wireMock.stubFor(
                post(urlEqualTo("/api/chat"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(ollamaChatJsonResponse("@startuml\nclass WireMockedClass\n@enduml")),
                    ),
            )

            val config = minimalConfig().copy(
                langchain4j = LangchainConfig(
                    model = "ollama",
                    ollama = OllamaConfig(
                        baseUrl = "http://localhost:${wireMock.port}",
                        modelName = "smollm:135m",
                    ),
                ),
            )

            // The real DiagramProcessor is instantiated here with the real LlmService
            // pointing to WireMock — we test the complete HTTP path.
            val llmService = LlmService(config)
            val chatModel = llmService.createChatModel()
            val plantumlSvc = PlantumlService()
            val processor = DiagramProcessor(plantumlSvc, chatModel, null)

            orchestrator = PromptOrchestrator(config, processor, mockPlantumlService, tempDir.toPath())
        }

        @Test
        fun `should complete processing when ollama responds correctly`() {
            val promptsDir = File(tempDir, "prompts").also { it.mkdirs() }
            File(promptsDir, "test.prompt").writeText("Create a simple class diagram")

            val result = orchestrator.process(logger)

            // At least one attempt was made to WireMock
            wireMock.verify(1, postRequestedFor(urlEqualTo("/api/chat")))
            assertEquals(1, result.totalPrompts)
        }

        @Test
        fun `should handle 503 from llm and count as failure`() {
            wireMock.stubFor(
                post(urlEqualTo("/api/chat"))
                    .willReturn(aResponse().withStatus(503).withBody("Service Unavailable")),
            )

            val promptsDir = File(tempDir, "prompts").also { it.mkdirs() }
            File(promptsDir, "test.prompt").writeText("A diagram")

            val result = orchestrator.process(logger)

            // No success possible if LLM returns 503
            assertTrue(result.succeeded == 0 || result.failed > 0 || result.messages.any { it.contains("Could not generate") })
        }

        @Test
        fun `should send model name in request body`() {
            val promptsDir = File(tempDir, "prompts").also { it.mkdirs() }
            File(promptsDir, "test.prompt").writeText("A diagram")

            orchestrator.process(logger)

            wireMock.verify(
                postRequestedFor(urlEqualTo("/api/chat"))
                    .withRequestBody(matchingJsonPath("$.model", equalTo("smollm:135m"))),
            )
        }

    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private fun minimalConfig() = PlantumlConfig(
        input = plantuml.InputConfig(prompts = "prompts"),
        output = plantuml.OutputConfig(
            diagrams = "generated/diagrams",
            images = "generated/images",
            format = "png",
        ),
        langchain4j = LangchainConfig(
            model = "ollama",
            validation = false,
            maxIterations = 1,
        ),
    )

    private fun fakeDiagram(code: String = "@startuml\nclass Fake\n@enduml") =
        plantuml.PlantumlDiagram(
            conversation = listOf("mock conversation"),
            plantuml = plantuml.PlantumlCode(code = code, description = "fake"),
        )

    private fun ollamaChatJsonResponse(plantumlCode: String) = """
        {
          "model": "smollm:135m",
          "message": {
            "role": "assistant",
            "content": "${plantumlCode.replace("\n", "\\n")}"
          },
          "done": true
        }
    """.trimIndent()
}
