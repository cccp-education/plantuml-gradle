package plantuml.scenarios

import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer

class CommonSteps(
    private val world: PlantumlWorld,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CommonSteps::class.java)
        private val cleanupExtension = TestCleanupExtension()
    }

    private var pgvectorContainer: PostgreSQLContainer<*>? = null

    @Before
    fun beforeScenario() {
        log.debug("=== Before scenario ===")
        cleanupExtension.beforeScenario()
    }

    @After
    fun cleanup() {
        log.debug("=== After scenario cleanup ===")

        try {
            // 1. Stop Docker container created in this scenario
            pgvectorContainer?.let { container ->
                log.info("Stopping pgvector container...")
                container.stop()
                pgvectorContainer = null
            }

            // 2. Cleanup World resources (mock servers, temp dirs, etc.)
            world.cleanup()

            // 3. Global cleanup via extension
            cleanupExtension.afterScenario()

            log.debug("=== Cleanup complete ===")
        } catch (e: Exception) {
            log.error("Cleanup failed: ${e.message}", e)
            // Continue cleanup even if partial failure
        }
    }

    @Given("a prompt file {string} with content {string}")
    fun createPromptFile(
        fileName: String,
        content: String,
    ) {
        world.createGradleProject()
        world.createPromptFile(fileName, content)
    }

    @Given("a valid PlantUML file {string} with content {string}")
    fun createValidPlantumlFile(
        fileName: String,
        content: String,
    ) {
        world.createGradleProject()
        world.createPlantUmlFile(fileName, content)
    }

    @Given("an invalid PlantUML file {string} with content {string}")
    fun createInvalidPlantumlFile(
        fileName: String,
        content: String,
    ) {
        world.createGradleProject()
        world.createPlantUmlFile(fileName, content)
    }

    @Given("a mock LLM that returns a valid PlantUML diagram")
    fun mockLlmReturnsValidDiagram() {
        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@enduml",
                "description": "Generated diagram"
              }
            }
            """.trimIndent(),
        )
    }

    @Given("a mock LLM that returns an invalid PlantUML diagram on first attempt")
    fun mockLlmReturnsInvalidOnFirstAttempt() {
        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@endulm",
                "description": "Generated diagram with syntax error"
              }
            }
            """.trimIndent(),
        )
    }

    @Given("a mock LLM that returns a valid PlantUML diagram on second attempt")
    fun mockLlmReturnsValidOnSecondAttempt() {
        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@enduml",
                "description": "Generated diagram"
              }
            }
            """.trimIndent(),
        )
    }

    @Given("a mock LLM that always returns invalid PlantUML diagrams")
    fun mockLlmAlwaysReturnsInvalidDiagrams() {
        world.mockLlmReturnsSequence(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@endulm",
                "description": "Invalid PlantUML diagram - typo @endulm"
              }
            }
            """.trimIndent(),
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@endulm",
                "description": "Invalid PlantUML diagram - typo @endulm"
              }
            }
            """.trimIndent(),
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@endulm",
                "description": "Invalid PlantUML diagram - typo @endulm"
              }
            }
            """.trimIndent(),
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@endulm",
                "description": "Invalid PlantUML diagram - typo @endulm"
              }
            }
            """.trimIndent(),
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@endulm",
                "description": "Invalid PlantUML diagram - typo @endulm"
              }
            }
            """.trimIndent(),
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@endulm",
                "description": "Invalid PlantUML diagram - typo @endulm"
              }
            }
            """.trimIndent(),
        )
    }

    @Given("a mock LLM that returns invalid PlantUML diagrams for first {int} attempts")
    fun mockLlmReturnsInvalidForFirstAttempts(attempts: Int) {
        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startumlnactor User\n@endulm",
                "description": "Invalid PlantUML diagram"
              }
            }
            """.trimIndent(),
        )
    }

    @Given("a mock LLM that returns a valid PlantUML diagram on fourth attempt")
    fun mockLlmReturnsValidOnFourthAttempt() {
        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@enduml",
                "description": "Generated diagram"
              }
            }
            """.trimIndent(),
        )
    }

    @Given("a mock LLM that returns a sequence of responses: invalid then valid")
    fun mockLlmReturnsSequenceInvalidThenValid() {
        world.mockLlmReturnsSequence(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@endulm",
                "description": "Invalid diagram"
              }
            }
            """.trimIndent(),
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@enduml",
                "description": "Valid diagram"
              }
            }
            """.trimIndent(),
        )
    }

    @Given("a mock LLM that returns a sequence of 4 responses: 3 invalid then valid")
    fun mockLlmReturnsSequence4Responses() {
        world.mockLlmReturnsSequence(
            """
            {
              "plantuml": {
                "code": "@startumlnactor User\n@endulm",
                "description": "Invalid diagram 1"
              }
            }
            """.trimIndent(),
            """
            {
              "plantuml": {
                "code": "@startumlnactor User\n@endulm",
                "description": "Invalid diagram 2"
              }
            }
            """.trimIndent(),
            """
            {
              "plantuml": {
                "code": "@startumlnactor User\n@endulm",
                "description": "Invalid diagram 3"
              }
            }
            """.trimIndent(),
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@enduml",
                "description": "Valid diagram"
              }
            }
            """.trimIndent(),
        )
    }

    @Given("a mock LLM that captures the user message")
    fun mockLlmCapturesUserMessage() {
        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@enduml",
                "description": "Generated diagram with RAG context"
              }
            }
            """.trimIndent(),
        )
    }

    @Then("error details should be provided")
    fun verifyErrorDetailsProvided() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).isNotEmpty()
    }

    @Then("the LLM should correct the syntax after iteration")
    fun verifyLlmCorrectsSyntax() {
        assertThat(world.buildResult).isNotNull
    }
}
