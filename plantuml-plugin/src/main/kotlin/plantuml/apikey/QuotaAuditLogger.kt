package plantuml.apikey

import java.time.LocalDateTime

/**
 * Audit logger for API key usage and quota events.
 *
 * Tracks and logs all quota-related events for monitoring and debugging.
 *
 * @param enabled Enable audit logging (default: true)
 */
class QuotaAuditLogger(
    private val enabled: Boolean = true,
) {
    private val logs = mutableListOf<AuditLogEntry>()

    /**
     * Log a usage event.
     *
     * @param entry The API key entry that was used
     * @param usageCount The current usage count after this event
     */
    fun logUsage(
        entry: ApiKeyEntry,
        usageCount: Long,
    ) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.USAGE,
                entryId = entry.id,
                provider = entry.provider,
                message = "Usage tracked: $usageCount requests",
                details =
                    mapOf(
                        "usageCount" to usageCount.toString(),
                        "limitValue" to entry.quota.limitValue.toString(),
                        "thresholdPercent" to entry.quota.thresholdPercent.toString(),
                    ),
            ),
        )
    }

    /**
     * Log a quota exceeded event.
     *
     * @param entry The API key entry that exceeded quota
     * @param usageCount The current usage count
     */
    fun logQuotaExceeded(
        entry: ApiKeyEntry,
        usageCount: Long,
    ) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.QUOTA_EXCEEDED,
                entryId = entry.id,
                provider = entry.provider,
                message = "Quota threshold exceeded: $usageCount / ${entry.quota.limitValue}",
                details =
                    mapOf(
                        "usageCount" to usageCount.toString(),
                        "limitValue" to entry.quota.limitValue.toString(),
                        "thresholdPercent" to entry.quota.thresholdPercent.toString(),
                    ),
            ),
        )
    }

    /**
     * Log a reset event.
     *
     * @param entryId The ID of the API key entry that was reset
     * @param resetCount The total number of resets for this key
     * @param isManual Whether the reset was manual or automatic
     */
    fun logReset(
        entryId: String,
        resetCount: Int,
        isManual: Boolean,
    ) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = if (isManual) AuditEventType.MANUAL_RESET else AuditEventType.AUTO_RESET,
                entryId = entryId,
                provider = Provider.UNKNOWN,
                message = "Quota reset performed (${if (isManual) "manual" else "automatic"})",
                details =
                    mapOf(
                        "resetCount" to resetCount.toString(),
                    ),
            ),
        )
    }

    /**
     * Log a rotation event.
     *
     * @param fromEntry The API key entry that was rotated from
     * @param toEntry The API key entry that was rotated to
     * @param reason The reason for rotation
     */
    fun logRotation(
        fromEntry: ApiKeyEntry,
        toEntry: ApiKeyEntry,
        reason: String,
    ) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.ROTATION,
                entryId = toEntry.id,
                provider = toEntry.provider,
                message = "Key rotation: ${fromEntry.id} -> ${toEntry.id}",
                details =
                    mapOf(
                        "fromEntryId" to fromEntry.id,
                        "toEntryId" to toEntry.id,
                        "reason" to reason,
                    ),
            ),
        )
    }

    /**
     * Get all audit logs.
     */
    fun getLogs(): List<AuditLogEntry> = logs.toList()

    /**
     * Get logs for a specific API key.
     *
     * @param entryId The ID of the API key entry
     * @return List of audit logs for the specified key
     */
    fun getLogsForEntry(entryId: String): List<AuditLogEntry> = logs.filter { it.entryId == entryId }

    /**
     * Get logs by event type.
     *
     * @param eventType The type of event to filter by
     * @return List of audit logs matching the event type
     */
    fun getLogsByType(eventType: AuditEventType): List<AuditLogEntry> = logs.filter { it.eventType == eventType }

    /**
     * Log an info event.
     *
     * @param provider The API provider
     * @param message Human-readable message describing the event
     */
    fun logInfo(
        provider: Provider,
        message: String,
    ) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.INFO,
                entryId = "system",
                provider = provider,
                message = message,
                details = emptyMap(),
            ),
        )
    }

    /**
     * Log an error event.
     *
     * @param provider The API provider
     * @param message Human-readable message describing the error
     * @param error Optional throwable with error details
     */
    fun logError(
        provider: Provider,
        message: String,
        error: Throwable? = null,
    ) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.ERROR,
                entryId = "system",
                provider = provider,
                message = message,
                details = mapOf("error" to (error?.message ?: "unknown")),
            ),
        )
    }

    /**
     * Clear all audit logs.
     */
    fun clear() {
        logs.clear()
    }

    /**
     * Get the total number of audit logs.
     */
    fun getLogCount(): Int = logs.size
}

/**
 * Represents a single audit log entry.
 *
 * @property timestamp When the event occurred
 * @property eventType Type of audit event
 * @property entryId ID of the API key entry involved
 * @property provider The API provider
 * @property message Human-readable message describing the event
 * @property details Additional details about the event
 */
data class AuditLogEntry(
    val timestamp: LocalDateTime,
    val eventType: AuditEventType,
    val entryId: String,
    val provider: Provider,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

/**
 * Types of audit events.
 */
enum class AuditEventType {
    USAGE,
    QUOTA_EXCEEDED,
    AUTO_RESET,
    MANUAL_RESET,
    ROTATION,
    ERROR,
    INFO,
}
