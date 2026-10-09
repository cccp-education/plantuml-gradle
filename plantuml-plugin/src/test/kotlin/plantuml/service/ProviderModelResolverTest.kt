package plantuml.service

import plantuml.ApiKeyConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * PLT-CR3-1 — the HuggingFace / Groq model names must be configurable, like the
 * other providers (`langchain4j.huggingface.modelName` / `langchain4j.groq.modelName`).
 *
 * Before this fix, `LlmService.createHuggingFaceModel` hardcoded `gpt2` and
 * `createGroqModel` hardcoded `llama3-8b-8192`, ignoring the config fields that
 * already existed. The resolution is now a pure function (Red→Green, DDD).
 */
class ProviderModelResolverTest {

    @Test
    fun `huggingFace uses the configured model name when present`() {
        val resolved = ProviderModelResolver.huggingFace(ApiKeyConfig(modelName = "HuggingFaceH4/zephyr-7b-beta"))
        assertEquals("HuggingFaceH4/zephyr-7b-beta", resolved)
    }

    @Test
    fun `huggingFace falls back to the default when the config is blank`() {
        // Backward compatibility: an unconfigured model keeps the previous behaviour.
        val resolved = ProviderModelResolver.huggingFace(ApiKeyConfig(modelName = ""))
        assertEquals("gpt2", resolved)
    }

    @Test
    fun `groq uses the configured model name when present`() {
        val resolved = ProviderModelResolver.groq(ApiKeyConfig(modelName = "llama-3.3-70b-versatile"))
        assertEquals("llama-3.3-70b-versatile", resolved)
    }

    @Test
    fun `groq falls back to the default when the config is blank`() {
        val resolved = ProviderModelResolver.groq(ApiKeyConfig(modelName = "   "))
        assertEquals("llama3-8b-8192", resolved)
    }
}
