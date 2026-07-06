package plantuml.apikey

import java.time.LocalDateTime

/**
 * Represents a single API key entry in the rotation pool.
 *
 * @property id Unique identifier for this key entry
 * @property email Email associated with the API key account
 * @property name Friendly name for this key (e.g., "Google Account #1")
 * @property keyRef GitHub Secrets reference (e.g., "GOOGLE_API_KEY_1")
 * @property provider The API provider (GOOGLE, HUGGINGFACE, etc.)
 * @property services List of services this key can access
 * @property tier Priority tier for tiered rotation (default: FREE)
 * @property expirationDate Optional expiration date for the key
 * @property quota Quota configuration for this key
 * @property metadata Additional metadata (creation date, notes, etc.)
 */
data class ApiKeyEntry(
    val id: String,
    val email: String,
    val name: String,
    val keyRef: String,
    val provider: Provider,
    val services: List<ServiceType>,
    val tier: KeyTier = KeyTier.FREE,
    val weight: Int = 1,
    val expirationDate: LocalDateTime? = null,
    val quota: QuotaConfig = QuotaConfig(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Quota configuration for an API key.
 *
 * Tracks usage limits and thresholds for rotation triggers.
 *
 * @property limitType Type of quota limit (REQUESTS, TOKENS, DAILY, etc.)
 * @property limitValue Maximum allowed value for the quota period
 * @property consumedValue Current consumed value in the period
 * @property thresholdPercent Percentage at which rotation is triggered (default: 80)
 * @property periodStart Start of the current quota period
 * @property periodEnd End of the current quota period
 * @property resetPolicy How the quota resets (DAILY, WEEKLY, MONTHLY, NEVER, MANUAL)
 * @property lastManualSync Last manual synchronization timestamp
 */
data class QuotaConfig(
    val limitType: QuotaType = QuotaType.REQUESTS,
    val limitValue: Long = 1000,
    val consumedValue: Long = 0,
    val thresholdPercent: Int = 80,
    val periodStart: LocalDateTime? = null,
    val periodEnd: LocalDateTime? = null,
    val resetPolicy: ResetPolicy = ResetPolicy.DAILY,
    val lastManualSync: LocalDateTime? = null
)

/**
 * Top-level configuration for the API key pool.
 *
 * @property version Configuration version for migration support
 * @property poolName Friendly name for this pool configuration
 * @property rotationStrategy Strategy for rotating keys (ROUND_ROBIN, WEIGHTED, etc.)
 * @property fallbackEnabled Enable fallback to next key on failure
 * @property auditEnabled Enable audit logging for key usage
 * @property providers Map of providers to their key lists
 */
data class ApiKeyPoolConfig(
    val version: String = "1.0",
    val poolName: String = "default",
    val rotationStrategy: RotationStrategy = RotationStrategy.ROUND_ROBIN,
    val fallbackEnabled: Boolean = true,
    val auditEnabled: Boolean = true,
    val providers: Map<Provider, List<ApiKeyEntry>> = emptyMap()
)

/**
 * Type of quota limit.
 */
enum class QuotaType {
    REQUESTS,
    TOKENS,
    DAILY,
    HOURLY,
    MINUTE,
    MONTHLY,
    WEEKLY,
    CUSTOM
}

/**
 * Policy for resetting quota counters.
 */
enum class ResetPolicy {
    DAILY,
    WEEKLY,
    MONTHLY,
    NEVER,
    MANUAL
}
