package plantuml.apikey

import org.junit.jupiter.api.Test
import plantuml.apikey.QuotaConfig
import plantuml.apikey.ResetPolicy
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [ApiKeyPool] saturation semantics.
 *
 * A pool is saturated when every entry has exceeded its quota threshold.
 * This is the building block for cross-provider fallback (EPIC 13 S162).
 */
class ApiKeyPoolSaturationTest {

    private fun entry(
        id: String,
        quota: QuotaConfig = QuotaConfig()
    ): ApiKeyEntry = ApiKeyEntry(
        id = id,
        email = "t@e.com",
        name = "Test $id",
        keyRef = "REF_$id",
        provider = Provider.GOOGLE,
        services = listOf(ServiceType.CHAT_COMPLETION),
        quota = quota
    )

    private fun saturatingQuota(): QuotaConfig = QuotaConfig(
        limitValue = 2,
        thresholdPercent = 50,
        resetPolicy = ResetPolicy.MANUAL
    )

    @Test
    fun `isPoolSaturated returns false when no entry has exceeded its quota`() {
        val pool = ApiKeyPool(
            entries = listOf(entry("k1"), entry("k2")),
            autoResetEnabled = false
        )

        assertFalse(pool.isPoolSaturated())
    }

    @Test
    fun `isPoolSaturated returns true when every entry has exceeded its quota`() {
        val pool = ApiKeyPool(
            entries = listOf(
                entry("k1", saturatingQuota()),
                entry("k2", saturatingQuota())
            ),
            autoResetEnabled = false
        )

        pool.getNextKey()
        pool.getNextKey()
        pool.getNextKey()
        pool.getNextKey()

        assertTrue(pool.isPoolSaturated())
    }

    @Test
    fun `isPoolSaturated returns false when at least one entry is still available`() {
        val pool = ApiKeyPool(
            entries = listOf(
                entry("k1", saturatingQuota()),
                entry("k2", QuotaConfig(limitValue = 100, thresholdPercent = 50))
            ),
            autoResetEnabled = false
        )

        pool.getNextKey()
        pool.getNextKey()

        assertFalse(pool.isPoolSaturated())
    }
}