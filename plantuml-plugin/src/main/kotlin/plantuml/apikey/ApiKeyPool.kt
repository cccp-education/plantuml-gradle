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
    private val tieredStrategy: TieredRotationStrategy = TieredRotationStrategy()

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
            RotationStrategy.TIERED -> getNextTiered()
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
     * Get next key using tiered strategy (Enterprise > Pro > Free, intra-tier by weight).
     *
     * When [fallbackEnabled] is true (default), entries whose quota is exceeded are
     * excluded so the strategy descends to lower tiers. When all entries are saturated,
     * the full list is used so a key is always returned.
     *
     * When [fallbackEnabled] is false, saturated entries are kept as candidates so the
     * strategy never descends to a lower tier (Enterprise/Pro are reused even when
     * saturated, FREE is never reached).
     */
    private fun getNextTiered(): ApiKeyEntry {
        val candidates = if (fallbackEnabled) {
            val available = entries.filterNot { tracker.isQuotaExceeded(it) }
            if (available.isEmpty()) entries else available
        } else {
            entries
        }
        return tieredStrategy.select(candidates)
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
     * Check whether every entry in the pool has exceeded its quota threshold.
     *
     * Used by cross-provider fallback (EPIC 13 S162) to decide whether to
     * skip this pool and try the next provider in the fallback chain.
     *
     * @return true when all entries are saturated, false otherwise
     */
    fun isPoolSaturated(): Boolean {
        if (entries.isEmpty()) return true
        return entries.all { tracker.isQuotaExceeded(it) }
    }

    /**
     * Get audit logs.
     */
    fun getAuditLogs(): List<AuditLogEntry> = auditLogger.getLogs()
}
