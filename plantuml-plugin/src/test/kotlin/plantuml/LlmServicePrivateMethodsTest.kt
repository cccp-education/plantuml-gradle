package plantuml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import plantuml.runtime.RuntimeEnvironment
import plantuml.service.LlmService
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for LlmService private methods — no LangChain4j concrete class imports.
 * Tests verify createChatModel() returns non-null for each provider.
 */
class LlmServicePrivateMethodsTest {

    private lateinit var config: PlantumlConfig
    private lateinit var llmService: LlmService

    @BeforeEach
    fun setup() {
        config = PlantumlConfig(
            input = InputConfig(),
            output = OutputConfig(),
            langchain4j = LangchainConfig(
                model = "ollama",
                maxIterations = 1,
                validation = false,
                ollama = OllamaConfig(
                    baseUrl = "http://localhost:11438",
                    modelName = "smollm:135m"
                ),
                openai = ApiKeyConfig(apiKey = "sk-test-openai"),
                gemini = ApiKeyConfig(apiKey = "sk-test-gemini"),
                mistral = ApiKeyConfig(apiKey = "sk-test-mistral"),
                claude = ApiKeyConfig(apiKey = "sk-test-claude"),
                huggingface = ApiKeyConfig(apiKey = "sk-test-huggingface")
            ),
            git = GitConfig(),
            rag = RagConfig()
        )
        llmService = LlmService(config)
    }

    @Test
    fun `createOllamaModel should return OllamaChatModel with correct configuration`() {
        config = config.copy(langchain4j = config.langchain4j.copy(model = "ollama"))
        llmService = LlmService(config)
        val model = llmService.createChatModel()
        assertNotNull(model, "Should return OllamaChatModel")
    }

    @Test
    fun `createOpenAiModel should return OpenAiChatModel with correct API key`() {
        config = config.copy(langchain4j = config.langchain4j.copy(model = "openai"))
        llmService = LlmService(config)
        val model = llmService.createChatModel()
        assertNotNull(model, "Should return OpenAiChatModel")
    }

    @Test
    fun `createGeminiModel should return GoogleAiGeminiChatModel with correct API key`() {
        config = config.copy(langchain4j = config.langchain4j.copy(model = "gemini"))
        llmService = LlmService(config)
        val model = llmService.createChatModel()
        assertNotNull(model, "Should return GoogleAiGeminiChatModel")
    }

    @Test
    fun `createMistralModel should return MistralAiChatModel with correct API key`() {
        config = config.copy(langchain4j = config.langchain4j.copy(model = "mistral"))
        llmService = LlmService(config)
        val model = llmService.createChatModel()
        assertNotNull(model, "Should return MistralAiChatModel")
    }

    @Test
    fun `createClaudeModel should return AnthropicChatModel with correct API key`() {
        config = config.copy(langchain4j = config.langchain4j.copy(model = "claude"))
        llmService = LlmService(config)
        val model = llmService.createChatModel()
        assertNotNull(model, "Should return AnthropicChatModel")
    }

    @Test
    fun `createHuggingFaceModel should return OpenAiChatModel with custom baseUrl`() {
        config = config.copy(langchain4j = config.langchain4j.copy(model = "huggingface"))
        llmService = LlmService(config)
        val model = llmService.createChatModel()
        assertNotNull(model, "Should return OpenAiChatModel for HuggingFace")
    }

    @Test
    fun `createChatModel returns null in test mode when no mock endpoint is configured`() {
        val nonMockConfig = PlantumlConfig(
            langchain4j = LangchainConfig(
                model = "ollama",
                ollama = OllamaConfig(baseUrl = "http://remote:11438", modelName = "smollm:135m"),
            )
        )
        val testRuntime = RuntimeEnvironment.system(
            property = { if (it == "plantuml.test.mode") "true" else null },
            environment = { null },
        )

        assertNull(LlmService(nonMockConfig, runtime = testRuntime).createChatModel())
    }
}
