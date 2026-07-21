package plantuml.apikey

/**
 * Adjusts API key weights based on freemium ratio and quota usage.
 *
 * When enabled (freemiumRatio > 0), FREE tier keys receive a weight multiplier
 * of [freemiumRatio] while paid tiers receive `1.0 - freemiumRatio`. The
 * remaining quota percentage further scales the weight, so keys with more
 * available quota are preferred.
 *
 * @param freemiumRatio Proportion of traffic to route to FREE tier (0.0–1.0)
 * @param quotaTracker Tracks current usage per key
 */
class FreemiumWeightCalculator(
    private val freemiumRatio: Double = 0.0,
    private val quotaTracker: QuotaTracker
) {
    init {
        require(freemiumRatio in 0.0..1.0) {
            "freemiumRatio must be between 0.0 and 1.0, got $freemiumRatio"
        }
    }

    /** Whether freemium weight adjustment is active. */
    val isEnabled: Boolean get() = freemiumRatio > 0.0

    /**
     * Calculates the effective weight for an API key entry.
     *
     * @param entry The API key entry
     * @return Adjusted weight (never negative)
     */
    fun calculateWeight(entry: ApiKeyEntry): Int {
        if (!isEnabled) return entry.weight

        val usagePct = quotaTracker.getUsagePercentage(entry)
        val remainingRatio = (1.0 - usagePct / 100.0).coerceIn(0.0, 1.0)
        val tierMultiplier = when (entry.tier) {
            KeyTier.FREE -> freemiumRatio
            else -> 1.0 - freemiumRatio
        }
        return (entry.weight * remainingRatio * tierMultiplier).toInt().coerceAtLeast(0)
    }
}
