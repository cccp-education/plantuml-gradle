package plantuml.apikey

/**
 * API Provider enumeration.
 *
 * Represents supported API providers for the rotation pool.
 */
enum class Provider {
    GOOGLE,
    HUGGINGFACE,
    GROQ,
    OLLAMA,
    MISTRAL,
    GROK,
    OPENAI,
    ANTHROPIC,
    GITHUB,
    UNKNOWN
}

/**
 * Service Type enumeration.
 *
 * Represents different AI/ML services available from providers.
 */
enum class ServiceType {
    TEXT_GENERATION,
    IMAGE_GENERATION,
    CODE_GENERATION,
    EMBEDDINGS,
    SPEECH_TO_TEXT,
    TEXT_TO_SPEECH,
    TRANSLATION,
    CHAT_COMPLETION,
    VISION,
    CUSTOM
}

/**
 * Rotation Strategy enumeration.
 *
 * Defines how API keys are rotated within the pool.
 */
enum class RotationStrategy {
    ROUND_ROBIN,
    WEIGHTED,
    LEAST_USED,
    SMART,
    TIERED
}

/**
 * Key Tier enumeration.
 *
 * Represents the priority tier of an API key for tiered rotation.
 * Higher priority tiers are consumed first; lower tiers act as fallback.
 */
enum class KeyTier {
    ENTERPRISE,
    PRO,
    FREE
}
