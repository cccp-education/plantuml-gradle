package plantuml.apikey

import org.junit.jupiter.api.Test
import plantuml.PlantumlMessages
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class ApiKeyPoolTest {

    private fun createTestEntry(
        id: String = "test-key",
        email: String = "test@example.com",
        name: String = "Test Key",
        quota: QuotaConfig = QuotaConfig()
    ): ApiKeyEntry {
        return ApiKeyEntry(
            id = id,
            email = email,
            name = name,
            keyRef = "TEST_KEY_REF",
            provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            quota = quota
        )
    }

    @Test
    fun `should create pool with entries`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2")
        )
        val pool = ApiKeyPool(entries)

        assertEquals(2, pool.size())
        assertEquals(entries, pool.getAllKeys())
    }

    @Test
    fun `should throw exception when pool is empty`() {
        val pool = ApiKeyPool(emptyList())

        val ex = assertFailsWith<IllegalStateException> {
            pool.getNextKey()
        }
        assertEquals(PlantumlMessages.get("apikey.pool_empty"), ex.message)
    }

    @Test
    fun `should rotate keys in round robin fashion`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2"),
            createTestEntry("key3")
        )
        val pool = ApiKeyPool(entries, RotationStrategy.ROUND_ROBIN)

        val first = pool.getNextKey()
        val second = pool.getNextKey()
        val third = pool.getNextKey()
        val fourth = pool.getNextKey()

        assertEquals("key1", first.id)
        assertEquals("key2", second.id)
        assertEquals("key3", third.id)
        assertEquals("key1", fourth.id)
    }

    @Test
    fun `should return least used key with LEAST_USED strategy`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2"),
            createTestEntry("key3")
        )
        val pool = ApiKeyPool(entries, RotationStrategy.LEAST_USED)

        val first = pool.getNextKey()
        val second = pool.getNextKey()

        assertEquals("key1", first.id)
        assertEquals("key2", second.id)
    }

    @Test
    fun `should track usage counts`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2")
        )
        val pool = ApiKeyPool(entries)

        pool.getNextKey()
        pool.getNextKey()
        pool.getNextKey()

        assertEquals(2, pool.getUsageCount("key1"))
        assertEquals(1, pool.getUsageCount("key2"))
    }

    @Test
    fun `should reset usage counts`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2")
        )
        val pool = ApiKeyPool(entries)

        pool.getNextKey()
        pool.getNextKey()
        pool.resetUsageCounts()

        assertEquals(0, pool.getUsageCount("key1"))
        assertEquals(0, pool.getUsageCount("key2"))
    }

    @Test
    fun `should detect quota exceeded`() {
        val quota = QuotaConfig(
            limitValue = 100,
            thresholdPercent = 80
        )
        val entries = listOf(
            createTestEntry("key1", quota = quota)
        )
        val pool = ApiKeyPool(entries, autoResetEnabled = false)

        assertFalse(pool.isQuotaExceeded(entries[0]))

        for (i in 1..80) {
            pool.getNextKey()
        }

        assertTrue(pool.isQuotaExceeded(entries[0]))
        assertEquals(80, pool.getUsageCount("key1"))
    }

    @Test
    fun `should respect custom threshold percent`() {
        val quota = QuotaConfig(
            limitValue = 100,
            thresholdPercent = 50
        )
        val entries = listOf(
            createTestEntry("key1", quota = quota)
        )
        val pool = ApiKeyPool(entries, autoResetEnabled = false)

        for (i in 1..49) {
            pool.getNextKey()
        }
        assertFalse(pool.isQuotaExceeded(entries[0]))

        pool.getNextKey()
        assertTrue(pool.isQuotaExceeded(entries[0]))
        assertEquals(50, pool.getUsageCount("key1"))
    }

    @Test
    fun `should have fallback enabled by default`() {
        val pool = ApiKeyPool(listOf(createTestEntry()))
        assertTrue(pool.isFallbackEnabled())
    }

    @Test
    fun `should respect fallback disabled setting`() {
        val pool = ApiKeyPool(
            entries = listOf(createTestEntry()),
            fallbackEnabled = false
        )
        assertFalse(pool.isFallbackEnabled())
    }

    @Test
    fun `should perform automatic reset when quota exceeded`() {
        val quota = QuotaConfig(
            limitValue = 100,
            thresholdPercent = 80,
            resetPolicy = ResetPolicy.DAILY
        )
        val entries = listOf(
            createTestEntry("key1", quota = quota)
        )
        val pool = ApiKeyPool(entries, autoResetEnabled = true)

        for (i in 1..80) {
            pool.getNextKey()
        }

        assertEquals(0, pool.getUsageCount("key1"))
        assertEquals(1, pool.getResetManager().getResetCount("key1"))
    }

    @Test
    fun `should log audit events when using keys`() {
        val entries = listOf(createTestEntry("key1"))
        val pool = ApiKeyPool(entries, auditEnabled = true)

        pool.getNextKey()
        pool.getNextKey()

        val logs = pool.getAuditLogs()
        assertEquals(2, logs.size)
        assertEquals(AuditEventType.USAGE, logs[0].eventType)
        assertEquals(AuditEventType.USAGE, logs[1].eventType)
    }

    @Test
    fun `should log quota exceeded and reset events`() {
        val quota = QuotaConfig(
            limitValue = 100,
            thresholdPercent = 80,
            resetPolicy = ResetPolicy.DAILY
        )
        val entries = listOf(
            createTestEntry("key1", quota = quota)
        )
        val pool = ApiKeyPool(entries, autoResetEnabled = true, auditEnabled = true)

        for (i in 1..80) {
            pool.getNextKey()
        }

        val logs = pool.getAuditLogs()
        assertTrue(logs.size >= 2)
        assertTrue(logs.any { it.eventType == AuditEventType.QUOTA_EXCEEDED })
        assertTrue(logs.any { it.eventType == AuditEventType.AUTO_RESET })
    }

    @Test
    fun `should select ENTERPRISE key first with TIERED strategy`() {
        val entries = listOf(
            createTestEntry("free1").copy(tier = KeyTier.FREE),
            createTestEntry("ent1").copy(tier = KeyTier.ENTERPRISE),
            createTestEntry("pro1").copy(tier = KeyTier.PRO)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.TIERED)

        val selected = pool.getNextKey()

        assertEquals("ent1", selected.id)
    }

    @Test
    fun `should descend tiers with TIERED strategy when higher tier quota saturated`() {
        val entQuota = QuotaConfig(
            limitValue = 4,
            thresholdPercent = 50,
            resetPolicy = ResetPolicy.MANUAL
        )
        val proQuota = QuotaConfig(
            limitValue = 4,
            thresholdPercent = 50,
            resetPolicy = ResetPolicy.MANUAL
        )
        val entries = listOf(
            createTestEntry("ent1", quota = entQuota).copy(tier = KeyTier.ENTERPRISE),
            createTestEntry("pro1", quota = proQuota).copy(tier = KeyTier.PRO)
        )
        val pool = ApiKeyPool(entries, RotationStrategy.TIERED, autoResetEnabled = false)

        val first = pool.getNextKey()
        val second = pool.getNextKey()
        val third = pool.getNextKey()

        assertEquals("ent1", first.id)
        assertEquals("ent1", second.id)
        assertEquals("pro1", third.id)
    }

    @Test
    fun `TIERED with fallback disabled should not descend to FREE when higher tiers saturated`() {
        val entQuota = QuotaConfig(limitValue = 2, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val freeQuota = QuotaConfig(limitValue = 100, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val entries = listOf(
            createTestEntry("ent1", quota = entQuota).copy(tier = KeyTier.ENTERPRISE),
            createTestEntry("free1", quota = freeQuota).copy(tier = KeyTier.FREE)
        )
        val pool = ApiKeyPool(
            entries = entries,
            rotationStrategy = RotationStrategy.TIERED,
            fallbackEnabled = false,
            autoResetEnabled = false
        )

        val first = pool.getNextKey()
        val second = pool.getNextKey()
        val third = pool.getNextKey()

        assertEquals("ent1", first.id)
        assertEquals("ent1", second.id)
        assertEquals("ent1", third.id)
    }

    @Test
    fun `TIERED with fallback enabled should descend to FREE when higher tiers saturated`() {
        val entQuota = QuotaConfig(limitValue = 2, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val freeQuota = QuotaConfig(limitValue = 100, thresholdPercent = 50, resetPolicy = ResetPolicy.MANUAL)
        val entries = listOf(
            createTestEntry("ent1", quota = entQuota).copy(tier = KeyTier.ENTERPRISE),
            createTestEntry("free1", quota = freeQuota).copy(tier = KeyTier.FREE)
        )
        val pool = ApiKeyPool(
            entries = entries,
            rotationStrategy = RotationStrategy.TIERED,
            fallbackEnabled = true,
            autoResetEnabled = false
        )

        val first = pool.getNextKey()
        val second = pool.getNextKey()

        assertEquals("ent1", first.id)
        assertEquals("free1", second.id)
    }
}
