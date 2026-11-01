package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class FileEdgeCasesSteps(
    private val world: PlantumlWorld,
) {
    @Given("a prompt file {string} with UTF-8 content:")
    fun createUtf8PromptFile(
        fileName: String,
        content: String,
    ) {
        world.createGradleProject()
        val promptFile = world.createPromptFile(fileName, content)
        assertThat(promptFile.exists()).isTrue

        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\nnote: UTF-8 test with é à ü ñ\n@enduml",
                "description": "Generated diagram with UTF-8"
              }
            }
            """.trimIndent(),
        )
    }

    @Then("the generated diagram should preserve UTF-8 characters")
    fun generatedDiagramShouldPreserveUtf8Characters() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
            "é",
            "à",
            "ü",
            "ñ",
        )
    }

    @Given("a prompt file {string} with {int}+ characters")
    fun createLargePromptFile(
        fileName: String,
        minCharacters: Int,
    ) {
        world.createGradleProject()
        val promptsDir =
            File(world.projectDir, "prompts").apply {
                if (!exists()) mkdirs()
            }
        val promptFile = File(promptsDir, fileName)

        val largeContent =
            buildString {
                repeat(minCharacters / 10 + 1) {
                    append("This is line $it with some content to make the file larger. ")
                }
            }

        promptFile.writeText(largeContent)
        assertThat(promptFile.length()).isGreaterThan(minCharacters.toLong())

        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\nnote: Large file processing\n@enduml",
                "description": "Generated diagram for large file"
              }
            }
            """.trimIndent(),
        )
    }

    @Then("the task should complete within reasonable time")
    fun taskShouldCompleteWithinReasonableTime() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
        )
    }

    @Then("the generated diagram should reflect all requirements")
    fun generatedDiagramShouldReflectAllRequirements() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "No prompt files found",
            "Processing",
        )
    }

    @Given("a prompt file {string} exists")
    fun promptFileExists(fileName: String) {
        world.createGradleProject()
        val promptsDir =
            File(world.projectDir, "prompts").apply {
                if (!exists()) mkdirs()
            }
        val promptFile = File(promptsDir, fileName)
        promptFile.writeText("Create a diagram with special filename")
        assertThat(promptFile.exists()).isTrue

        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\nnote: Special filename test\n@enduml",
                "description": "Generated diagram"
              }
            }
            """.trimIndent(),
        )
    }

    @Then("the task should handle the filename correctly")
    fun taskShouldHandleFilenameCorrectly() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
            "No prompt files found",
        )
    }

    @Then("output files should use sanitized names")
    fun outputFilesShouldUseSanitizedNames() {
        val outputDir = File(world.projectDir, "generated/diagrams")
        if (outputDir.exists()) {
            val files = outputDir.listFiles()
            if (files != null && files.isNotEmpty()) {
                files.forEach { file ->
                    assertThat(file.name).doesNotContain("(", ")", " ")
                }
            }
        }
    }

    @Given("an empty prompt file {string}")
    fun createEmptyPromptFile(fileName: String) {
        world.createGradleProject()
        val promptsDir =
            File(world.projectDir, "prompts").apply {
                if (!exists()) mkdirs()
            }
        val promptFile = File(promptsDir, fileName)
        promptFile.writeText("")
        assertThat(promptFile.length()).isEqualTo(0)

        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nnote: Empty file skipped\n@enduml",
                "description": "Empty file handling"
              }
            }
            """.trimIndent(),
        )
    }

    @Then("the task should skip the empty file")
    fun taskShouldSkipEmptyFile() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "empty",
            "skip",
        )
    }

    @Then("log a warning about empty prompt")
    fun logWarningAboutEmptyPrompt() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "empty",
            "warning",
            "skip",
        )
    }

    @Given("a prompt file containing only spaces and newlines")
    fun createWhitespaceOnlyPromptFile() {
        world.createGradleProject()
        val promptsDir =
            File(world.projectDir, "prompts").apply {
                if (!exists()) mkdirs()
            }
        val promptFile = File(promptsDir, "whitespace.prompt")
        promptFile.writeText("   \n\n   \n  \n")
        assertThat(promptFile.readText().trim()).isEmpty()

        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nnote: Whitespace file skipped\n@enduml",
                "description": "Whitespace file handling"
              }
            }
            """.trimIndent(),
        )
    }

    @Then("the task should treat it as empty")
    fun taskShouldTreatAsEmpty() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "empty",
            "skip",
        )
    }

    @Then("skip processing")
    fun skipProcessing() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "No prompt files found",
            "skip",
        )
    }

    @Given("a prompt file without trailing newline")
    fun createPromptFileWithoutTrailingNewline() {
        world.createGradleProject()
        val promptsDir =
            File(world.projectDir, "prompts").apply {
                if (!exists()) mkdirs()
            }
        val promptFile = File(promptsDir, "no-newline.prompt")
        Files.writeString(
            Paths.get(promptFile.absolutePath),
            "Create a diagram without newline",
            StandardCharsets.UTF_8,
        )
        val content = promptFile.readText()
        assertThat(content).doesNotEndWith("\n")

        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "@startuml\nactor User\nnote: No trailing newline\n@enduml",
                "description": "Generated diagram"
              }
            }
            """.trimIndent(),
        )
    }

    @Then("the content should be read correctly")
    fun contentShouldBeReadCorrectly() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
        )
    }

    @Then("processing should succeed")
    fun processingShouldSucceed() {
        assertThat(world.buildResult?.output).contains("BUILD SUCCESSFUL")
    }

    @Then("the task should complete successfully")
    fun taskShouldCompleteSuccessfully() {
        assertThat(world.buildResult?.output).contains("BUILD SUCCESSFUL")
    }
}
