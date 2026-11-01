package plantuml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

/**
 * Tests pour couvrir les branches manquantes de ConfigMerger
 * Cible : lignes 104-158 (merge*Config avec conditions if (yaml.xxx != default))
 */
class ConfigMergerBranchCoverageTest {
    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `mergeInputConfig should use yaml value when different from default`() {
        val yamlConfig =
            PlantumlConfig(
                input =
                    InputConfig(
                        prompts = "custom-prompts",
                        defaultLang = "fr",
                    ),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, emptyMap())

        assertEquals("custom-prompts", result.input.prompts)
        assertEquals("fr", result.input.defaultLang)
    }

    @Test
    fun `mergeOutputConfig should use yaml values when different from defaults`() {
        val yamlConfig =
            PlantumlConfig(
                output =
                    OutputConfig(
                        diagrams = "custom-diagrams",
                        images = "custom-images",
                        validations = "custom-validations",
                        rag = "custom-rag",
                        format = "pdf",
                        theme = "modern",
                    ),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, emptyMap())

        assertEquals("custom-diagrams", result.output.diagrams)
        assertEquals("custom-images", result.output.images)
        assertEquals("custom-validations", result.output.validations)
        assertEquals("custom-rag", result.output.rag)
        assertEquals("pdf", result.output.format)
        assertEquals("modern", result.output.theme)
    }

    @Test
    fun `mergeLangchain4jConfig should use yaml maxIterations when different from default`() {
        val yamlConfig =
            PlantumlConfig(
                langchain4j =
                    LangchainConfig(
                        maxIterations = 10,
                        model = "gemini",
                        validation = false,
                        validationPrompt = "Custom validation prompt for testing",
                    ),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, emptyMap())

        assertEquals(10, result.langchain4j.maxIterations)
        assertEquals("gemini", result.langchain4j.model)
        assertEquals(false, result.langchain4j.validation)
        assertEquals("Custom validation prompt for testing", result.langchain4j.validationPrompt)
    }

    @Test
    fun `mergeLangchain4jConfig should use yaml ollama config when different from default`() {
        val yamlConfig =
            PlantumlConfig(
                langchain4j =
                    LangchainConfig(
                        ollama =
                            OllamaConfig(
                                baseUrl = "http://custom-server:8080",
                                modelName = "llama3:8b",
                            ),
                    ),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, emptyMap())

        assertEquals("http://custom-server:8080", result.langchain4j.ollama.baseUrl)
        assertEquals("llama3:8b", result.langchain4j.ollama.modelName)
    }

    @Test
    fun `mergeGitConfig should use yaml values when different from defaults`() {
        val yamlConfig =
            PlantumlConfig(
                git =
                    GitConfig(
                        userName = "custom-bot",
                        userEmail = "custom@bot.com",
                        commitMessage = "Custom commit message for PlantUML",
                        watchedBranches = listOf("main", "develop", "feature", "release"),
                    ),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, emptyMap())

        assertEquals("custom-bot", result.git.userName)
        assertEquals("custom@bot.com", result.git.userEmail)
        assertEquals("Custom commit message for PlantUML", result.git.commitMessage)
        assertEquals(listOf("main", "develop", "feature", "release"), result.git.watchedBranches)
    }

    @Test
    fun `mergeRagConfig should use yaml values when non-empty`() {
        val yamlConfig =
            PlantumlConfig(
                rag =
                    RagConfig(
                        databaseUrl = "jdbc:postgresql://custom-db:5432/custom",
                        username = "custom-user",
                        password = "custom-password",
                        tableName = "custom_embeddings",
                    ),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, emptyMap())

        assertEquals("jdbc:postgresql://custom-db:5432/custom", result.rag.databaseUrl)
        assertEquals("custom-user", result.rag.username)
        assertEquals("custom-password", result.rag.password)
        assertEquals("custom_embeddings", result.rag.tableName)
    }

    @Test
    fun `CLI should override yaml values for input config`() {
        val yamlConfig =
            PlantumlConfig(
                input = InputConfig(prompts = "yaml-prompts", defaultLang = "fr"),
            )

        val cliParams =
            mapOf(
                "input.prompts" to "cli-prompts",
                "input.defaultLang" to "es",
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("cli-prompts", result.input.prompts)
        assertEquals("es", result.input.defaultLang)
    }

    @Test
    fun `CLI should override yaml values for output config`() {
        val yamlConfig =
            PlantumlConfig(
                output =
                    OutputConfig(
                        diagrams = "yaml-diagrams",
                        format = "svg",
                    ),
            )

        val cliParams =
            mapOf(
                "output.diagrams" to "cli-diagrams",
                "output.format" to "pdf",
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("cli-diagrams", result.output.diagrams)
        assertEquals("pdf", result.output.format)
    }

    @Test
    fun `CLI should override yaml values for langchain4j config`() {
        val yamlConfig =
            PlantumlConfig(
                langchain4j =
                    LangchainConfig(
                        maxIterations = 10,
                        model = "gemini",
                        validation = false,
                    ),
            )

        val cliParams =
            mapOf(
                "langchain4j.maxIterations" to 15,
                "langchain4j.model" to "claude",
                "langchain4j.validation" to true,
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals(15, result.langchain4j.maxIterations)
        assertEquals("claude", result.langchain4j.model)
        assertEquals(true, result.langchain4j.validation)
    }

    @Test
    fun `CLI should override yaml values for git config`() {
        val yamlConfig =
            PlantumlConfig(
                git =
                    GitConfig(
                        userName = "yaml-user",
                        watchedBranches = listOf("main", "develop"),
                    ),
            )

        val cliParams =
            mapOf(
                "git.userName" to "cli-user",
                "git.watchedBranches" to listOf("main", "feature"),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("cli-user", result.git.userName)
        assertEquals(listOf("main", "feature"), result.git.watchedBranches)
    }

    @Test
    fun `CLI should override yaml values for rag config`() {
        val yamlConfig =
            PlantumlConfig(
                rag =
                    RagConfig(
                        databaseUrl = "jdbc:postgresql://yaml-db:5432/yaml",
                        tableName = "yaml_embeddings",
                    ),
            )

        val cliParams =
            mapOf(
                "rag.databaseUrl" to "jdbc:postgresql://cli-db:5432/cli",
                "rag.tableName" to "cli_embeddings",
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("jdbc:postgresql://cli-db:5432/cli", result.rag.databaseUrl)
        assertEquals("cli_embeddings", result.rag.tableName)
    }

    @Test
    fun `merge should prioritize CLI over yaml over properties`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText(
            """
            plantuml.input.prompts=props-prompts
            plantuml.output.format=png
            plantuml.langchain4j.model=ollama
            """.trimIndent(),
        )

        val yamlConfig =
            PlantumlConfig(
                input = InputConfig(prompts = "yaml-prompts"),
                output = OutputConfig(format = "svg"),
                langchain4j = LangchainConfig(model = "gemini"),
            )

        val cliParams =
            mapOf(
                "input.prompts" to "cli-prompts",
                "output.format" to "pdf",
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("cli-prompts", result.input.prompts)
        assertEquals("pdf", result.output.format)
        assertEquals("gemini", result.langchain4j.model)
    }

    @Test
    fun `merge should use yaml when properties absent and CLI absent`() {
        val yamlConfig =
            PlantumlConfig(
                input = InputConfig(prompts = "yaml-prompts"),
                output = OutputConfig(format = "svg", theme = "dark"),
                langchain4j = LangchainConfig(model = "claude", maxIterations = 20),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, emptyMap())

        assertEquals("yaml-prompts", result.input.prompts)
        assertEquals("svg", result.output.format)
        assertEquals("dark", result.output.theme)
        assertEquals("claude", result.langchain4j.model)
        assertEquals(20, result.langchain4j.maxIterations)
    }

    @Test
    fun `merge should use properties when yaml uses defaults`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText(
            """
            plantuml.input.prompts=props-prompts
            plantuml.output.format=pdf
            plantuml.langchain4j.model=mistral
            plantuml.git.userName=props-user
            """.trimIndent(),
        )

        val yamlConfig = PlantumlConfig()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, emptyMap())

        assertEquals("props-prompts", result.input.prompts)
        assertEquals("pdf", result.output.format)
        assertEquals("mistral", result.langchain4j.model)
        assertEquals("props-user", result.git.userName)
    }
}
