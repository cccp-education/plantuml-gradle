package plantuml.service

import plantuml.ApiKeyConfig

/**
 * Resolves the effective model name for the providers whose model was hardcoded
 * (PLT-CR3-1, EPIC PLT-CR3).
 *
 * HuggingFace and Groq were the only two providers ignoring
 * `langchain4j.<provider>.modelName` — the config field already existed
 * (`ApiKeyConfig.modelName`) and is merged through the 4-layer cascade, but
 * `LlmService` hardcoded `gpt2` / `llama3-8b-8192`. This pure resolver makes the
 * model configurable while preserving backward compatibility: a blank config
 * keeps the historical default.
 *
 * Pure function (no LangChain4j, no I/O) — unit-testable in isolation (DDD).
 */
object ProviderModelResolver {

    /** HuggingFace default model when the config does not override it. */
    const val HUGGING_FACE_DEFAULT = "gpt2"

    /** Groq default model when the config does not override it. */
    const val GROQ_DEFAULT = "llama3-8b-8192"

    /**
     * Effective HuggingFace model: the configured `modelName` when non-blank,
     * otherwise [HUGGING_FACE_DEFAULT].
     */
    fun huggingFace(config: ApiKeyConfig): String =
        config.modelName.trim().ifBlank { HUGGING_FACE_DEFAULT }

    /**
     * Effective Groq model: the configured `modelName` when non-blank,
     * otherwise [GROQ_DEFAULT].
     */
    fun groq(config: ApiKeyConfig): String =
        config.modelName.trim().ifBlank { GROQ_DEFAULT }
}
