package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import plantuml.PlantumlMessages
import java.io.File

class I18nSteps(private val world: PlantumlWorld) {

    @Given("plantuml-context.yml specifies language {string}")
    fun configSpecifiesLanguage(language: String) {
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
            language: "$language"
            """.trimIndent()
        )
    }

    @Given("plantuml-context.yml specifies supportedLanguages {string}")
    fun configSpecifiesSupportedLanguages(languages: String) {
        world.createGradleProject()
        val langList = languages.split(",").map { it.trim() }
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
            language: "en"
            supportedLanguages: [${langList.joinToString(", ") { "\"$it\"" }}]
            """.trimIndent()
        )
    }

    @Given("gradle.properties specifies plantuml.language={string}")
    fun gradlePropertiesSpecifiesLanguage(language: String) {
        world.createGradleProject(
            gradleProperties = mapOf("plantuml.language" to language)
        )
    }

    @Given("environment variable PLANTUML_LANGUAGE is set to {string}")
    fun environmentVariableLanguageIsSetTo(language: String) {
        world.environmentVariables["PLANTUML_LANGUAGE"] = language
    }

    @When("I run generatePlantumlDiagrams task with language override {word}")
    fun runGeneratePlantumlDiagramsTaskWithLanguageOverride(language: String) = runBlocking {
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
        properties["plantuml.language"] = language
        properties["plantuml.test.mode"] = "true"

        try {
            world.executeGradle("generatePlantumlDiagrams", properties = properties, systemProperties = systemProperties)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Then("the resolved language should be {string}")
    fun resolvedLanguageShouldBe(expectedLanguage: String) {
        assertThat(world.buildResult).isNotNull
        val output = world.buildResult!!.output
        assertThat(output).contains("BUILD SUCCESSFUL")
    }

    @Then("the supported languages should include {string}, {string}, and {string}")
    fun supportedLanguagesShouldInclude(lang1: String, lang2: String, lang3: String) {
        assertThat(world.buildResult).isNotNull
        val output = world.buildResult!!.output
        assertThat(output).contains("BUILD SUCCESSFUL")
    }

    @When("I run the {string} task")
    fun runTask(taskName: String) = runBlocking {
        val properties = mutableMapOf<String, String>()
        val systemProperties = mutableMapOf<String, String>()
        world.projectDir?.let {
            systemProperties["plugin.project.dir"] = it.absolutePath
        }

        try {
            world.executeGradle(taskName, properties = properties, systemProperties = systemProperties)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Then("the output should contain the i18n task description for {string} in language {string}")
    fun outputShouldContainI18nTaskDescription(taskName: String, language: String) {
        assertThat(world.buildResult).isNotNull
        val output = world.buildResult!!.output
        assertThat(output).contains("BUILD SUCCESSFUL")
        val expectedDescription = PlantumlMessages.get("task.generate.description", language)
        assertThat(output).contains(expectedDescription)
    }
}
