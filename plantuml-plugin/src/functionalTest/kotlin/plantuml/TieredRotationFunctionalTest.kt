package plantuml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import plantuml.apikey.ApiKeyEntry
import plantuml.apikey.ApiKeyPool
import plantuml.apikey.AuditEventType
import plantuml.apikey.KeyTier
import plantuml.apikey.Provider
import plantuml.apikey.QuotaConfig
import plantuml.apikey.ResetPolicy
import plantuml.apikey.RotationStrategy
import plantuml.apikey.ServiceType

/**
 * Functional tests for TieredRotationStrategy integration with ApiKeyPool.
 *
 * Validates tiered rotation end-to-end through the public ApiKeyPool API,
 * with audit logging, quota tracking, and tier descent semantics.
 */
class TieredRotationFunctionalTest {

    private fun entry(
        id: String,
        tier: KeyTier,
        weight: Int = 1,
        quota: QuotaConfig = QuotaConfig()
    ): ApiKeyEntry = ApiKeyEntry(
        id = id,
        email = "t@e.com",
        name = "Test $id",
        keyRef = "REF_$id",
        provider = Provider.GOOGLE,
        services = listOf(ServiceType.CHAT_COMPLETION),
        tier = tier,
        weight = weight,
        quota = quota
    )

    @Test
    fun `tiered pool selects ENTERPRISE key first`() {
        val entries = listOf(
            entry("free-1", KeyTier.FREE),
            entry("ent-1", KeyTier.ENTERPRISE),
            entry("pro-1", KeyTier.PRO)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.TIERED, auditEnabled = true)

        val selected = pool.getNextKey()

        assertEquals("ent-1", selected.id)
        assertEquals(KeyTier.ENTERPRISE, selected.tier)

        val logs = pool.getAuditLogs()
        assertEquals(1, logs.size)
        assertEquals(AuditEventType.USAGE, logs[0].eventType)
    }

    @Test
    fun `tiered pool descends to PRO then FREE as quotas saturate`() {
        val entQuota = QuotaConfig(limitValue = 4, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val proQuota = QuotaConfig(limitValue = 4, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val freeQuota = QuotaConfig(limitValue = 10, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val entries = listOf(
            entry("ent-1", KeyTier.ENTERPRISE, quota = entQuota),
            entry("pro-1", KeyTier.PRO, quota = proQuota),
            entry("free-1", KeyTier.FREE, quota = freeQuota)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.TIERED, autoResetEnabled = false)

        val first = pool.getNextKey()
        val second = pool.getNextKey()
        val third = pool.getNextKey()
        val fourth = pool.getNextKey()
        val fifth = pool.getNextKey()

        assertEquals("ent-1", first.id)
        assertEquals("ent-1", second.id)
        assertEquals("pro-1", third.id)
        assertEquals("pro-1", fourth.id)
        assertEquals("free-1", fifth.id)
    }

    @Test
    fun `tiered pool respects weight within same tier`() {
        val entries = listOf(
            entry("ent-low", KeyTier.ENTERPRISE, weight = 1),
            entry("ent-mid", KeyTier.ENTERPRISE, weight = 3),
            entry("ent-high", KeyTier.ENTERPRISE, weight = 10)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.TIERED, autoResetEnabled = false)

        val selected = pool.getNextKey()

        assertEquals("ent-high", selected.id)
        assertEquals(10, selected.weight)
    }

    @Test
    fun `tiered pool logs quota exceeded when descending tiers`() {
        val entQuota = QuotaConfig(limitValue = 2, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val proQuota = QuotaConfig(limitValue = 10, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val entries = listOf(
            entry("ent-1", KeyTier.ENTERPRISE, quota = entQuota),
            entry("pro-1", KeyTier.PRO, quota = proQuota)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.TIERED, autoResetEnabled = false, auditEnabled = true)

        pool.getNextKey()
        pool.getNextKey()

        val logs = pool.getAuditLogs()
        assertTrue(logs.any { it.eventType == AuditEventType.QUOTA_EXCEEDED })
    }

    @Test
    fun `tiered pool returns a key even when all entries are saturated`() {
        val quota = QuotaConfig(limitValue = 2, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val entries = listOf(
            entry("ent-1", KeyTier.ENTERPRISE, quota = quota)
        )
        val pool = ApiKeyPool(
            entries = entries,
            rotationStrategy = RotationStrategy.TIERED,
            autoResetEnabled = false
        )

        val first = pool.getNextKey()
        val second = pool.getNextKey()

        assertEquals("ent-1", first.id)
        assertEquals("ent-1", second.id)
    }
}