package plantuml.apikey

class TieredRotationStrategy(
    private val weightCalculator: FreemiumWeightCalculator? = null
) {

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