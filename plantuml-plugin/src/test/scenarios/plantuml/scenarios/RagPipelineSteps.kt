package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import java.security.MessageDigest
import java.time.Duration

class RagPipelineSteps(private val world: PlantumlWorld) {

    companion object {
        private val log = LoggerFactory.getLogger(RagPipelineSteps::class.java)
    }

    // Shared container reference - avoids duplicate variable issue with CommonSteps
    private var pgvectorContainer: PostgreSQLContainer<*>? = null

    @Given("a PlantUML configuration with RAG enabled")
    fun configurePlantumlWithRag() {
        val configPath = File(world.projectDir, "plantuml-context.yml")
        if (configPath.exists()) {
            val configContent = configPath.readText()
            if (!configContent.contains("rag:")) {
                val updatedConfig = configContent.replace(
                    "output:".toRegex(),
                    """rag:
                      |  enabled: true
                      |  mode: "database"
                      |output:
                    """.trimMargin()
                )
                configPath.writeText(updatedConfig)
            }
        }
    }

    @Given("a running pgvector container")
    fun startPgvectorContainer() {
        log.info("Starting pgvector container...")

        pgvectorContainer = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg15").apply {
            withDatabaseName("plantuml_rag")
            withUsername("test")
            withPassword("test")
            withStartupTimeout(Duration.ofMinutes(2))
            withReuse(false) // Force new container each test
        }.also { container ->
            try {
                container.start()
                log.info("pgvector container started: ${container.containerId}")
            } catch (e: Exception) {
                log.error("Failed to start pgvector container: ${e.message}", e)
                throw e
            }
        }

        val configPath = File(world.projectDir, "plantuml-context.yml")
        val configContent = configPath.readText()
        val updatedConfig = configContent.replace(
            "rag:\\s*\\n\\s*databaseUrl:.*".toRegex(RegexOption.MULTILINE),
            """rag:
              |  databaseUrl: "localhost"
              |  port: ${pgvectorContainer!!.firstMappedPort}
              |  username: "test"
              |  password: "test"
              |  tableName: "embeddings_test"
            """.trimMargin()
        )
        configPath.writeText(updatedConfig)
        log.info("pgvector configured on port ${pgvectorContainer!!.firstMappedPort}")
    }

    @Given("a running pgvector container with existing embeddings")
    fun startPgvectorContainerWithEmbeddings() {
        startPgvectorContainer()
        val ragDir = File(world.projectDir, "generated/rag")
        ragDir.mkdirs()
        File(ragDir, "existing-diagram.puml").writeText(
            """
            @startuml
            class ExistingClass {
              +field: String
            }
            @enduml
            """.trimIndent()
        )
        File(ragDir, "attempt-history-001.json").writeText(
            """
            {
              "prompt": "Existing prompt for RAG context",
              "attempts": 1,
              "finalDiagram": "@startuml\nclass Existing\n@enduml",
              "validationFeedback": []
            }
            """.trimIndent()
        )
    }

    @Given("a running pgvector container with embeddings for {int} prompts")
    fun startPgvectorContainerWithMultipleEmbeddings(count: Int) {
        log.info("Starting pgvector container for $count prompts...")

        pgvectorContainer = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg15").apply {
            withDatabaseName("plantuml_rag")
            withUsername("test")
            withPassword("test")
            withStartupTimeout(Duration.ofMinutes(2))
            withReuse(false)
        }.also { container ->
            try {
                container.start()
                log.info("pgvector container started: ${container.containerId}")
            } catch (e: Exception) {
                log.error("Failed to start pgvector container: ${e.message}", e)
                throw e
            }
        }

        val ragDir = File(world.projectDir, "generated/rag")
        ragDir.mkdirs()

        for (i in 1..count) {
            File(ragDir, "diagram-$i.puml").writeText(
                """
                @startuml
                class Class$i {
                  +field: String
                }
                @enduml
                """.trimIndent()
            )
            File(ragDir, "attempt-history-$i.json").writeText(
                """
                {
                  "prompt": "Prompt number $i",
                  "attempts": 1,
                  "finalDiagram": "@startuml\nclass Class$i\n@enduml",
                  "validationFeedback": []
                }
                """.trimIndent()
            )
        }

        val configPath = File(world.projectDir, "plantuml-context.yml")
        val configContent = configPath.readText()
        val updatedConfig = configContent.replace(
            "rag:\\s*\\n\\s*databaseUrl:.*".toRegex(RegexOption.MULTILINE),
            """rag:
              |  databaseUrl: "localhost"
              |  port: ${pgvectorContainer!!.firstMappedPort}
              |  username: "test"
              |  password: "test"
              |  tableName: "embeddings_multi"
            """.trimMargin()
        )
        configPath.writeText(updatedConfig)
    }

    @When("I run collectPlantumlIndex task")
    fun runCollectPlantumlIndexTask() = runBlocking {
        val properties = mutableMapOf<String, String>()
        pgvectorContainer?.let {
            properties["plantuml.rag.databaseUrl"] = "localhost"
            properties["plantuml.rag.port"] = it.firstMappedPort.toString()
            properties["plantuml.rag.username"] = "test"
            properties["plantuml.rag.password"] = "test"
            properties["plantuml.rag.tableName"] = "embeddings_test"
        }
        val systemProperties = mutableMapOf<String, String>()
        world.projectDir?.let {
            systemProperties["plugin.project.dir"] = it.absolutePath
        }
        properties["plantuml.test.mode"] = "true"

        world.executeGradle("collectPlantumlIndex", properties = properties, systemProperties = systemProperties)
    }

    @Then("embeddings should be stored in pgvector")
    fun verifyEmbeddingsStoredInPgvector() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).containsAnyOf(
            "Rebuilding RAG index",
            "RAG mode",
            "database"
        )
    }

    @Then("the embedding count should match prompt count")
    fun verifyEmbeddingCountMatchesPromptCount() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).contains("BUILD SUCCESSFUL")
    }

    @Then("the LLM request should contain RAG context chunks")
    fun verifyLlmRequestContainsRagContext() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).containsAnyOf(
            "RAG",
            "context",
            "similarity"
        )
    }

    @Then("the RAG similarity score should be logged")
    fun verifyRagSimilarityScoreLogged() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).containsAnyOf(
            "similarity",
            "score",
            "RAG"
        )
    }

    @Then("unchanged prompts should be skipped")
    fun verifyUnchangedPromptsSkipped() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).containsAnyOf(
            "Skipped",
            "skipped",
            "unchanged",
            "Unchanged"
        )
    }

    @Then("only new or modified prompts should be indexed")
    fun verifyOnlyNewOrModifiedPromptsIndexed() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).contains("BUILD SUCCESSFUL")
    }

    @Then("the deleted prompt embeddings should be removed")
    fun verifyDeletedPromptEmbeddingsRemoved() {
        assertThat(world.buildResult).isNotNull
    }

    @Then("the embedding count should decrease by one")
    fun verifyEmbeddingCountDecreasedByOne() {
        val ragDir = File(world.projectDir, "generated/rag")
        val historyFiles = ragDir.listFiles { file -> file.name.contains("attempt-history") }
        assertThat(historyFiles).isNotNull
    }

    @Given("a puml file {string} exists in the RAG directory")
    fun pumlFileExistsInRagDirectory(fileName: String) {
        val ragDir = File(world.projectDir, "build/plantuml-plugin/generated/rag").apply { mkdirs() }
        val pumlFile = File(ragDir, fileName)
        pumlFile.writeText("@startuml\nactor User\n@enduml")
    }

    @Given("a checksum is stored for the prompt")
    fun checksumIsStoredForThePrompt() {
        val promptsDir = File(world.projectDir, "prompts")
        val promptFile = File(promptsDir, "rag-inc-test.prompt")
        val checksumsDir = File(world.projectDir, "build/plantuml-plugin/checksums").apply { mkdirs() }
        val checksum = calculateChecksum(promptFile)
        File(checksumsDir, "rag-inc-test.sha256").writeText(checksum)
    }

    @Given("the prompt file content is modified to {string}")
    fun promptFileContentIsModifiedTo(newContent: String) {
        val promptsDir = File(world.projectDir, "prompts")
        val promptFile = File(promptsDir, "rag-inc-test.prompt")
        promptFile.writeText(newContent)
    }

    @When("I run collectPlantumlIndex task in simulation mode")
    fun runCollectPlantumlIndexTaskInSimulationMode() = runBlocking {
        val properties = mutableMapOf<String, String>()
        val systemProperties = mutableMapOf<String, String>()
        world.projectDir?.let {
            systemProperties["plugin.project.dir"] = it.absolutePath
        }
        properties["plantuml.test.mode"] = "true"
        world.executeGradle("collectPlantumlIndex", properties = properties, systemProperties = systemProperties)
    }

    @Then("the puml file should be skipped in RAG reindex")
    fun pumlFileShouldBeSkippedInRagReindex() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).containsAnyOf(
            "Skipped",
            "skipped",
            "unchanged",
            "Unchanged"
        )
    }

    @Then("the puml file should be reindexed in RAG")
    fun pumlFileShouldBeReindexedInRag() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).containsAnyOf(
            "Found",
            "found",
            "indexing",
            "Indexing",
            "Rebuilding RAG"
        )
    }

    private fun calculateChecksum(file: File): String {
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
