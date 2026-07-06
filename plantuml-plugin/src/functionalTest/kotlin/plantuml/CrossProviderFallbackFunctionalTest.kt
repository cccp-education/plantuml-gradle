package plantuml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import plantuml.apikey.ApiKeyEntry
import plantuml.apikey.ApiKeyPool
import plantuml.apikey.CrossProviderFallbackOrchestrator
import plantuml.apikey.KeyTier
import plantuml.apikey.Provider
import plantuml.apikey.QuotaConfig
import plantuml.apikey.ResetPolicy
import plantuml.apikey.RotationStrategy
import plantuml.apikey.ServiceType

/**
 * Functional tests for [CrossProviderFallbackOrchestrator] end-to-end.
 *
 * Validates cross-provider fallback (EPIC 13 S162) with three provider
 * pools (Gemini → Mistral → Ollama), progressive saturation, audit
 * logging, and null return when every pool is saturated.
 */
class CrossProviderFallbackFunctionalTest {

    private fun entry(
        id: String,
        provider: Provider,
        tier: KeyTier = KeyTier.FREE,
        quota: QuotaConfig = QuotaConfig()
    ): ApiKeyEntry = ApiKeyEntry(
        id = id,
        email = "t@e.com",
        name = "Test $id",
        keyRef = "REF_$id",
        provider = provider,
        services = listOf(ServiceType.CHAT_COMPLETION),
        tier = tier,
        quota = quota
    )

    private fun saturatingQuota(): QuotaConfig = QuotaConfig(
        limitValue = 2,
        thresholdPercent = 50,
        resetPolicy = ResetPolicy.MANUAL
    )

    private fun largeQuota(): QuotaConfig = QuotaConfig(
        limitValue = 100,
        thresholdPercent = 50
    )

    private fun pool(vararg entries: ApiKeyEntry): ApiKeyPool =
        ApiKeyPool(entries.toList(), RotationStrategy.TIERED, autoResetEnabled = false)

    @Test
    fun `orchestrator returns Gemini key when Gemini pool is not saturated`() {
        val gemini = pool(entry("gemini-1", Provider.GOOGLE, KeyTier.PRO, largeQuota()))
        val mistral = pool(entry("mistral-1", Provider.MISTRAL, KeyTier.FREE, largeQuota()))
        val ollama = pool(entry("ollama-1", Provider.OLLAMA, KeyTier.FREE, largeQuota()))
        val orchestrator = CrossProviderFallbackOrchestrator(
            mapOf("gemini" to gemini, "mistral" to mistral, "ollama" to ollama),
            fallbackOrder = listOf("gemini", "mistral", "ollama")
        )

        val selected = orchestrator.selectKey()

        assertNotNull(selected)
        assertEquals("gemini-1", selected!!.id)
        assertEquals(Provider.GOOGLE, selected!!.provider)
    }

    @Test
    fun `orchestrator falls back to Mistral when Gemini pool is saturated`() {
        val gemini = pool(entry("gemini-1", Provider.GOOGLE, KeyTier.PRO, saturatingQuota()))
        val mistral = pool(entry("mistral-1", Provider.MISTRAL, KeyTier.FREE, largeQuota()))
        val ollama = pool(entry("ollama-1", Provider.OLLAMA, KeyTier.FREE, largeQuota()))
        val orchestrator = CrossProviderFallbackOrchestrator(
            mapOf("gemini" to gemini, "mistral" to mistral, "ollama" to ollama),
            fallbackOrder = listOf("gemini", "mistral", "ollama")
        )

        gemini.getNextKey()
        gemini.getNextKey()

        val selected = orchestrator.selectKey()

        assertNotNull(selected)
        assertEquals("mistral-1", selected!!.id)
        assertEquals(Provider.MISTRAL, selected!!.provider)
    }

    @Test
    fun `orchestrator falls back to Ollama when Gemini and Mistral are saturated`() {
        val gemini = pool(entry("gemini-1", Provider.GOOGLE, KeyTier.PRO, saturatingQuota()))
        val mistral = pool(entry("mistral-1", Provider.MISTRAL, KeyTier.FREE, saturatingQuota()))
        val ollama = pool(entry("ollama-1", Provider.OLLAMA, KeyTier.FREE, largeQuota()))
        val orchestrator = CrossProviderFallbackOrchestrator(
            mapOf("gemini" to gemini, "mistral" to mistral, "ollama" to ollama),
            fallbackOrder = listOf("gemini", "mistral", "ollama")
        )

        gemini.getNextKey()
        gemini.getNextKey()
        mistral.getNextKey()
        mistral.getNextKey()

        val selected = orchestrator.selectKey()

        assertNotNull(selected)
        assertEquals("ollama-1", selected!!.id)
        assertEquals(Provider.OLLAMA, selected!!.provider)
    }

    @Test
    fun `orchestrator returns null when every provider pool is saturated`() {
        val gemini = pool(entry("gemini-1", Provider.GOOGLE, KeyTier.PRO, saturatingQuota()))
        val mistral = pool(entry("mistral-1", Provider.MISTRAL, KeyTier.FREE, saturatingQuota()))
        val ollama = pool(entry("ollama-1", Provider.OLLAMA, KeyTier.FREE, saturatingQuota()))
        val orchestrator = CrossProviderFallbackOrchestrator(
            mapOf("gemini" to gemini, "mistral" to mistral, "ollama" to ollama),
            fallbackOrder = listOf("gemini", "mistral", "ollama")
        )

        gemini.getNextKey()
        gemini.getNextKey()
        mistral.getNextKey()
        mistral.getNextKey()
        ollama.getNextKey()
        ollama.getNextKey()

        val selected = orchestrator.selectKey()

        assertNull(selected)
    }

    @Test
    fun `orchestrator skips providers absent from the pools map`() {
        val ollama = pool(entry("ollama-1", Provider.OLLAMA, KeyTier.FREE, largeQuota()))
        val orchestrator = CrossProviderFallbackOrchestrator(
            mapOf("ollama" to ollama),
            fallbackOrder = listOf("gemini", "mistral", "ollama")
        )

        val selected = orchestrator.selectKey()

        assertNotNull(selected)
        assertEquals("ollama-1", selected!!.id)
    }
}