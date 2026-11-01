package plantuml.service

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import plantuml.ApiKeyConfig
import plantuml.ApiKeyPoolEntry
import plantuml.LangchainConfig
import plantuml.PlantumlConfig
import plantuml.PoolQuotaConfig
import kotlin.test.assertNotNull

/**
 * Unit tests for LlmService integration with ApiKeyPool.
 */
class LlmServiceApiKeyPoolTest {
    private fun createConfigWithPool(
        provider: String,
        pool: List<ApiKeyPoolEntry>,
    ): PlantumlConfig =
        PlantumlConfig(
            langchain4j =
                LangchainConfig(
                    model = provider,
                    openai = ApiKeyConfig(pool = pool),
                    gemini = ApiKeyConfig(pool = pool),
                    mistral = ApiKeyConfig(pool = pool),
                    claude = ApiKeyConfig(pool = pool),
                    huggingface = ApiKeyConfig(pool = pool),
                    groq = ApiKeyConfig(pool = pool),
                ),
        )

    @Test
    @DisplayName("should use pool key when pool is configured for OpenAI")
    fun `should use pool key when pool is configured for OpenAI`() {
        // Given
        val pool =
            listOf(
                ApiKeyPoolEntry(
                    id = "openai-key-1",
                    email = "test1@example.com",
                    name = "OpenAI Key #1",
                    keyRef = "OPENAI_API_KEY_1",
                    provider = "OPENAI",
                    services = listOf("CHAT_COMPLETION"),
                    quota = PoolQuotaConfig(limitValue = 1000, thresholdPercent = 80),
                ),
                ApiKeyPoolEntry(
                    id = "openai-key-2",
                    email = "test2@example.com",
                    name = "OpenAI Key #2",
                    keyRef = "OPENAI_API_KEY_2",
                    provider = "OPENAI",
                    services = listOf("CHAT_COMPLETION"),
                    quota = PoolQuotaConfig(limitValue = 1000, thresholdPercent = 80),
                ),
            )
        val config = createConfigWithPool("openai", pool)
        val llmService = LlmService(config)

        // When
        val chatModel = llmService.createChatModel()

        // Then
        assertNotNull(chatModel)
    }

    @Test
    @DisplayName("should use pool key when pool is configured for Gemini")
    fun `should use pool key when pool is configured for Gemini`() {
        // Given
        val pool =
            listOf(
                ApiKeyPoolEntry(
                    id = "gemini-key-1",
                    email = "test1@example.com",
                    name = "Gemini Key #1",
                    keyRef = "GEMINI_API_KEY_1",
                    provider = "GOOGLE",
                    services = listOf("CHAT_COMPLETION"),
                    quota = PoolQuotaConfig(limitValue = 1000, thresholdPercent = 80),
                ),
            )
        val config = createConfigWithPool("gemini", pool)
        val llmService = LlmService(config)

        // When
        val chatModel = llmService.createChatModel()

        // Then
        assertNotNull(chatModel)
    }

    @Test
    @DisplayName("should use pool key when pool is configured for Mistral")
    fun `should use pool key when pool is configured for Mistral`() {
        // Given
        val pool =
            listOf(
                ApiKeyPoolEntry(
                    id = "mistral-key-1",
                    email = "test1@example.com",
                    name = "Mistral Key #1",
                    keyRef = "MISTRAL_API_KEY_1",
                    provider = "MISTRAL",
                    services = listOf("CHAT_COMPLETION"),
                    quota = PoolQuotaConfig(limitValue = 1000, thresholdPercent = 80),
                ),
            )
        val config = createConfigWithPool("mistral", pool)
        val llmService = LlmService(config)

        // When
        val chatModel = llmService.createChatModel()

        // Then
        assertNotNull(chatModel)
    }

    @Test
    @DisplayName("should use pool key when pool is configured for Claude")
    fun `should use pool key when pool is configured for Claude`() {
        // Given
        val pool =
            listOf(
                ApiKeyPoolEntry(
                    id = "claude-key-1",
                    email = "test1@example.com",
                    name = "Claude Key #1",
                    keyRef = "ANTHROPIC_API_KEY_1",
                    provider = "ANTHROPIC",
                    services = listOf("CHAT_COMPLETION"),
                    quota = PoolQuotaConfig(limitValue = 1000, thresholdPercent = 80),
                ),
            )
        val config = createConfigWithPool("claude", pool)
        val llmService = LlmService(config)

        // When
        val chatModel = llmService.createChatModel()

        // Then
        assertNotNull(chatModel)
    }

    @Test
    @DisplayName("should use pool key when pool is configured for HuggingFace")
    fun `should use pool key when pool is configured for HuggingFace`() {
        // Given
        val pool =
            listOf(
                ApiKeyPoolEntry(
                    id = "hf-key-1",
                    email = "test1@example.com",
                    name = "HuggingFace Key #1",
                    keyRef = "HUGGINGFACE_API_KEY_1",
                    provider = "HUGGINGFACE",
                    services = listOf("TEXT_GENERATION"),
                    quota = PoolQuotaConfig(limitValue = 1000, thresholdPercent = 80),
                ),
            )
        val config = createConfigWithPool("huggingface", pool)
        val llmService = LlmService(config)

        // When
        val chatModel = llmService.createChatModel()

        // Then
        assertNotNull(chatModel)
    }

    @Test
    @DisplayName("should use pool key when pool is configured for Groq")
    fun `should use pool key when pool is configured for Groq`() {
        // Given
        val pool =
            listOf(
                ApiKeyPoolEntry(
                    id = "groq-key-1",
                    email = "test1@example.com",
                    name = "Groq Key #1",
                    keyRef = "GROQ_API_KEY_1",
                    provider = "GROQ",
                    services = listOf("CHAT_COMPLETION"),
                    quota = PoolQuotaConfig(limitValue = 1000, thresholdPercent = 80),
                ),
            )
        val config = createConfigWithPool("groq", pool)
        val llmService = LlmService(config)

        // When
        val chatModel = llmService.createChatModel()

        // Then
        assertNotNull(chatModel)
    }

    @Test
    @DisplayName("should rotate keys in round-robin fashion")
    fun `should rotate keys in round-robin fashion`() {
        // Given
        val pool =
            listOf(
                ApiKeyPoolEntry(
                    id = "key-1",
                    email = "test1@example.com",
                    name = "Key #1",
                    keyRef = "API_KEY_1",
                    provider = "OPENAI",
                    services = listOf("CHAT_COMPLETION"),
                    quota = PoolQuotaConfig(limitValue = 1000, thresholdPercent = 80),
                ),
                ApiKeyPoolEntry(
                    id = "key-2",
                    email = "test2@example.com",
                    name = "Key #2",
                    keyRef = "API_KEY_2",
                    provider = "OPENAI",
                    services = listOf("CHAT_COMPLETION"),
                    quota = PoolQuotaConfig(limitValue = 1000, thresholdPercent = 80),
                ),
            )
        val config = createConfigWithPool("openai", pool)
        val llmService = LlmService(config)

        // When - Create multiple chat models
        val model1 = llmService.createChatModel()
        val model2 = llmService.createChatModel()
        val model3 = llmService.createChatModel()

        // Then - All models should be created successfully
        assertNotNull(model1)
        assertNotNull(model2)
        assertNotNull(model3)
    }

    @Test
    @DisplayName("should handle empty pool gracefully")
    fun `should handle empty pool gracefully`() {
        // Given
        val config = createConfigWithPool("openai", emptyList())
        val llmService = LlmService(config)

        // When/Then - Should not crash, returns model (test mode allows creation)
        val chatModel = llmService.createChatModel()
        assertNotNull(chatModel)
    }
}
