package plantuml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class ConfigMergerEdgeCasesTest {
    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `should use getOrDefault helper method for typed values`() {
        val cliParams =
            mapOf(
                "langchain4j.maxIterations" to 10,
                "langchain4j.validation" to true,
                "git.watchedBranches" to listOf("main", "develop", "feature"),
            )

        val result = ConfigMerger.merge(testProjectDir, PlantumlConfig(), cliParams)

        assertEquals(10, result.langchain4j.maxIterations)
        assertEquals(true, result.langchain4j.validation)
        assertEquals(listOf("main", "develop", "feature"), result.git.watchedBranches)
    }

    @Test
    fun `should handle CLI parameter with null value`() {
        val cliParams =
            mapOf<String, Any?>(
                "input.prompts" to null,
                "output.format" to "svg",
            )

        val yamlConfig =
            PlantumlConfig(
                input = InputConfig(prompts = "yaml-prompts"),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("yaml-prompts", result.input.prompts)
        assertEquals("svg", result.output.format)
    }

    @Test
    fun `should handle gradle properties with comments and empty lines`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText(
            """
            # This is a comment
            plantuml.input.prompts=custom-prompts
            
            # Another comment
            plantuml.output.format=svg
            
            plantuml.langchain4j.validation=true
            """.trimIndent(),
        )

        val config = ConfigMerger.loadFromGradleProperties(testProjectDir)

        assertEquals("custom-prompts", config.input.prompts)
        assertEquals("svg", config.output.format)
        assertEquals(true, config.langchain4j.validation)
    }

    @Test
    fun `should ignore malformed gradle properties lines`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText(
            """
            plantuml.input.prompts=custom-prompts
            malformed line without equals
            plantuml.output.format=svg
            = no key
            plantuml.langchain4j.model=gemini
            """.trimIndent(),
        )

        val config = ConfigMerger.loadFromGradleProperties(testProjectDir)

        assertEquals("custom-prompts", config.input.prompts)
        assertEquals("svg", config.output.format)
        assertEquals("gemini", config.langchain4j.model)
    }

    @Test
    fun `should handle plantuml prefix in comments correctly`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText(
            """
            # plantuml.input.prompts=should-be-ignored
            plantuml.input.prompts=actual-value
            #plantuml.output.format=ignored
            """.trimIndent(),
        )

        val config = ConfigMerger.loadFromGradleProperties(testProjectDir)

        assertEquals("actual-value", config.input.prompts)
        assertEquals("png", config.output.format)
    }

    @Test
    fun `should handle whitespace around property values`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText(
            """
            plantuml.input.prompts   =   spaced-prompts   
            plantuml.output.format=  svg  
            plantuml.langchain4j.model  =gemini
            """.trimIndent(),
        )

        val config = ConfigMerger.loadFromGradleProperties(testProjectDir)

        assertEquals("spaced-prompts", config.input.prompts)
        assertEquals("svg", config.output.format)
        assertEquals("gemini", config.langchain4j.model)
    }

    @Test
    fun `should handle property value with equals sign`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText(
            """
            plantuml.input.prompts=prompts-with=equals=sign
            """.trimIndent(),
        )

        val config = ConfigMerger.loadFromGradleProperties(testProjectDir)

        assertEquals("prompts-with=equals=sign", config.input.prompts)
    }

    @Test
    fun `should merge RAG config with non-empty check for database credentials`() {
        val yamlConfig =
            PlantumlConfig(
                rag =
                    RagConfig(
                        databaseUrl = "jdbc:postgresql://yaml-db:5432/plantuml",
                        username = "yaml-user",
                        password = "yaml-password",
                        tableName = "yaml_embeddings",
                    ),
            )

        val propsConfig =
            PlantumlConfig(
                rag =
                    RagConfig(
                        databaseUrl = "jdbc:postgresql://props-db:5432/plantuml",
                        username = "props-user",
                        password = "props-password",
                        tableName = "props_embeddings",
                    ),
            )

        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("jdbc:postgresql://yaml-db:5432/plantuml", result.rag.databaseUrl)
        assertEquals("yaml-user", result.rag.username)
        assertEquals("yaml-password", result.rag.password)
        assertEquals("yaml_embeddings", result.rag.tableName)
    }

    @Test
    fun `should override RAG config with CLI parameters`() {
        val yamlConfig =
            PlantumlConfig(
                rag =
                    RagConfig(
                        databaseUrl = "jdbc:postgresql://yaml-db:5432/plantuml",
                        tableName = "yaml_embeddings",
                    ),
            )

        val cliParams =
            mapOf(
                "rag.databaseUrl" to "jdbc:postgresql://cli-db:5432/plantuml",
                "rag.tableName" to "cli_embeddings",
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("jdbc:postgresql://cli-db:5432/plantuml", result.rag.databaseUrl)
        assertEquals("cli_embeddings", result.rag.tableName)
    }

    @Test
    fun `should merge Git config with custom values`() {
        val yamlConfig =
            PlantumlConfig(
                git =
                    GitConfig(
                        userName = "custom-user",
                        userEmail = "custom@example.com",
                        commitMessage = "Custom commit message",
                        watchedBranches = listOf("main", "develop", "release"),
                    ),
            )

        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("custom-user", result.git.userName)
        assertEquals("custom@example.com", result.git.userEmail)
        assertEquals("Custom commit message", result.git.commitMessage)
        assertEquals(listOf("main", "develop", "release"), result.git.watchedBranches)
    }

    @Test
    fun `should override Git config with CLI parameters`() {
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
                "git.watchedBranches" to listOf("main", "feature", "hotfix"),
            )

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("cli-user", result.git.userName)
        assertEquals(listOf("main", "feature", "hotfix"), result.git.watchedBranches)
    }

    @Test
    fun `should handle empty gradle properties file`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("")

        val config = ConfigMerger.loadFromGradleProperties(testProjectDir)

        assertEquals("prompts", config.input.prompts)
        assertEquals("generated/diagrams", config.output.diagrams)
        assertEquals("ollama", config.langchain4j.model)
    }

    @Test
    fun `should handle gradle properties with only comments`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText(
            """
            # No actual properties here
            # Just comments
            """.trimIndent(),
        )

        val config = ConfigMerger.loadFromGradleProperties(testProjectDir)

        assertEquals("prompts", config.input.prompts)
        assertEquals("ollama", config.langchain4j.model)
    }
}
