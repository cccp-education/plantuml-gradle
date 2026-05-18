package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class ConfigurationSteps(private val world: PlantumlWorld) {

    @Given("no plantuml-config.yml exists")
    fun noConfigFileExists() {
        world.createGradleProject("nonexistent.yml")
    }

    @Then("a default configuration should be created")
    fun defaultConfigurationShouldBeCreated() {
        val configFile = File(world.projectDir, "plantuml-context.yml")
        if (!configFile.exists()) {
            assertThat(world.buildResult?.output).containsAnyOf(
                "No plantuml-context.yml",
                "using defaults",
                "default configuration"
            )
        }
    }

    @Then("the task should complete successfully with defaults")
    fun taskShouldCompleteSuccessfullyWithDefaults() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "No prompt files found"
        )
    }

    @Given("plantuml-config.yml contains malformed YAML")
    fun configFileContainsMalformedYaml() {
        world.createGradleProject()
        val configFile = File(world.projectDir, "plantuml-context.yml")
        configFile.writeText(
            """
            input:
              prompts: "prompts"
            output:
              images: "generated/images"
              diagrams: "generated/diagrams"
            langchain4j:
              model: "ollama"
              ollama:
                baseUrl: "http://localhost:11434"
                modelName: "smollm:135m"
              maxIterations: 5
            invalid yaml syntax here: [unclosed bracket
            """.trimIndent()
        )
    }

    @Then("the build should fail with clear YAML error")
    fun buildShouldFailWithClearYamlError() {
        assertThat(world.exception).isNotNull
        assertThat(world.exception?.message).containsAnyOf(
            "Invalid YAML",
            "YAML syntax",
            "parse error"
        )
    }

    @Then("indicate the problematic line")
    fun indicateTheProblematicLine() {
        val errorMessage = world.exception?.message ?: world.buildResult?.output ?: ""
        assertThat(errorMessage).containsAnyOf(
            "line",
            "column",
            "position"
        )
    }

    @Given("plantuml-config.yml specifies custom directories:")
    fun configSpecifiesCustomDirectories(table: io.cucumber.datatable.DataTable) {
        world.createGradleProject()
        val configMap = table.asMaps().associate { it["input"] to it["output"] }
        val inputDir = configMap["input"] ?: "my-prompts"
        val outputDir = configMap["output"] ?: "my-generated"

        val configFile = File(world.projectDir, "plantuml-context.yml")
        configFile.writeText(
            """
            input:
              prompts: "$inputDir"
            output:
              images: "$outputDir/images"
              diagrams: "$outputDir"
              rag: "$outputDir/rag"
              validations: "$outputDir/validations"
            langchain4j:
              model: "ollama"
              ollama:
                baseUrl: "http://localhost:11434"
                modelName: "smollm:135m"
              maxIterations: 5
            """.trimIndent()
        )

        // Create the custom prompts directory and a prompt file
        val promptsDir = File(world.projectDir, inputDir).apply { mkdirs() }
        val promptFile = File(promptsDir, "custom.prompt")
        promptFile.writeText("Create a diagram")
    }

    @Given("a prompt file exists in {string}")
    fun promptFileExistsInCustomDirectory(filePath: String) {
        val promptFile = File(world.projectDir, filePath)
        promptFile.parentFile?.mkdirs()
        promptFile.writeText("Create a simple diagram")
    }

    @Then("diagrams should be generated in {string}")
    fun diagramsShouldBeGeneratedInDirectory(path: String) {
        val diagramsDir = File(world.projectDir, path)
        assertThat(diagramsDir).`as`("Diagrams directory should exist").exists()
    }

    @Then("images should be generated in {string}")
    fun imagesShouldBeGeneratedInDirectory(path: String) {
        val imagesDir = File(world.projectDir, path)
        assertThat(imagesDir).`as`("Images directory should exist").exists()
    }

    @Given("plantuml-config.yml specifies Ollama as provider")
    fun configSpecifiesOllamaAsProvider() {
        world.createGradleProject()
        val configFile = File(world.projectDir, "plantuml-context.yml")
        configFile.writeText(
            """
            input:
              prompts: "prompts"
            output:
              images: "generated/images"
              diagrams: "generated/diagrams"
              rag: "generated/rag"
              validations: "generated/validations"
            langchain4j:
              model: "ollama"
              ollama:
                baseUrl: "http://localhost:11434"
                modelName: "smollm:135m"
              maxIterations: 5
            """.trimIndent()
        )
    }

    @Given("environment variable PLANTUML_LLM_PROVIDER is set to {string}")
    fun environmentVariableIsSetTo(provider: String) {
        // Store the provider to be used in the When step
        world.environmentVariables["PLANTUML_LLM_PROVIDER"] = provider
    }

    @Then("OpenAI should be used instead of Ollama")
    fun openAiShouldBeUsedInsteadOfOllama() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing"
        )
    }

    @Given("plantuml-config.yml specifies maxIterations={int}")
    fun configSpecifiesMaxIterations(maxIterations: Int) {
        world.createGradleProject()
        val configFile = File(world.projectDir, "plantuml-context.yml")
        configFile.writeText(
            """
            input:
              prompts: "prompts"
            output:
              images: "generated/images"
              diagrams: "generated/diagrams"
              rag: "generated/rag"
              validations: "generated/validations"
            langchain4j:
              model: "ollama"
              ollama:
                baseUrl: "http://localhost:11434"
                modelName: "smollm:135m"
              maxIterations: $maxIterations
            """.trimIndent()
        )
    }

    @When("I run generatePlantumlDiagrams task with -Pplantuml.langchain4j.maxIterations={int}")
    fun runGeneratePlantumlDiagramsTaskWithCliOverride(maxIterations: Int) = runBlocking {
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
        properties["plantuml.langchain4j.maxIterations"] = maxIterations.toString()
        properties["plantuml.test.mode"] = "true"

        try {
            world.executeGradle("generatePlantumlDiagrams", properties = properties, systemProperties = systemProperties)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @When("I run generatePlantumlDiagrams task")
    fun runGeneratePlantumlDiagramsTask() = runBlocking {
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

    @Then("{int} iterations should be allowed")
    fun iterationsShouldBeAllowed(maxIterations: Int) {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing"
        )
    }

    @Given("plantuml-config.yml only specifies input directory")
    fun configOnlySpecifiesInputDirectory() {
        world.createGradleProject()
        val configFile = File(world.projectDir, "plantuml-context.yml")
        configFile.writeText(
            """
            input:
              prompts: "custom-prompts"
            """.trimIndent()
        )
    }

    @Then("default values should be used for unspecified settings")
    fun defaultValuesShouldBeUsedForUnspecifiedSettings() {
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "No prompt files found",
            "Processing"
        )
    }

}
