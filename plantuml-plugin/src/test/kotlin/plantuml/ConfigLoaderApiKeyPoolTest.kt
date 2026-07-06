package plantuml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for ConfigLoader with API Key Pool YAML parsing support.
 */
class ConfigLoaderApiKeyPoolTest {

    @Test
    @DisplayName("should parse pool configuration for OpenAI")
    fun `should parse pool configuration for OpenAI`() {
        // Given
        val tempFile = File.createTempFile("test-config-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: openai
              openai:
                pool:
                  - id: "openai-key-1"
                    email: "test1@example.com"
                    name: "OpenAI Key #1"
                    keyRef: "OPENAI_API_KEY_1"
                    provider: "OPENAI"
                    services:
                      - "CHAT_COMPLETION"
                    quota:
                      limitValue: 1000
                      thresholdPercent: 80
                  - id: "openai-key-2"
                    email: "test2@example.com"
                    name: "OpenAI Key #2"
                    keyRef: "OPENAI_API_KEY_2"
                    provider: "OPENAI"
                    services:
                      - "CHAT_COMPLETION"
                    quota:
                      limitValue: 2000
                      thresholdPercent: 75
        """.trimIndent())
        tempFile.deleteOnExit()

        // When
        val config = ConfigLoader.load(tempFile)

        // Then
        assertNotNull(config.langchain4j.openai.pool)
        assertEquals(2, config.langchain4j.openai.pool.size)
        assertEquals("openai-key-1", config.langchain4j.openai.pool[0].id)
        assertEquals("openai-key-2", config.langchain4j.openai.pool[1].id)
    }

    @Test
    @DisplayName("should parse pool configuration for Gemini")
    fun `should parse pool configuration for Gemini`() {
        // Given
        val tempFile = File.createTempFile("test-config-gemini-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: gemini
              gemini:
                pool:
                  - id: "gemini-key-1"
                    email: "google@example.com"
                    name: "Gemini Key #1"
                    keyRef: "GEMINI_API_KEY_1"
                    provider: "GOOGLE"
                    services:
                      - "CHAT_COMPLETION"
                    quota:
                      limitValue: 500
                      thresholdPercent: 90
        """.trimIndent())
        tempFile.deleteOnExit()

        // When
        val config = ConfigLoader.load(tempFile)

        // Then
        assertNotNull(config.langchain4j.gemini.pool)
        assertEquals(1, config.langchain4j.gemini.pool.size)
        assertEquals("gemini-key-1", config.langchain4j.gemini.pool[0].id)
        assertEquals("google@example.com", config.langchain4j.gemini.pool[0].email)
    }

    @Test
    @DisplayName("should parse pool configuration for Mistral")
    fun `should parse pool configuration for Mistral`() {
        // Given
        val tempFile = File.createTempFile("test-config-mistral-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: mistral
              mistral:
                pool:
                  - id: "mistral-key-1"
                    email: "mistral@example.com"
                    name: "Mistral Key #1"
                    keyRef: "MISTRAL_API_KEY_1"
                    provider: "MISTRAL"
                    services:
                      - "CHAT_COMPLETION"
                    quota:
                      limitValue: 750
                      thresholdPercent: 85
        """.trimIndent())
        tempFile.deleteOnExit()

        // When
        val config = ConfigLoader.load(tempFile)

        // Then
        assertNotNull(config.langchain4j.mistral.pool)
        assertEquals(1, config.langchain4j.mistral.pool.size)
        assertEquals("mistral-key-1", config.langchain4j.mistral.pool[0].id)
    }

    @Test
    @DisplayName("should parse pool configuration for Claude")
    fun `should parse pool configuration for Claude`() {
        // Given
        val tempFile = File.createTempFile("test-config-claude-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: claude
              claude:
                pool:
                  - id: "claude-key-1"
                    email: "anthropic@example.com"
                    name: "Claude Key #1"
                    keyRef: "ANTHROPIC_API_KEY_1"
                    provider: "ANTHROPIC"
                    services:
                      - "CHAT_COMPLETION"
                    quota:
                      limitValue: 1500
                      thresholdPercent: 70
        """.trimIndent())
        tempFile.deleteOnExit()

        // When
        val config = ConfigLoader.load(tempFile)

        // Then
        assertNotNull(config.langchain4j.claude.pool)
        assertEquals(1, config.langchain4j.claude.pool.size)
        assertEquals("claude-key-1", config.langchain4j.claude.pool[0].id)
    }

    @Test
    @DisplayName("should parse pool configuration for HuggingFace")
    fun `should parse pool configuration for HuggingFace`() {
        // Given
        val tempFile = File.createTempFile("test-config-hf-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: huggingface
              huggingface:
                pool:
                  - id: "hf-key-1"
                    email: "hf@example.com"
                    name: "HuggingFace Key #1"
                    keyRef: "HUGGINGFACE_API_KEY_1"
                    provider: "HUGGINGFACE"
                    services:
                      - "TEXT_GENERATION"
                    quota:
                      limitValue: 300
                      thresholdPercent: 80
        """.trimIndent())
        tempFile.deleteOnExit()

        // When
        val config = ConfigLoader.load(tempFile)

        // Then
        assertNotNull(config.langchain4j.huggingface.pool)
        assertEquals(1, config.langchain4j.huggingface.pool.size)
        assertEquals("hf-key-1", config.langchain4j.huggingface.pool[0].id)
    }

    @Test
    @DisplayName("should parse pool configuration for Groq")
    fun `should parse pool configuration for Groq`() {
        // Given
        val tempFile = File.createTempFile("test-config-groq-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: groq
              groq:
                pool:
                  - id: "groq-key-1"
                    email: "groq@example.com"
                    name: "Groq Key #1"
                    keyRef: "GROQ_API_KEY_1"
                    provider: "GROQ"
                    services:
                      - "CHAT_COMPLETION"
                    quota:
                      limitValue: 1000
                      thresholdPercent: 80
        """.trimIndent())
        tempFile.deleteOnExit()

        // When
        val config = ConfigLoader.load(tempFile)

        // Then
        assertNotNull(config.langchain4j.groq.pool)
        assertEquals(1, config.langchain4j.groq.pool.size)
        assertEquals("groq-key-1", config.langchain4j.groq.pool[0].id)
    }

    @Test
    @DisplayName("should parse multiple provider pools")
    fun `should parse multiple provider pools`() {
        // Given
        val tempFile = File.createTempFile("test-config-multi-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: openai
              openai:
                pool:
                  - id: "openai-key-1"
                    email: "test1@example.com"
                    name: "OpenAI Key #1"
                    keyRef: "OPENAI_API_KEY_1"
                    provider: "OPENAI"
                    services:
                      - "CHAT_COMPLETION"
                    quota:
                      limitValue: 1000
                      thresholdPercent: 80
              gemini:
                pool:
                  - id: "gemini-key-1"
                    email: "google@example.com"
                    name: "Gemini Key #1"
                    keyRef: "GEMINI_API_KEY_1"
                    provider: "GOOGLE"
                    services:
                      - "CHAT_COMPLETION"
                    quota:
                      limitValue: 500
                      thresholdPercent: 90
        """.trimIndent())
        tempFile.deleteOnExit()

        // When
        val config = ConfigLoader.load(tempFile)

        // Then
        assertEquals(1, config.langchain4j.openai.pool.size)
        assertEquals(1, config.langchain4j.gemini.pool.size)
        assertEquals("openai-key-1", config.langchain4j.openai.pool[0].id)
        assertEquals("gemini-key-1", config.langchain4j.gemini.pool[0].id)
    }

    @Test
    @DisplayName("should parse empty pool as empty list")
    fun `should parse empty pool as empty list`() {
        // Given
        val tempFile = File.createTempFile("test-config-empty-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: openai
              openai:
                pool: []
        """.trimIndent())
        tempFile.deleteOnExit()

        // When
        val config = ConfigLoader.load(tempFile)

        // Then
        assertNotNull(config.langchain4j.openai.pool)
        assertEquals(0, config.langchain4j.openai.pool.size)
    }

    @Test
    @DisplayName("should parse pool with baseUrl")
    fun `should parse pool with baseUrl`() {
        val tempFile = File.createTempFile("test-config-baseurl-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: openai
              openai:
                baseUrl: "https://custom.openai.api"
                pool:
                  - id: "openai-custom-1"
                    email: "custom@example.com"
                    name: "OpenAI Custom #1"
                    keyRef: "OPENAI_CUSTOM_KEY_1"
                    provider: "OPENAI"
                    services:
                      - "CHAT_COMPLETION"
                    baseUrl: "https://custom.openai.api"
                    quota:
                      limitValue: 1000
                      thresholdPercent: 80
        """.trimIndent())
        tempFile.deleteOnExit()

        val config = ConfigLoader.load(tempFile)

        assertNotNull(config.langchain4j.openai.pool)
        assertEquals(1, config.langchain4j.openai.pool.size)
        assertEquals("https://custom.openai.api", config.langchain4j.openai.pool[0].baseUrl)
    }

    @Test
    @DisplayName("should parse tier and weight from pool entries")
    fun `should parse tier and weight from pool entries`() {
        val tempFile = File.createTempFile("test-config-tier-pool", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: openai
              openai:
                pool:
                  - id: "ent-key-1"
                    email: "ent@example.com"
                    name: "Enterprise Key"
                    keyRef: "ENT_API_KEY_1"
                    provider: "OPENAI"
                    services:
                      - "CHAT_COMPLETION"
                    tier: "ENTERPRISE"
                    weight: 5
                    quota:
                      limitValue: 10000
                      thresholdPercent: 80
                  - id: "free-key-1"
                    email: "free@example.com"
                    name: "Free Key"
                    keyRef: "FREE_API_KEY_1"
                    provider: "OPENAI"
                    services:
                      - "CHAT_COMPLETION"
                    quota:
                      limitValue: 100
                      thresholdPercent: 80
        """.trimIndent())
        tempFile.deleteOnExit()

        val config = ConfigLoader.load(tempFile)

        assertEquals(2, config.langchain4j.openai.pool.size)
        val ent = config.langchain4j.openai.pool[0]
        assertEquals("ENTERPRISE", ent.tier)
        assertEquals(5, ent.weight)
        val free = config.langchain4j.openai.pool[1]
        assertEquals("FREE", free.tier)
        assertEquals(1, free.weight)
    }

    @Test
    @DisplayName("should parse tiered rotation strategy at langchain4j level")
    fun `should parse tiered rotation strategy at langchain4j level`() {
        val tempFile = File.createTempFile("test-config-rotation", ".yml")
        tempFile.writeText("""
            langchain4j:
              model: openai
              rotationStrategy: "TIERED"
              fallbackEnabled: false
              openai:
                pool:
                  - id: "k1"
                    email: "e@e.com"
                    name: "K1"
                    keyRef: "K1"
                    provider: "OPENAI"
                    services:
                      - "CHAT_COMPLETION"
        """.trimIndent())
        tempFile.deleteOnExit()

        val config = ConfigLoader.load(tempFile)

        assertEquals("TIERED", config.langchain4j.rotationStrategy)
        assertEquals(false, config.langchain4j.fallbackEnabled)
    }
}
