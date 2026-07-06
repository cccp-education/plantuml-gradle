package plantuml.apikey

/**
 * Tiered rotation strategy.
 *
 * Selects the next API key entry by priority tier (ENTERPRISE > PRO > FREE),
 * then by weight descending within the same tier.
 */
class TieredRotationStrategy {

    /**
     * Select the highest-priority entry from the given list.
     *
     * @param entries Candidate entries (any order)
     * @return The selected entry (highest tier, then highest weight)
     * @throws IllegalArgumentException if entries is empty
     */
    fun select(entries: List<ApiKeyEntry>): ApiKeyEntry {
        require(entries.isNotEmpty()) { "entries must not be empty" }

        return entries.maxWithOrNull(
            compareBy<ApiKeyEntry> { it.tier.priority() }
                .thenBy { it.weight }
        ) ?: error("unreachable")
    }

    private fun KeyTier.priority(): Int = when (this) {
        KeyTier.ENTERPRISE -> 3
        KeyTier.PRO -> 2
        KeyTier.FREE -> 1
    }
}