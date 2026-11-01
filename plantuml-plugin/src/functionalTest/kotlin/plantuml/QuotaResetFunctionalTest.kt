package plantuml

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import plantuml.apikey.ApiKeyEntry
import plantuml.apikey.ApiKeyPool
import plantuml.apikey.AuditEventType
import plantuml.apikey.Provider
import plantuml.apikey.QuotaConfig
import plantuml.apikey.ResetPolicy
import plantuml.apikey.RotationStrategy
import plantuml.apikey.ServiceType

/**
 * Functional tests for Quota Tracker and Automatic Reset.
 */
class QuotaResetFunctionalTest {
    @Test
    fun `test automatic reset when quota exceeded with WireMock`() {
        val quotaConfig =
            QuotaConfig(
                limitValue = 10,
                thresholdPercent = 50,
                resetPolicy = ResetPolicy.DAILY,
            )

        val entries =
            listOf(
                ApiKeyEntry(
                    id = "test-key-1",
                    email = "test@example.com",
                    name = "Test Key 1",
                    keyRef = "TEST_KEY_1",
                    provider = Provider.GOOGLE,
                    services = listOf(ServiceType.TEXT_GENERATION),
                    quota = quotaConfig,
                ),
            )

        val pool = ApiKeyPool(entries, autoResetEnabled = true, auditEnabled = true)

        repeat(5) {
            pool.getNextKey()
        }

        val usageCount = pool.getUsageCount("test-key-1")
        Assertions.assertEquals(0, usageCount, "Usage should be reset after quota exceeded")

        val resetCount = pool.getResetManager().getResetCount("test-key-1")
        Assertions.assertEquals(1, resetCount, "Should have reset once")

        val auditLogs = pool.getAuditLogs()
        Assertions.assertTrue(
            auditLogs.any { it.eventType == AuditEventType.QUOTA_EXCEEDED },
        )
        Assertions.assertTrue(
            auditLogs.any { it.eventType == AuditEventType.AUTO_RESET },
        )
    }

    @Test
    fun `test key rotation with quota management`() {
        val quotaConfig =
            QuotaConfig(
                limitValue = 3,
                thresholdPercent = 100,
                resetPolicy = ResetPolicy.NEVER,
            )

        val entries =
            listOf(
                ApiKeyEntry(
                    id = "key-1",
                    email = "key1@example.com",
                    name = "Key 1",
                    keyRef = "KEY_1",
                    provider = Provider.MISTRAL,
                    services = listOf(ServiceType.TEXT_GENERATION),
                    quota = quotaConfig,
                ),
                ApiKeyEntry(
                    id = "key-2",
                    email = "key2@example.com",
                    name = "Key 2",
                    keyRef = "KEY_2",
                    provider = Provider.MISTRAL,
                    services = listOf(ServiceType.TEXT_GENERATION),
                    quota = quotaConfig,
                ),
            )

        val pool = ApiKeyPool(entries, RotationStrategy.ROUND_ROBIN, autoResetEnabled = false)

        val firstKey = pool.getNextKey()
        Assertions.assertEquals("key-1", firstKey.id)

        val secondKey = pool.getNextKey()
        Assertions.assertEquals("key-2", secondKey.id)

        val thirdKey = pool.getNextKey()
        Assertions.assertEquals("key-1", thirdKey.id)

        Assertions.assertEquals(2, pool.getUsageCount("key-1"))
        Assertions.assertEquals(1, pool.getUsageCount("key-2"))
    }

    @Test
    fun `test audit logger tracks all operations`() {
        val entries =
            listOf(
                ApiKeyEntry(
                    id = "audit-key",
                    email = "audit@example.com",
                    name = "Audit Key",
                    keyRef = "AUDIT_KEY",
                    provider = Provider.GROQ,
                    services = listOf(ServiceType.TEXT_GENERATION),
                    quota = QuotaConfig(limitValue = 3, thresholdPercent = 50),
                ),
            )

        val pool = ApiKeyPool(entries, auditEnabled = true, autoResetEnabled = true)

        repeat(3) {
            pool.getNextKey()
        }

        val logs = pool.getAuditLogs()
        Assertions.assertTrue(logs.size >= 3)

        pool.manualReset("audit-key")
        val allLogs = pool.getAuditLogs()
        Assertions.assertTrue(allLogs.size >= 4)
        Assertions.assertEquals(
            AuditEventType.MANUAL_RESET,
            allLogs.last().eventType,
        )
    }
}
