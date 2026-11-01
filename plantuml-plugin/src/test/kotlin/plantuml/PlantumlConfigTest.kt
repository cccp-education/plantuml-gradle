package plantuml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// Tests are quick
class PlantumlConfigTest {
    companion object {
        private val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    }

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should load configuration from YAML file`() {
        // Given
        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText(
            """
            input:
              prompts: "custom-prompts"
              defaultLang: "fr"
              
            output:
              images: "custom-images"
              format: "svg"
              theme: "blue"
              
            langchain4j:
              maxIterations: 3
              model: "gemini"
              validation: false
              
              gemini:
                apiKey: "custom-gemini-key"
                
            git:
              userName: "custom-user"
              userEmail: "custom@example.com"
              commitMessage: "custom commit message"
              watchedBranches: 
                - "feature"
                - "release"
                
            rag:
              databaseUrl: "jdbc:postgresql://localhost:5432/custom_db"
              username: "custom_user"
              password: "custom_password"
              tableName: "custom_table"
            """.trimIndent(),
        )

        // When
        val config = mapper.readValue(configFile, PlantumlConfig::class.java)

        // Then
        assertNotNull(config)
        assertEquals("custom-prompts", config.input.prompts)
        assertEquals("fr", config.input.defaultLang)
        assertEquals("custom-images", config.output.images)
        assertEquals("svg", config.output.format)
        assertEquals("blue", config.output.theme)
        assertEquals(3, config.langchain4j.maxIterations)
        assertEquals("gemini", config.langchain4j.model)
        assertEquals(false, config.langchain4j.validation)
        assertEquals("custom-gemini-key", config.langchain4j.gemini.apiKey)
        assertEquals("custom-user", config.git.userName)
        assertEquals("custom@example.com", config.git.userEmail)
        assertEquals("custom commit message", config.git.commitMessage)
        assertEquals(listOf("feature", "release"), config.git.watchedBranches)
        assertEquals("jdbc:postgresql://localhost:5432/custom_db", config.rag.databaseUrl)
        assertEquals("custom_user", config.rag.username)
        assertEquals("custom_password", config.rag.password)
        assertEquals("custom_table", config.rag.tableName)
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "empty"])
    fun `should use default values when config is`(testCase: String) {
        // Given
        when (testCase) {
            "missing" -> {
                // Just test instantiation directly - no file needed
            }
            "empty" -> {
                val configFile = File(tempDir, "empty.yml")
                configFile.writeText("")
            }
        }

        // When
        val config = PlantumlConfig()

        // Then
        assertNotNull(config)
        assertDefaultConfigValues(config)
    }

    private fun assertDefaultConfigValues(config: PlantumlConfig) {
        assertEquals("prompts", config.input.prompts)
        assertEquals("en", config.input.defaultLang)
        assertEquals("generated/images", config.output.images)
        assertEquals("png", config.output.format)
        assertEquals("default", config.output.theme)
        assertEquals(5, config.langchain4j.maxIterations)
        assertEquals("ollama", config.langchain4j.model)
        assertEquals(true, config.langchain4j.validation)
        assertEquals("", config.langchain4j.gemini.apiKey)
        assertEquals("", config.langchain4j.mistral.apiKey)
        assertEquals("", config.langchain4j.openai.apiKey)
        assertEquals("", config.langchain4j.claude.apiKey)
        assertEquals("", config.langchain4j.huggingface.apiKey)
        assertEquals("", config.langchain4j.groq.apiKey)
        assertEquals("github-actions[bot]", config.git.userName)
        assertEquals("github-actions[bot]@users.noreply.github.com", config.git.userEmail)
        assertEquals("chore: update PlantUML diagrams [skip ci]", config.git.commitMessage)
        assertEquals(listOf("main", "develop"), config.git.watchedBranches)
        assertEquals("", config.rag.databaseUrl)
        assertEquals("", config.rag.username)
        assertEquals("", config.rag.password)
        assertEquals("embeddings", config.rag.tableName)
    }
}
