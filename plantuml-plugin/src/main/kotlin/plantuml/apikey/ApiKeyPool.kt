package plantuml.apikey

import plantuml.PlantumlMessages

/**
 * Manages a pool of API keys with rotation support.
 *
 * @param entries List of API key entries in the pool
 * @param rotationStrategy Strategy for rotating keys (default: ROUND_ROBIN)
 * @param fallbackEnabled Enable fallback to next key on failure
 * @param autoResetEnabled Enable automatic reset when quota exceeded (default: true)
 * @param auditEnabled Enable audit logging (default: true)
 */
class ApiKeyPool(
    private val entries: List<ApiKeyEntry>,
    private val rotationStrategy: RotationStrategy = RotationStrategy.ROUND_ROBIN,
    private val fallbackEnabled: Boolean = true,
    autoResetEnabled: Boolean = true,
    auditEnabled: Boolean = true
) {
    private var currentIndex = 0
    private val tracker: QuotaTracker = QuotaTracker()
    private val resetManager: QuotaResetManager = QuotaResetManager(tracker, autoResetEnabled)
    private val auditLogger: QuotaAuditLogger = QuotaAuditLogger(auditEnabled)

    init {
        entries.forEach { entry ->
            tracker.getUsage(entry.id)
        }
    }

    /**
     * Get the next API key entry based on rotation strategy.
     *
     * @return The next API key entry to use
     * @throws IllegalStateException if pool is empty
     */
    fun getNextKey(): ApiKeyEntry {
        if (entries.isEmpty()) {
            throw IllegalStateException(PlantumlMessages.get("apikey.pool_empty"))
        }

        val selectedEntry = when (rotationStrategy) {
            RotationStrategy.ROUND_ROBIN -> getNextRoundRobin()
            RotationStrategy.LEAST_USED -> getNextLeastUsed()
            RotationStrategy.WEIGHTED, RotationStrategy.SMART -> getNextRoundRobin()
        }

        tracker.trackUsage(selectedEntry.id)
        val usageCount = tracker.getUsage(selectedEntry.id)
        auditLogger.logUsage(selectedEntry, usageCount)

        if (tracker.isQuotaExceeded(selectedEntry)) {
            auditLogger.logQuotaExceeded(selectedEntry, usageCount)
            if (resetManager.checkAndReset(selectedEntry)) {
                auditLogger.logReset(selectedEntry.id, resetManager.getResetCount(selectedEntry.id), false)
            }
        }

        return selectedEntry
    }

    /**
     * Get next key using round-robin strategy.
     */
    private fun getNextRoundRobin(): ApiKeyEntry {
        val entry = entries[currentIndex % entries.size]
        currentIndex = (currentIndex + 1) % entries.size
        return entry
    }

    /**
     * Get next key using least-used strategy.
     */
    private fun getNextLeastUsed(): ApiKeyEntry {
        return entries.minByOrNull { entry ->
            tracker.getUsage(entry.id)
        } ?: entries.first()
    }

    /**
     * Check if a key has exceeded its quota threshold.
     *
     * @param entry The API key entry to check
     * @return true if quota threshold is exceeded
     */
    fun isQuotaExceeded(entry: ApiKeyEntry): Boolean {
        return tracker.isQuotaExceeded(entry)
    }

    /**
     * Get all keys in the pool.
     */
    fun getAllKeys(): List<ApiKeyEntry> = entries

    /**
     * Get the number of keys in the pool.
     */
    fun size(): Int = entries.size

    /**
     * Check if fallback is enabled.
     */
    fun isFallbackEnabled(): Boolean = fallbackEnabled

    /**
     * Reset usage counts for all keys.
     */
    fun resetUsageCounts() {
        tracker.resetAll()
    }

    /**
     * Get usage count for a specific key.
     */
    fun getUsageCount(entryId: String): Long {
        return tracker.getUsage(entryId)
    }

    /**
     * Get the quota tracker instance.
     */
    fun getTracker(): QuotaTracker = tracker

    /**
     * Get the reset manager instance.
     */
    fun getResetManager(): QuotaResetManager = resetManager

    /**
     * Get the audit logger instance.
     */
    fun getAuditLogger(): QuotaAuditLogger = auditLogger

    /**
     * Get usage percentage for a specific key.
     */
    fun getUsagePercentage(entry: ApiKeyEntry): Double {
        return tracker.getUsagePercentage(entry)
    }

    /**
     * Perform manual reset for a specific key.
     */
    fun manualReset(entryId: String) {
        resetManager.manualReset(entryId)
        auditLogger.logReset(entryId, resetManager.getResetCount(entryId), true)
    }

    /**
     * Get audit logs.
     */
    fun getAuditLogs(): List<AuditLogEntry> = auditLogger.getLogs()
}
