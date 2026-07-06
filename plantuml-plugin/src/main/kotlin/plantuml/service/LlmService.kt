package plantuml.service

import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.model.mistralai.MistralAiChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import plantuml.ApiKeyPoolEntry
import plantuml.PlantumlConfig
import plantuml.PoolQuotaConfig
import plantuml.apikey.ApiKeyPool
import plantuml.apikey.ApiKeyEntry
import plantuml.apikey.CrossProviderFallbackOrchestrator
import plantuml.apikey.Provider
import plantuml.apikey.QuotaAuditLogger
import plantuml.apikey.QuotaConfig
import java.time.Duration

/**
 * Service responsible for creating and configuring LLM (Large Language Model) instances.
 *
 * Supports 7 LLM providers via LangChain4j:
 * - **Ollama** (local models)
 * - **OpenAI** (GPT-4, GPT-3.5-turbo)
 * - **Google Gemini** (gemini-pro)
 * - **Mistral AI** (mistral-large-latest)
 * - **Anthropic Claude** (claude-3-opus)
 * - **HuggingFace** (API inference)
 * - **Groq** (llama3-8b)
 *
 * All providers are configured with temperature 0.7 for balanced creativity/accuracy.
 *
 * @param config PlantUML configuration containing LLM provider settings
 */
class LlmService(
    private val config: PlantumlConfig,
    internal var auditLogger: QuotaAuditLogger = QuotaAuditLogger()
) {

    private val apiKeyPools = mutableMapOf<String, ApiKeyPool>()
    private lateinit var fallbackOrchestrator: CrossProviderFallbackOrchestrator

    init {
        initializeApiKeyPools()
        initializeFallbackOrchestrator()
    }

    private fun initializeApiKeyPools() {
        val rotation = parseRotationStrategy(config.langchain4j.rotationStrategy)
        val fallback = config.langchain4j.fallbackEnabled

        val openaiPool = config.langchain4j.openai.pool
        if (openaiPool.isNotEmpty()) {
            apiKeyPools["openai"] = ApiKeyPool(openaiPool.map { it.toApiKeyEntry() }, rotation, fallbackEnabled = fallback)
        }

        val geminiPool = config.langchain4j.gemini.pool
        if (geminiPool.isNotEmpty()) {
            apiKeyPools["gemini"] = ApiKeyPool(geminiPool.map { it.toApiKeyEntry() }, rotation, fallbackEnabled = fallback)
        }

        val mistralPool = config.langchain4j.mistral.pool
        if (mistralPool.isNotEmpty()) {
            apiKeyPools["mistral"] = ApiKeyPool(mistralPool.map { it.toApiKeyEntry() }, rotation, fallbackEnabled = fallback)
        }

        val claudePool = config.langchain4j.claude.pool
        if (claudePool.isNotEmpty()) {
            apiKeyPools["claude"] = ApiKeyPool(claudePool.map { it.toApiKeyEntry() }, rotation, fallbackEnabled = fallback)
        }

        val huggingfacePool = config.langchain4j.huggingface.pool
        if (huggingfacePool.isNotEmpty()) {
            apiKeyPools["huggingface"] = ApiKeyPool(huggingfacePool.map { it.toApiKeyEntry() }, rotation, fallbackEnabled = fallback)
        }

        val groqPool = config.langchain4j.groq.pool
        if (groqPool.isNotEmpty()) {
            apiKeyPools["groq"] = ApiKeyPool(groqPool.map { it.toApiKeyEntry() }, rotation, fallbackEnabled = fallback)
        }
    }

    /**
     * Build the cross-provider fallback orchestrator.
     *
     * The fallback order starts with the active provider, then every other
     * configured provider pool, in a stable order. This lets the plugin
     * keep running when the primary provider is entirely saturated
     * (EPIC 13 S162).
     */
    private fun initializeFallbackOrchestrator() {
        val active = config.langchain4j.model.lowercase()
        val others = apiKeyPools.keys.filter { it != active }.sorted()
        val order = listOf(active) + others
        fallbackOrchestrator = CrossProviderFallbackOrchestrator(apiKeyPools.toMap(), order)
    }

    private fun parseRotationStrategy(value: String): plantuml.apikey.RotationStrategy {
        return runCatching {
            plantuml.apikey.RotationStrategy.valueOf(value.uppercase())
        }.getOrDefault(plantuml.apikey.RotationStrategy.ROUND_ROBIN)
    }

    private fun resolveProvider(name: String): Provider {
        val upper = name.uppercase()
        return when (upper) {
            "GEMINI" -> Provider.GOOGLE
            "CLAUDE" -> Provider.ANTHROPIC
            else -> Provider.entries.find { it.name == upper } ?: Provider.UNKNOWN
        }
    }

    private fun ApiKeyPoolEntry.toApiKeyEntry(): ApiKeyEntry {
        return ApiKeyEntry(
            id = this.id,
            email = this.email,
            name = this.name,
            keyRef = this.keyRef,
            provider = resolveProvider(this.provider),
            services = this.services.map { plantuml.apikey.ServiceType.valueOf(it.uppercase()) },
            tier = parseTier(this.tier),
            weight = this.weight,
            quota = QuotaConfig(
                limitValue = this.quota.limitValue,
                thresholdPercent = this.quota.thresholdPercent
            )
        )
    }

    private fun parseTier(value: String): plantuml.apikey.KeyTier {
        return runCatching {
            plantuml.apikey.KeyTier.valueOf(value.uppercase())
        }.getOrDefault(plantuml.apikey.KeyTier.FREE)
    }

    private fun getApiKeyFromPool(provider: String): String? {
        val keyEntry = selectKeyWithCrossProviderFallback(provider)

        if (keyEntry != null) {
            auditLogger.logInfo(keyEntry.provider, "Using API key from pool: ${keyEntry.id}")
        }

        return keyEntry?.keyRef
    }

    /**
     * Select an API key entry, preferring the requested provider and
     * falling back to the next provider pool when the primary one is
     * entirely saturated (EPIC 13 S162).
     */
    private fun selectKeyWithCrossProviderFallback(provider: String): ApiKeyEntry? {
        val primary = apiKeyPools[provider.lowercase()]
        if (primary != null && !primary.isPoolSaturated()) {
            return primary.getNextKey()
        }
        return fallbackOrchestrator.selectKey()
    }

    /**
     * Creates and configures a LangChain4j [ChatModel] based on the active provider.
     *
     * @return A configured [ChatModel] instance for the selected provider, or null in simple test mode
     * @throws IllegalArgumentException if provider name is invalid
     */
    fun createChatModel(): ChatModel? {
        val provider = config.langchain4j.model
        
        auditLogger.logInfo(
            resolveProvider(provider),
            "Creating chat model for provider: $provider"
        )
        
        // Return null only in simple test mode without mock LLM server
        // If mock LLM server is configured (localhost baseUrl), use real Ollama model
        val isTestMode = System.getProperty("plantuml.test.mode") == "true" || System.getenv("TEST_ENV") == "true"
        val isMockConfigured = config.langchain4j.ollama.baseUrl.contains("localhost")
        
        if (isTestMode && !isMockConfigured) {
            return null
        }
        
        return when (config.langchain4j.model.lowercase()) {
            "ollama" -> createOllamaModel()
            "openai" -> createOpenAiModel()
            "gemini" -> createGeminiModel()
            "mistral" -> createMistralModel()
            "claude" -> createClaudeModel()
            "huggingface" -> createHuggingFaceModel()
            "groq" -> createGroqModel()
            else -> {
                auditLogger.logError(
                    plantuml.apikey.Provider.UNKNOWN,
                    "Unknown provider: $provider, defaulting to Ollama"
                )
                createOllamaModel()
            }
        }
    }

    /**
     * Creates an Ollama chat model for local LLM inference.
     *
     * @return Configured [OllamaChatModel] with baseUrl and modelName from config
     */
    private fun createOllamaModel(): ChatModel = OllamaChatModel.builder()
        .baseUrl(config.langchain4j.ollama.baseUrl)
        .modelName(config.langchain4j.ollama.modelName)
        .temperature(0.7)
        .timeout(Duration.ofSeconds(getTimeoutInSeconds()))
        .build()

    /**
     * Creates an OpenAI chat model.
     *
     * @return Configured [OpenAiChatModel] with GPT-4 model
     */
    private fun createOpenAiModel(): ChatModel {
        val apiKey = getApiKeyFromPool("openai") ?: config.langchain4j.openai.apiKey
        val builder = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(config.langchain4j.openai.modelName)
            .temperature(0.7)
            .timeout(Duration.ofSeconds(getTimeoutInSeconds()))
        
        if (config.langchain4j.openai.baseUrl.isNotBlank()) {
            builder.baseUrl(config.langchain4j.openai.baseUrl)
        }
        
        return builder.build()
    }

    /**
     * Creates a Google Gemini chat model.
     *
     * @return Configured [GoogleAiGeminiChatModel] with gemini-pro model
     */
    private fun createGeminiModel(): ChatModel {
        val apiKey = getApiKeyFromPool("gemini") ?: config.langchain4j.gemini.apiKey
        return GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName(config.langchain4j.gemini.modelName)
            .temperature(0.7)
            .timeout(Duration.ofSeconds(getTimeoutInSeconds()))
            .build()
    }

    /**
     * Creates a Mistral AI chat model.
     *
     * @return Configured [MistralAiChatModel] with mistral-large-latest model
     */
    private fun createMistralModel(): ChatModel {
        val apiKey = getApiKeyFromPool("mistral") ?: config.langchain4j.mistral.apiKey
        val builder = MistralAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(config.langchain4j.mistral.modelName)
            .temperature(0.7)
            .timeout(Duration.ofSeconds(getTimeoutInSeconds()))
        
        if (config.langchain4j.mistral.baseUrl.isNotBlank()) {
            builder.baseUrl(config.langchain4j.mistral.baseUrl)
        }
        
        return builder.build()
    }

    /**
     * Creates an Anthropic Claude chat model.
     *
     * @return Configured [AnthropicChatModel] with claude-3-opus-20240229 model
     */
    private fun createClaudeModel(): ChatModel {
        val apiKey = getApiKeyFromPool("claude") ?: config.langchain4j.claude.apiKey
        val builder = AnthropicChatModel.builder()
            .apiKey(apiKey)
            .modelName(config.langchain4j.claude.modelName)
            .temperature(0.7)
            .timeout(Duration.ofSeconds(getTimeoutInSeconds()))
        
        if (config.langchain4j.claude.baseUrl.isNotBlank()) {
            builder.baseUrl(config.langchain4j.claude.baseUrl)
        }
        
        return builder.build()
    }

    /**
     * Creates a HuggingFace chat model via OpenAI-compatible API.
     *
     * @return Configured [OpenAiChatModel] pointing to HuggingFace inference API
     */
    private fun createHuggingFaceModel(): ChatModel {
        val apiKey = getApiKeyFromPool("huggingface") ?: config.langchain4j.huggingface.apiKey
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl("https://api-inference.huggingface.co")
            .modelName("gpt2") // Default model, can be made configurable
            .temperature(0.7)
            .timeout(Duration.ofSeconds(getTimeoutInSeconds()))
            .build()
    }

    /**
     * Creates a Groq chat model via OpenAI-compatible API.
     *
     * @return Configured [OpenAiChatModel] pointing to Groq API with llama3-8b model
     */
    private fun createGroqModel(): ChatModel {
        val apiKey = getApiKeyFromPool("groq") ?: config.langchain4j.groq.apiKey
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl("https://api.groq.com/openai/v1")
            .modelName("llama3-8b-8192") // Default model, can be made configurable
            .temperature(0.7)
            .timeout(Duration.ofSeconds(getTimeoutInSeconds()))
            .build()
    }
    
    /**
     * Returns timeout duration in seconds based on environment.
     *
     * @return 5 seconds in test environment (TEST_ENV=true), 60 seconds otherwise
     */
    private fun getTimeoutInSeconds(): Long {
        // In test environments, use shorter timeouts to avoid hanging tests
        return if (System.getenv("TEST_ENV") == "true") 5L else 60L
    }
}