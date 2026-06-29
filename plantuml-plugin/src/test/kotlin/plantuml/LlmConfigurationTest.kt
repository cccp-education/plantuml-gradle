package plantuml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class LlmConfigurationTest {

    companion object {
        private val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    }

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should load Ollama configuration correctly`() {
        // Given
        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText("""
            langchain4j:
              model: "ollama"
              ollama:
                baseUrl: "http://localhost:11438"
                modelName: "llama3:8b"
        """.trimIndent())

        // When
        val config = mapper.readValue(configFile, PlantumlConfig::class.java)

        // Then
        assertEquals("ollama", config.langchain4j.model)
        assertEquals("http://localhost:11438", config.langchain4j.ollama.baseUrl)
        assertEquals("llama3:8b", config.langchain4j.ollama.modelName)
    }

    @Test
    fun `should load Gemini configuration correctly`() {
        // Given
        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText("""
            langchain4j:
              model: "gemini"
              gemini:
                apiKey: "fake-gemini-key"
        """.trimIndent())

        // When
        val config = mapper.readValue(configFile, PlantumlConfig::class.java)

        // Then
        assertEquals("gemini", config.langchain4j.model)
        assertEquals("fake-gemini-key", config.langchain4j.gemini.apiKey)
    }

    @Test
    fun `should load Mistral configuration correctly`() {
        // Given
        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText("""
            langchain4j:
              model: "mistral"
              mistral:
                apiKey: "fake-mistral-key"
        """.trimIndent())

        // When
        val config = mapper.readValue(configFile, PlantumlConfig::class.java)

        // Then
        assertEquals("mistral", config.langchain4j.model)
        assertEquals("fake-mistral-key", config.langchain4j.mistral.apiKey)
    }

    @Test
    fun `should load OpenAI configuration correctly`() {
        // Given
        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText("""
            langchain4j:
              model: "openai"
              openai:
                apiKey: "fake-openai-key"
        """.trimIndent())

        // When
        val config = mapper.readValue(configFile, PlantumlConfig::class.java)

        // Then
        assertEquals("openai", config.langchain4j.model)
        assertEquals("fake-openai-key", config.langchain4j.openai.apiKey)
    }

    @Test
    fun `should load Claude configuration correctly`() {
        // Given
        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText("""
            langchain4j:
              model: "claude"
              claude:
                apiKey: "fake-claude-key"
        """.trimIndent())

        // When
        val config = mapper.readValue(configFile, PlantumlConfig::class.java)

        // Then
        assertEquals("claude", config.langchain4j.model)
        assertEquals("fake-claude-key", config.langchain4j.claude.apiKey)
    }

    @Test
    fun `should load HuggingFace configuration correctly`() {
        // Given
        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText("""
            langchain4j:
              model: "huggingface"
              huggingface:
                apiKey: "fake-huggingface-key"
        """.trimIndent())

        // When
        val config = mapper.readValue(configFile, PlantumlConfig::class.java)

        // Then
        assertEquals("huggingface", config.langchain4j.model)
        assertEquals("fake-huggingface-key", config.langchain4j.huggingface.apiKey)
    }

    @Test
    fun `should load Groq configuration correctly`() {
        // Given
        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText("""
            langchain4j:
              model: "groq"
              groq:
                apiKey: "fake-groq-key"
        """.trimIndent())

        // When
        val config = mapper.readValue(configFile, PlantumlConfig::class.java)

        // Then
        assertEquals("groq", config.langchain4j.model)
        assertEquals("fake-groq-key", config.langchain4j.groq.apiKey)
    }

    @Test
    fun `should handle mixed provider configurations`() {
        // Given
        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText("""
            langchain4j:
              model: "ollama"
              ollama:
                baseUrl: "http://localhost:11438"
                modelName: "llama3:8b"
              gemini:
                apiKey: "fake-gemini-key"
              mistral:
                apiKey: "fake-mistral-key"
              openai:
                apiKey: "fake-openai-key"
              claude:
                apiKey: "fake-claude-key"
              huggingface:
                apiKey: "fake-huggingface-key"
              groq:
                apiKey: "fake-groq-key"
        """.trimIndent())

        // When
        val config = mapper.readValue(configFile, PlantumlConfig::class.java)

        // Then
        assertEquals("ollama", config.langchain4j.model)
        assertEquals("http://localhost:11438", config.langchain4j.ollama.baseUrl)
        assertEquals("llama3:8b", config.langchain4j.ollama.modelName)
        assertEquals("fake-gemini-key", config.langchain4j.gemini.apiKey)
        assertEquals("fake-mistral-key", config.langchain4j.mistral.apiKey)
        assertEquals("fake-openai-key", config.langchain4j.openai.apiKey)
        assertEquals("fake-claude-key", config.langchain4j.claude.apiKey)
        assertEquals("fake-huggingface-key", config.langchain4j.huggingface.apiKey)
        assertEquals("fake-groq-key", config.langchain4j.groq.apiKey)
    }
}