package plantuml.apikey

class FreemiumWeightCalculator(
    private val freemiumRatio: Double = 0.0,
    private val quotaTracker: QuotaTracker
) {
    init {
        require(freemiumRatio in 0.0..1.0) {
            "freemiumRatio must be between 0.0 and 1.0, got $freemiumRatio"
        }
    }

    val isEnabled: Boolean get() = freemiumRatio > 0.0

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
