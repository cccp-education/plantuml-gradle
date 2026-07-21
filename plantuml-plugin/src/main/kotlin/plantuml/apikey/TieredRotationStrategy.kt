package plantuml.apikey

/**
 * Selects the highest-priority API key from a list using tiered rotation.
 *
 * Priority order: [KeyTier.ENTERPRISE] (3) > [KeyTier.PRO] (2) > [KeyTier.FREE] (1).
 * Within the same tier, keys are ordered by effective weight (optionally
 * adjusted by [FreemiumWeightCalculator]).
 *
 * @param weightCalculator Optional calculator that adjusts weights based on
 *   freemium ratio and quota usage; when disabled, raw [ApiKeyEntry.weight] is used
 */
class TieredRotationStrategy(
    private val weightCalculator: FreemiumWeightCalculator? = null
) {

    /**
     * Selects the best candidate from a non-empty list of entries.
     *
     * @param entries Non-empty list of [ApiKeyEntry] candidates
     * @return The highest-priority entry
     * @throws IllegalArgumentException if entries is empty
     */
    fun select(entries: List<ApiKeyEntry>): ApiKeyEntry {
        require(entries.isNotEmpty()) { "entries must not be empty" }

        return entries.maxWithOrNull(
            if (weightCalculator?.isEnabled == true) {
                compareBy<ApiKeyEntry> { effectiveWeight(it) }
                    .thenBy { it.tier.priority() }
            } else {
                compareBy<ApiKeyEntry> { it.tier.priority() }
                    .thenBy { effectiveWeight(it) }
            }
        ) ?: error("unreachable")
    }

    private fun effectiveWeight(entry: ApiKeyEntry): Int {
        return weightCalculator?.calculateWeight(entry) ?: entry.weight
    }

    private fun KeyTier.priority(): Int = when (this) {
        KeyTier.ENTERPRISE -> 3
        KeyTier.PRO -> 2
        KeyTier.FREE -> 1
    }
}