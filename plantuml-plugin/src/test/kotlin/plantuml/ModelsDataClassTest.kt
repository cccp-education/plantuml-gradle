package plantuml

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelsDataClassTest {

    @Test
    fun `InputConfig should have correct defaults`() {
        val config = InputConfig()
        assertEquals("prompts", config.prompts)
        assertEquals("en", config.defaultLang)
    }

    @Test
    fun `OutputConfig should have correct defaults`() {
        val config = OutputConfig()
        assertEquals("generated/diagrams", config.diagrams)
        assertEquals("generated/images", config.images)
        assertEquals("generated/validations", config.validations)
        assertEquals("generated/rag", config.rag)
        assertEquals("png", config.format)
        assertEquals("default", config.theme)
    }

    @Test
    fun `LangchainConfig should have correct defaults`() {
        val config = LangchainConfig()
        assertEquals(5, config.maxIterations)
        assertEquals("ollama", config.model)
        assertTrue(config.validation)
        assertEquals("Rate this diagram on clarity, completeness, and best practices. Return a JSON with 'score' (1-10) and 'feedback' (string) and 'recommendations' (array).", config.validationPrompt)
    }

    @Test
    fun `GitConfig should have correct defaults`() {
        val config = GitConfig()
        assertEquals("github-actions[bot]", config.userName)
        assertEquals("github-actions[bot]@users.noreply.github.com", config.userEmail)
        assertEquals("chore: update PlantUML diagrams [skip ci]", config.commitMessage)
        assertEquals(listOf("main", "develop"), config.watchedBranches)
    }

    @Test
    fun `OllamaConfig should have correct defaults`() {
        val config = OllamaConfig()
        assertEquals("http://localhost:11438", config.baseUrl)
        assertEquals("smollm:135m", config.modelName)
    }

    @Test
    fun `ApiKeyConfig should have correct defaults`() {
        val config = ApiKeyConfig()
        assertEquals("", config.apiKey)
    }

    @Test
    fun `RagConfig should have correct defaults`() {
        val config = RagConfig()
        assertEquals("", config.databaseUrl)
        assertEquals("", config.username)
        assertEquals("", config.password)
        assertEquals("embeddings", config.tableName)
    }

    @Test
    fun `PlantumlDiagram should be instantiable`() {
        val code = PlantumlCode("@startuml\nA -> B\n@enduml", "Test diagram")
        val diagram = PlantumlDiagram(
            conversation = listOf("Create a sequence diagram"),
            plantuml = code
        )
        assertEquals(1, diagram.conversation.size)
        assertEquals("@startuml\nA -> B\n@enduml", diagram.plantuml.code)
        assertEquals("Test diagram", diagram.plantuml.description)
    }

    @Test
    fun `PlantumlCode should be instantiable`() {
        val code = PlantumlCode("@startuml\nA -> B\n@enduml", "Test diagram")
        assertEquals("@startuml\nA -> B\n@enduml", code.code)
        assertEquals("Test diagram", code.description)
    }

    @Test
    fun `ValidationFeedback should be instantiable`() {
        val feedback = ValidationFeedback(
            score = 8,
            feedback = "Good diagram",
            recommendations = listOf("Add more comments")
        )
        assertEquals(8, feedback.score)
        assertEquals("Good diagram", feedback.feedback)
        assertEquals(1, feedback.recommendations.size)
    }

    @Test
    fun `PlantumlConfig should compose all configs correctly`() {
        val config = PlantumlConfig(
            input = InputConfig("custom-prompts", "fr"),
            output = OutputConfig("out/diagrams", "out/images", "out/validations", "out/rag", "svg", "dark"),
            langchain4j = LangchainConfig(3, "openai", true, "", OllamaConfig("http://custom:11434", "llama2"), ApiKeyConfig("sk-key")),
            git = GitConfig("user", "user@email.com", "custom message", listOf("main")),
            rag = RagConfig("jdbc:postgresql://localhost:5432/rag", 5432, "user", "pass", "custom_table")
        )
        assertEquals("custom-prompts", config.input.prompts)
        assertEquals("fr", config.input.defaultLang)
        assertEquals("out/diagrams", config.output.diagrams)
        assertEquals("svg", config.output.format)
        assertEquals(3, config.langchain4j.maxIterations)
        assertEquals("openai", config.langchain4j.model)
        assertEquals("user", config.git.userName)
        assertEquals("jdbc:postgresql://localhost:5432/rag", config.rag.databaseUrl)
    }

    @Test
    fun `PlantumlConfig should default language to en`() {
        val config = PlantumlConfig()
        assertEquals("en", config.language)
    }

    @Test
    fun `PlantumlConfig should default supportedLanguages to en only`() {
        val config = PlantumlConfig()
        assertEquals(listOf("en"), config.supportedLanguages)
    }

    @Test
    fun `PlantumlConfig SUPPORTED_LANGS should contain exactly 10 languages`() {
        assertEquals(10, PlantumlConfig.SUPPORTED_LANGS.size)
    }

    @Test
    fun `PlantumlConfig SUPPORTED_LANGS should contain all expected codes`() {
        val expected = setOf("en", "zh", "hi", "es", "fr", "ar", "bn", "pt", "ru", "ur")
        assertEquals(expected, PlantumlConfig.SUPPORTED_LANGS)
    }

    @Test
    fun `PlantumlConfig should accept custom language`() {
        val config = PlantumlConfig(language = "fr")
        assertEquals("fr", config.language)
    }

    @Test
    fun `PlantumlConfig should accept custom supportedLanguages`() {
        val config = PlantumlConfig(supportedLanguages = listOf("fr", "en", "es"))
        assertEquals(listOf("fr", "en", "es"), config.supportedLanguages)
    }
}
