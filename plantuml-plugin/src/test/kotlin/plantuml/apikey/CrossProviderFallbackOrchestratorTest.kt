package plantuml.apikey

import org.junit.jupiter.api.Test
import plantuml.apikey.QuotaConfig
import plantuml.apikey.ResetPolicy
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [CrossProviderFallbackOrchestrator].
 *
 * Validates the cross-provider fallback semantics (EPIC 13 S162):
 * when a provider pool is entirely saturated, the orchestrator
 * selects the next provider in the fallback chain and returns a
 * key from there.
 */
class CrossProviderFallbackOrchestratorTest {

    private fun entry(
        id: String,
        provider: Provider,
        quota: QuotaConfig = QuotaConfig()
    ): ApiKeyEntry = ApiKeyEntry(
        id = id,
        email = "t@e.com",
        name = "Test $id",
        keyRef = "REF_$id",
        provider = provider,
        services = listOf(ServiceType.CHAT_COMPLETION),
        quota = quota
    )

    private fun saturatingQuota(): QuotaConfig = QuotaConfig(
        limitValue = 2,
        thresholdPercent = 50,
        resetPolicy = ResetPolicy.MANUAL
    )

    private fun pool(vararg entries: ApiKeyEntry): ApiKeyPool =
        ApiKeyPool(entries.toList(), autoResetEnabled = false)

    @Test
    fun `selectKey returns a key from the first provider when it is not saturated`() {
        val geminiPool = pool(
            entry("gemini-1", Provider.GOOGLE, QuotaConfig(limitValue = 100, thresholdPercent = 50))
        )
        val orchestrator = CrossProviderFallbackOrchestrator(
            mapOf("gemini" to geminiPool),
            fallbackOrder = listOf("gemini", "mistral", "ollama")
        )

        val selected = orchestrator.selectKey()

        assertNotNull(selected)
        assertEquals("gemini-1", selected.id)
    }

    @Test
    fun `selectKey falls back to the next provider when the first pool is saturated`() {
        val geminiPool = pool(
            entry("gemini-1", Provider.GOOGLE, saturatingQuota())
        )
        val mistralPool = pool(
            entry("mistral-1", Provider.MISTRAL, QuotaConfig(limitValue = 100, thresholdPercent = 50))
        )
        val orchestrator = CrossProviderFallbackOrchestrator(
            mapOf("gemini" to geminiPool, "mistral" to mistralPool),
            fallbackOrder = listOf("gemini", "mistral", "ollama")
        )

        geminiPool.getNextKey()
        geminiPool.getNextKey()

        val selected = orchestrator.selectKey()

        assertNotNull(selected)
        assertEquals("mistral-1", selected.id)
    }

    @Test
    fun `selectKey falls back across two saturated providers to the third`() {
        val geminiPool = pool(entry("gemini-1", Provider.GOOGLE, saturatingQuota()))
        val mistralPool = pool(entry("mistral-1", Provider.MISTRAL, saturatingQuota()))
        val ollamaPool = pool(
            entry("ollama-1", Provider.OLLAMA, QuotaConfig(limitValue = 100, thresholdPercent = 50))
        )
        val orchestrator = CrossProviderFallbackOrchestrator(
            mapOf("gemini" to geminiPool, "mistral" to mistralPool, "ollama" to ollamaPool),
            fallbackOrder = listOf("gemini", "mistral", "ollama")
        )

        geminiPool.getNextKey()
        geminiPool.getNextKey()
        mistralPool.getNextKey()
        mistralPool.getNextKey()

        val selected = orchestrator.selectKey()

        assertNotNull(selected)
        assertEquals("ollama-1", selected.id)
    }

    @Test
    fun `selectKey returns null when every provider pool is saturated`() {
        val geminiPool = pool(entry("gemini-1", Provider.GOOGLE, saturatingQuota()))
        val mistralPool = pool(entry("mistral-1", Provider.MISTRAL, saturatingQuota()))
        val orchestrator = CrossProviderFallbackOrchestrator(
            mapOf("gemini" to geminiPool, "mistral" to mistralPool),
            fallbackOrder = listOf("gemini", "mistral")
        )

        geminiPool.getNextKey()
        geminiPool.getNextKey()
        mistralPool.getNextKey()
        mistralPool.getNextKey()

        val selected = orchestrator.selectKey()

        assertNull(selected)
    }

    @Test
    fun `selectKey returns null when fallback order contains no known provider`() {
        val orchestrator = CrossProviderFallbackOrchestrator(
            emptyMap(),
            fallbackOrder = listOf("gemini", "mistral")
        )

        val selected = orchestrator.selectKey()

        assertNull(selected)
    }
}