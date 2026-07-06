package plantuml.apikey

/**
 * Orchestrates API key selection across multiple provider pools with
 * cross-provider fallback (EPIC 13 S162).
 *
 * When a provider pool is entirely saturated (every entry has exceeded
 * its quota threshold), the orchestrator moves to the next provider in
 * the [fallbackOrder] chain and returns a key from there.
 *
 * Example: Gemini Pro saturated → Mistral Free → Ollama.
 *
 * @param pools Provider name → [ApiKeyPool]
 * @param fallbackOrder Ordered list of provider names to try
 */
class CrossProviderFallbackOrchestrator(
    private val pools: Map<String, ApiKeyPool>,
    private val fallbackOrder: List<String>
) {

    /**
     * Select the next available API key entry, trying each provider in
     * [fallbackOrder] until a non-saturated pool is found.
     *
     * @return The selected [ApiKeyEntry], or null when every provider in
     *         the fallback chain is saturated or absent
     */
    fun selectKey(): ApiKeyEntry? {
        for (providerName in fallbackOrder) {
            val pool = pools[providerName] ?: continue
            if (pool.isPoolSaturated()) continue
            return pool.getNextKey()
        }
        return null
    }
}