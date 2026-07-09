package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.PlantumlMessages
import plantuml.apikey.ApiKeyPool
import plantuml.service.KnowledgeGraphParser
import plantuml.service.GraphifyPromptAdapter
import java.io.File
import kotlin.test.assertFailsWith

class I18nErrorSteps(private val world: PlantumlWorld) {

    @Given("an empty API key pool")
    fun anEmptyApiKeyPool() {
        world.i18nErrorPool = ApiKeyPool(emptyList())
    }

    @When("the pool is asked for the next key")
    fun poolAskedForNextKey() {
        try {
            world.i18nErrorPool!!.getNextKey()
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Given("a non-existent graph file path")
    fun aNonExistentGraphFilePath() {
        world.i18nErrorGraphFile = File(world.projectDir ?: File(System.getProperty("java.io.tmpdir")), "nonexistent.json")
    }

    @When("the parser is invoked")
    fun theParserIsInvoked() {
        try {
            KnowledgeGraphParser(world.i18nErrorGraphFile!!).parse()
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Given("a graph file without communities field")
    fun aGraphFileWithoutCommunitiesField() {
        val graphFile = File(world.projectDir ?: File(System.getProperty("java.io.tmpdir")), "no-communities.json")
        graphFile.writeText("""{"other_field": "value"}""")
        world.i18nErrorGraphFile = graphFile
        world.i18nErrorPromptsDir = File(graphFile.parentFile, "prompts").apply { mkdirs() }
    }

    @When("generateAllPrompts is invoked")
    fun generateAllPromptsIsInvoked() {
        try {
            GraphifyPromptAdapter(world.i18nErrorGraphFile!!, world.i18nErrorPromptsDir!!).generateAllPrompts()
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Given("a graph file with null communities")
    fun aGraphFileWithNullCommunities() {
        val graphFile = File(world.projectDir ?: File(System.getProperty("java.io.tmpdir")), "null-communities.json")
        graphFile.writeText("""{"nodes": []}""")
        world.i18nErrorGraphFile = graphFile
        world.i18nErrorPromptsDir = File(graphFile.parentFile, "prompts").apply { mkdirs() }
    }

    @When("generatePrompt is invoked for {string}")
    fun generatePromptIsInvokedFor(subgraphName: String) {
        try {
            GraphifyPromptAdapter(world.i18nErrorGraphFile!!, world.i18nErrorPromptsDir!!).generatePrompt(subgraphName)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Given("plantuml-context.yml with invalid YAML content")
    fun plantumlContextYmlWithInvalidYamlContent() {
        world.createGradleProject()
        val configFile = File(world.projectDir, "plantuml-context.yml")
        configFile.writeText("invalid: yaml: content: [")
    }

    @When("the configuration is loaded")
    fun theConfigurationIsLoaded() {
        try {
            val project = org.gradle.testfixtures.ProjectBuilder.builder()
                .withProjectDir(world.projectDir ?: error("no project dir"))
                .build()
            plantuml.PlantumlManager.Configuration.load(project)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Given("a JSON response without plantuml or code field")
    fun aJsonResponseWithoutPlantumlOrCodeField() {
        world.i18nErrorResponse = """{"other": "value"}"""
    }

    @Given("a malformed JSON response")
    fun aMalformedJsonResponse() {
        world.i18nErrorResponse = """{"invalid": json}"""
    }

    @When("extractPlantUmlFromResponse is invoked")
    fun extractPlantUmlFromResponseIsInvoked() {
        try {
            val processor = plantuml.service.DiagramProcessor(
                plantumlService = plantuml.service.PlantumlService(),
                chatModel = null,
                config = plantuml.PlantumlConfig()
            )
            val method = plantuml.service.DiagramProcessor::class.java
                .getDeclaredMethod("extractPlantUmlFromResponse", String::class.java)
            method.isAccessible = true
            method.invoke(processor, world.i18nErrorResponse)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            world.exception = e.targetException
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Then("the exception message should match the i18n key {string} in language {string}")
    fun exceptionMessageShouldMatchI18nKey(key: String, language: String) {
        assertThat(world.exception).isNotNull
        val expected = PlantumlMessages.get(key, language)
        assertThat(world.exception!!.message).isEqualTo(expected)
    }

    @Then("the exception message should match the i18n format key {string} with args in language {string}")
    fun exceptionMessageShouldMatchI18nFormatKey(key: String, language: String) {
        assertThat(world.exception).isNotNull
        val expected = PlantumlMessages.format(key, language, "")
        assertThat(world.exception!!.message).contains(expected.substringBefore("{"))
    }

    @Then("the exception message should match the i18n format key {string} with args {string} in language {string}")
    fun exceptionMessageShouldMatchI18nFormatKeyWithArgs(key: String, args: String, language: String) {
        assertThat(world.exception).isNotNull
        val expected = PlantumlMessages.format(key, language, args)
        assertThat(world.exception!!.message).isEqualTo(expected)
    }

    @Then("the exception message should contain the i18n key prefix {string} in language {string}")
    fun exceptionMessageShouldContainI18nKeyPrefix(prefix: String, language: String) {
        assertThat(world.exception).isNotNull
        assertThat(world.exception!!.message).contains(prefix)
    }
}