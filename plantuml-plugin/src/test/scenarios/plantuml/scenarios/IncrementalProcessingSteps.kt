package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.security.MessageDigest

class IncrementalProcessingSteps(private val world: PlantumlWorld) {

    private fun startIncrementalMockLlm() {
        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\n@enduml",
                "description": "Generated diagram"
              }
            }
        """.trimIndent()
        )
    }

    @Given("a prompt file was already processed successfully")
    fun promptWasAlreadyProcessedSuccessfully() {
        world.createGradleProject()
        val promptsDir = File(world.projectDir, "prompts").apply { mkdirs() }
        val promptFile = File(promptsDir, "incremental-test.prompt")
        promptFile.writeText("Create a diagram")

        val diagramsDir = File(world.projectDir, "generated/diagrams").apply { mkdirs() }
        val diagramsFile = File(diagramsDir, "incremental-test.yml")
        diagramsFile.writeText(
            """
            plantuml:
              code: "@startuml\nactor User\n@enduml"
            status: success
            """.trimIndent()
        )

        val imagesDir = File(world.projectDir, "generated/images").apply { mkdirs() }
        val imageFile = File(imagesDir, "incremental-test.png")
        imageFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()))

        val checksumsDir = File(world.projectDir, "build/plantuml-plugin/checksums").apply { mkdirs() }
        val checksumFile = File(checksumsDir, "incremental-test.sha256")
        val checksum = calculateChecksum(promptFile)
        checksumFile.writeText(checksum)
        startIncrementalMockLlm()
    }

    @Given("the prompt file has not been modified")
    fun promptFileHasNotBeenModified() {
        val promptsDir = File(world.projectDir, "prompts")
        val promptFile = File(promptsDir, "incremental-test.prompt")
        promptFile.setLastModified(System.currentTimeMillis() - 10000)
    }

    @When("I run generatePlantumlDiagrams task again")
    fun runGeneratePlantumlDiagramsTaskAgain() = runBlocking {
        val properties = mutableMapOf<String, String>()
        world.mockServerPort?.let {
            properties["plantuml.langchain4j.model"] = "ollama"
            properties["plantuml.langchain4j.ollama.baseUrl"] = "http://localhost:$it"
            properties["plantuml.langchain4j.ollama.modelName"] = "smollm:135m"
        }
        val systemProperties = mutableMapOf<String, String>()
        world.projectDir?.let {
            systemProperties["plugin.project.dir"] = it.absolutePath
        }
        properties["plantuml.test.mode"] = "true"

        try {
            world.executeGradle("generatePlantumlDiagrams", properties = properties, systemProperties = systemProperties)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Then("the prompt should be skipped")
    fun promptShouldBeSkipped() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "UP-TO-DATE",
            "up-to-date",
            "skipped",
            "Skipped",
            "no changes",
            "No changes"
        )
    }

    @Then("output should indicate {string}")
    fun outputShouldIndicate(expected: String) {
        assertThat(world.buildResult?.output).contains(expected)
    }

    @Given("a prompt file was already processed")
    fun promptWasAlreadyProcessed() {
        world.createGradleProject()
        val promptsDir = File(world.projectDir, "prompts").apply { mkdirs() }
        val promptFile = File(promptsDir, "incremental-test.prompt")
        promptFile.writeText("Create a diagram")

        val diagramsDir = File(world.projectDir, "generated/diagrams").apply { mkdirs() }
        val diagramsFile = File(diagramsDir, "incremental-test.yml")
        diagramsFile.writeText(
            """
            plantuml:
              code: "@startuml\nactor User\n@enduml"
            status: success
            """.trimIndent()
        )
        startIncrementalMockLlm()
    }

    @Given("the prompt file content has been modified")
    fun promptFileContentHasBeenModified() {
        val promptsDir = File(world.projectDir, "prompts")
        val promptFile = File(promptsDir, "incremental-test.prompt")
        promptFile.writeText("Create a different diagram with more elements")
        world.stopMockLlm()
        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\ntitle different\nactor User\n@enduml",
                "description": "Generated different diagram"
              }
            }
        """.trimIndent()
        )
    }

    @Then("the modified prompt should be reprocessed")
    fun modifiedPromptShouldBeReprocessed() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "Processing",
            "processing",
            "Processed",
            "processed"
        )
    }

    @Then("new diagram should be generated")
    fun newDiagramShouldBeGenerated() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        val diagramsFile = File(diagramsDir, "incremental-test.yml")
        assertThat(diagramsFile).exists()

        val content = diagramsFile.readText()
        assertThat(content).contains("different")
    }

    @Given("{int} prompts have been processed with outputs generated")
    fun promptsHaveBeenProcessedWithOutputsGenerated(count: Int) {
        world.createGradleProject()
        val promptsDir = File(world.projectDir, "prompts").apply { mkdirs() }
        val diagramsDir = File(world.projectDir, "generated/diagrams").apply { mkdirs() }
        val imagesDir = File(world.projectDir, "generated/images").apply { mkdirs() }

        for (i in 1..count) {
            val promptFile = File(promptsDir, "prompt-$i.prompt")
            promptFile.writeText("Create diagram $i")

            val diagramsFile = File(diagramsDir, "prompt-$i.yml")
            diagramsFile.writeText(
                """
                plantuml:
                  code: "@startuml\nclass Class$i\n@enduml"
                status: success
                """.trimIndent()
            )

            val imageFile = File(imagesDir, "prompt-$i.png")
            imageFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()))
        }
        startIncrementalMockLlm()
    }

    @Given("one prompt file is deleted")
    fun onePromptFileIsDeleted() {
        val promptsDir = File(world.projectDir, "prompts")
        val promptFile = File(promptsDir, "prompt-1.prompt")
        promptFile.delete()
    }

    @Then("the outputs for the deleted prompt should be removed")
    fun outputsForDeletedPromptShouldBeRemoved() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        val imagesDir = File(world.projectDir, "generated/images")

        val diagramsFile = File(diagramsDir, "prompt-1.yml")
        val imageFile = File(imagesDir, "prompt-1.png")

        assertThat(diagramsFile).doesNotExist()
        assertThat(imageFile).doesNotExist()
    }

    @Then("outputs for remaining prompts should be preserved")
    fun outputsForRemainingPromptsShouldBePreserved() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        val imagesDir = File(world.projectDir, "generated/images")

        for (i in 2..3) {
            val diagramsFile = File(diagramsDir, "prompt-$i.yml")
            val imageFile = File(imagesDir, "prompt-$i.png")
            assertThat(diagramsFile).`as`("Diagrams file for prompt-$i should exist").exists()
            assertThat(imageFile).`as`("Image file for prompt-$i should exist").exists()
        }
    }

    @Given("a prompt file with known content")
    fun promptFileWithKnownContent() {
        world.createGradleProject()
        val promptsDir = File(world.projectDir, "prompts").apply { mkdirs() }
        val promptFile = File(promptsDir, "checksum-test.prompt")
        promptFile.writeText("Create a diagram with known content")
        startIncrementalMockLlm()
    }

    @Then("a checksum should be stored for the prompt")
    fun checksumShouldBeStoredForPrompt() {
        val checksumsDir = File(world.projectDir, "build/plantuml-plugin/checksums")
        val checksumFile = File(checksumsDir, "checksum-test.sha256")
        assertThat(checksumFile).exists()

        val checksum = checksumFile.readText()
        assertThat(checksum).hasSize(64)
    }

    @Then("on re-run, checksum should be compared")
    fun checksumShouldBeComparedOnRerun() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "checksum",
            "Checksum",
            "CHECKSUM",
            "unchanged",
            "Unchanged"
        )
    }

    @Then("processing should be skipped if checksum matches")
    fun processingShouldBeSkippedIfChecksumMatches() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "UP-TO-DATE",
            "up-to-date",
            "skipped",
            "Skipped"
        )
    }

    @Given("prompts were already processed")
    fun promptsWereAlreadyProcessed() {
        world.createGradleProject()
        val promptsDir = File(world.projectDir, "prompts").apply { mkdirs() }
        val diagramsDir = File(world.projectDir, "generated/diagrams").apply { mkdirs() }
        val imagesDir = File(world.projectDir, "generated/images").apply { mkdirs() }

        for (i in 1..2) {
            val promptFile = File(promptsDir, "rerun-test-$i.prompt")
            promptFile.writeText("Create diagram $i")

            val diagramsFile = File(diagramsDir, "rerun-test-$i.yml")
            diagramsFile.writeText(
                """
                plantuml:
                  code: "@startuml\nclass Class$i\n@enduml"
                status: success
                """.trimIndent()
            )

            val imageFile = File(imagesDir, "rerun-test-$i.png")
            imageFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()))
        }
        startIncrementalMockLlm()
    }

    @When("I run generatePlantumlDiagrams task with --rerun-tasks")
    fun runGeneratePlantumlDiagramsTaskWithRerunTasks() = runBlocking {
        val properties = mutableMapOf<String, String>()
        world.mockServerPort?.let {
            properties["plantuml.langchain4j.model"] = "ollama"
            properties["plantuml.langchain4j.ollama.baseUrl"] = "http://localhost:$it"
            properties["plantuml.langchain4j.ollama.modelName"] = "smollm:135m"
        }
        val systemProperties = mutableMapOf<String, String>()
        world.projectDir?.let {
            systemProperties["plugin.project.dir"] = it.absolutePath
        }
        properties["plantuml.test.mode"] = "true"

        try {
            world.executeGradle("generatePlantumlDiagrams", "--rerun-tasks", properties = properties, systemProperties = systemProperties)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Then("all prompts should be reprocessed regardless of change status")
    fun allPromptsShouldBeReprocessedRegardlessOfChangeStatus() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "Processing",
            "processing",
            "Processed",
            "processed"
        )
    }

    private fun calculateChecksum(file: File): String {
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
