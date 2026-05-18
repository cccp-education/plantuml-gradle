package plantuml

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import plantuml.tasks.GeneratePlantumlDiagramsTask
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for GeneratePlantumlDiagramsTask
 * 
 * Tests covered:
 * - Early exit when prompts directory does not exist
 * - Early exit when no .prompt files are found
 * - Processing prompt files with WireMock LLM
 * - Override of plantuml.prompts.dir
 * - Override of plantuml.langchain4j.model
 */
class GeneratePlantumlDiagramsTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Suppress("JUnitMalformedDeclaration")
    @RegisterExtension
    val wireMock: WireMockExtension = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().dynamicPort())
        .build()

    private lateinit var project: Project
    private lateinit var task: GeneratePlantumlDiagramsTask

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()
        
        project.pluginManager.apply("com.cheroliv.plantuml")
        task = project.tasks.getByName("generatePlantumlDiagrams") as GeneratePlantumlDiagramsTask
        
        // Configure WireMock to return a valid response
        wireMock.stubFor(
            post(urlEqualTo("/api/chat"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaChatJsonResponse("@startuml\nclass WireMockedClass\n@enduml")),
                ),
        )
        
        // Configure syntax validation to return valid
        wireMock.stubFor(
            post(urlPathMatching("/validate.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("<result><valid>true</valid></result>"),
                ),
        )
    }

    @Test
    fun `should exit early when prompts directory does not exist`() {
        // Given: prompts directory does not exist (not created)
        
        // When: execute the task
        task.processPrompts()
        
        // Then: no WireMock call (early exit)
        wireMock.verify(0, postRequestedFor(urlEqualTo("/api/chat")))
        assertTrue(true, "Task should complete without throwing exception")
    }

    @Test
    fun `should exit early when no prompt files found`() {
        // Given: empty prompts directory
        val promptsDir = File(tempDir, "prompts")
        promptsDir.mkdirs()
        
        // Non-.prompt file (should be ignored)
        File(promptsDir, "readme.txt").writeText("This is not a prompt file")
        
        // When: execute the task
        task.processPrompts()
        
        // Then: no WireMock call (no .prompt files)
        wireMock.verify(0, postRequestedFor(urlEqualTo("/api/chat")))
        assertTrue(true, "Task should complete without processing non-prompt files")
    }

    @Test
    fun `should process all prompt files in directory`() {
        // Given: prompts directory with 2 .prompt files
        val promptsDir = File(tempDir, "prompts")
        promptsDir.mkdirs()
        val promptFile1 = File(promptsDir, "test1.prompt")
        val promptFile2 = File(promptsDir, "test2.prompt")
        promptFile1.writeText("Create a class diagram")
        promptFile2.writeText("Create a sequence diagram")
        
        // Configure with WireMock
        setupProjectConfig()
        
        // When: execute the task
        task.processPrompts()
        
        // Then: prompt files have been deleted
        assertFalse(promptFile1.exists(), "First prompt should be deleted")
        assertFalse(promptFile2.exists(), "Second prompt should be deleted")
        
        // And: WireMock called 2 times
        wireMock.verify(2, postRequestedFor(urlEqualTo("/api/chat")))
    }

    @Test
    fun `should respect plantuml prompts dir property override`() {
        // Given: custom prompts directory
        val customPromptsDir = File(tempDir, "custom-prompts")
        customPromptsDir.mkdirs()
        val promptFile = File(customPromptsDir, "test.prompt")
        promptFile.writeText("Create a class diagram")
        
        // Override prompts directory
        project.extensions.extraProperties.set("plantuml.prompts.dir", "custom-prompts")
        
        // Configure with WireMock
        setupProjectConfig()
        
        // When: execute the task
        task.processPrompts()
        
        // Then: file has been processed (deleted)
        assertFalse(promptFile.exists(), "Custom prompt should be processed")
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/chat")))
    }

    @Test
    fun `should override LLM model name from command line property`() {
        // Given: configuration with ollama as default model
        val configPath = File(tempDir, "plantuml-context.yml")
        configPath.writeText("""
            langchain4j:
              model: "ollama"
              maxIterations: 1
              validation: false
              ollama:
                baseUrl: "http://localhost:${wireMock.port}"
                modelName: "smollm:135m"
        """.trimIndent())
        
        // Given: a prompt file
        val promptsDir = File(tempDir, "prompts")
        promptsDir.mkdirs()
        val promptFile = File(promptsDir, "test.prompt")
        promptFile.writeText("Create a diagram")
        
        // Override ollama model name
        // Must be done AFTER writing the YAML file
        project.extensions.extraProperties.set("plantuml.langchain4j.ollama.modelName", "llama2")
        
        // When: execute the task
        task.processPrompts()
        
        // Then: file has been processed
        assertFalse(promptFile.exists(), "Prompt should be processed with model override")
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/chat")))
        
        // And: the model sent is "llama2" (override)
        wireMock.verify(
            postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("llama2"))),
        )
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private fun setupProjectConfig() {
        val configPath = File(tempDir, "plantuml-context.yml")
        configPath.writeText("""
            langchain4j:
              model: "ollama"
              maxIterations: 1
              validation: false
              ollama:
                baseUrl: "http://localhost:${wireMock.port}"
                modelName: "smollm:135m"
        """.trimIndent())
    }

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
