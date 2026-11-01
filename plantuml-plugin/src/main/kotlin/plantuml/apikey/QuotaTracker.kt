package plantuml.apikey

/**
 * Tracks API key usage and quota consumption.
 *
 * Monitors usage per key and provides quota threshold checking
 * for automatic rotation triggers.
 */
class QuotaTracker {
    private val usageCounts = mutableMapOf<String, Long>()

    /**
     * Track a usage event for the given API key.
     *
     * @param entryId The ID of the API key entry
     */
    fun trackUsage(entryId: String) {
        usageCounts[entryId] = (usageCounts[entryId] ?: 0) + 1
    }

    /**
     * Get the current usage count for an API key.
     *
     * @param entryId The ID of the API key entry
     * @return The current usage count
     */
    fun getUsage(entryId: String): Long = usageCounts[entryId] ?: 0

    /**
     * Check if the API key has exceeded its quota threshold.
     *
     * @param entry The API key entry to check
     * @return true if quota threshold is exceeded, false otherwise
     */
    fun isQuotaExceeded(entry: ApiKeyEntry): Boolean {
        val quota = entry.quota
        val currentUsage = usageCounts[entry.id] ?: 0
        val threshold = (quota.limitValue * quota.thresholdPercent) / 100
        return currentUsage >= threshold
    }

    /**
     * Reset the usage count for a specific API key.
     *
     * @param entryId The ID of the API key entry to reset
     */
    fun reset(entryId: String) {
        usageCounts[entryId] = 0
    }

    /**
     * Reset all usage counts.
     */
    fun resetAll() {
        usageCounts.clear()
    }

    /**
     * Get the usage percentage for an API key.
     *
     * @param entry The API key entry
     * @return The usage percentage (0.0 to 100.0)
     */
    fun getUsagePercentage(entry: ApiKeyEntry): Double {
        val currentUsage = usageCounts[entry.id] ?: 0
        val percentage = (currentUsage.toDouble() / entry.quota.limitValue) * 100.0
        return percentage.coerceIn(0.0, 100.0)
    }
}
