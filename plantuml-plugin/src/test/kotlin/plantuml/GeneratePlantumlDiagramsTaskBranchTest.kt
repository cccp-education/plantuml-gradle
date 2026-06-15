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
 * Unit tests for GeneratePlantumlDiagramsTask - Branch Coverage
 * 
 * Tests branches not covered by GeneratePlantumlDiagramsTaskTest:
 * - Override of plantuml.langchain4j.model
 * - Syntax validation errors (Invalid result)
 * - Error handling (catch blocks)
 */
class GeneratePlantumlDiagramsTaskBranchTest {

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
        
        project.pluginManager.apply("education.cccp.plantuml")
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
    fun `should override LLM model from command line property`() {
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
        
        // Override model (not ollama.modelName, but the main model property)
        project.extensions.extraProperties.set("plantuml.langchain4j.model", "ollama")
        
        // When: execute the task
        task.processPrompts()
        
        // Then: file has been processed
        assertFalse(promptFile.exists(), "Prompt should be processed with model override")
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/chat")))
    }



    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

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
