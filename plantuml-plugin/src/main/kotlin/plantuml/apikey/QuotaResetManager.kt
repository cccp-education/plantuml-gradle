package plantuml.apikey

import java.time.LocalDateTime

/**
 * Manages automatic quota reset for API keys.
 *
 * Monitors quota consumption and triggers automatic resets
 * based on configured reset policies.
 *
 * @param tracker The QuotaTracker to monitor
 * @param autoResetEnabled Enable automatic reset when quota exceeded
 */
class QuotaResetManager(
    private val tracker: QuotaTracker,
    private val autoResetEnabled: Boolean = true,
) {
    private val resetTimestamps = mutableMapOf<String, LocalDateTime>()
    private val resetCounts = mutableMapOf<String, Int>()

    /**
     * Check and perform automatic reset if quota exceeded and policy allows.
     *
     * @param entry The API key entry to check
     * @return true if reset was performed, false otherwise
     */
    fun checkAndReset(entry: ApiKeyEntry): Boolean {
        if (!autoResetEnabled) {
            return false
        }

        if (!tracker.isQuotaExceeded(entry)) {
            return false
        }

        when (entry.quota.resetPolicy) {
            ResetPolicy.DAILY -> {
                if (shouldResetDaily(entry.id)) {
                    performReset(entry.id)
                    return true
                }
            }
            ResetPolicy.WEEKLY -> {
                if (shouldResetWeekly(entry.id)) {
                    performReset(entry.id)
                    return true
                }
            }
            ResetPolicy.MONTHLY -> {
                if (shouldResetMonthly(entry.id)) {
                    performReset(entry.id)
                    return true
                }
            }
            ResetPolicy.MANUAL -> {
                // Manual reset only, no automatic reset
                return false
            }
            ResetPolicy.NEVER -> {
                // No reset allowed
                return false
            }
        }

        return false
    }

    /**
     * Perform a manual reset for an API key.
     *
     * @param entryId The ID of the API key entry to reset
     */
    fun manualReset(entryId: String) {
        tracker.reset(entryId)
        resetTimestamps[entryId] = LocalDateTime.now()
        resetCounts[entryId] = (resetCounts[entryId] ?: 0) + 1
    }

    /**
     * Get the number of times an API key has been reset.
     *
     * @param entryId The ID of the API key entry
     * @return The number of resets
     */
    fun getResetCount(entryId: String): Int = resetCounts[entryId] ?: 0

    /**
     * Get the last reset timestamp for an API key.
     *
     * @param entryId The ID of the API key entry
     * @return The last reset timestamp, or null if never reset
     */
    fun getLastReset(entryId: String): LocalDateTime? = resetTimestamps[entryId]

    /**
     * Check if daily reset should be performed.
     */
    private fun shouldResetDaily(entryId: String): Boolean {
        val lastReset = resetTimestamps[entryId]
        return lastReset == null || lastReset.toLocalDate() < LocalDateTime.now().toLocalDate()
    }

    /**
     * Check if weekly reset should be performed.
     */
    private fun shouldResetWeekly(entryId: String): Boolean {
        val lastReset = resetTimestamps[entryId]
        if (lastReset == null) return true

        val currentWeek =
            LocalDateTime
                .now()
                .toLocalDate()
                .atStartOfDay()
                .with(
                    java.time.temporal.TemporalAdjusters
                        .previousOrSame(java.time.DayOfWeek.MONDAY),
                )
        val lastResetWeek =
            lastReset
                .toLocalDate()
                .atStartOfDay()
                .with(
                    java.time.temporal.TemporalAdjusters
                        .previousOrSame(java.time.DayOfWeek.MONDAY),
                )

        return currentWeek > lastResetWeek
    }

    /**
     * Check if monthly reset should be performed.
     */
    private fun shouldResetMonthly(entryId: String): Boolean {
        val lastReset = resetTimestamps[entryId]
        return lastReset == null ||
            lastReset.monthValue < LocalDateTime.now().monthValue ||
            lastReset.year < LocalDateTime.now().year
    }

    /**
     * Perform the actual reset operation.
     */
    private fun performReset(entryId: String) {
        tracker.reset(entryId)
        resetTimestamps[entryId] = LocalDateTime.now()
        resetCounts[entryId] = (resetCounts[entryId] ?: 0) + 1
    }

    /**
     * Reset all tracking data including reset history.
     */
    fun resetAll() {
        tracker.resetAll()
        resetTimestamps.clear()
        resetCounts.clear()
    }
}
