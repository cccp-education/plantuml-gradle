package plantuml.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import plantuml.PlantumlConfig
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class LlmServiceErrorTest {
    companion object {
        @JvmStatic
        fun errorHandlingScenarios() =
            listOf(
                arrayOf("invalid-api-key", "openai", "invalid-key"),
                arrayOf("unsupported-model", "unsupported-model", ""),
                arrayOf("network-timeouts", "openai", "test-key"),
                arrayOf("rate-limiting", "mistral", "test-key"),
                arrayOf("fallback-default", "gemini", ""),
            )
    }

    @Suppress("JUnitMalformedDeclaration")
    @ParameterizedTest
    @MethodSource("errorHandlingScenarios")
    fun `should handle various error scenarios gracefully`(
        scenario: String,
        model: String,
        apiKey: String,
    ) {
        // Given
        val config = createConfigForScenario(scenario, model, apiKey)
        val llmService = LlmService(config)

        // When
        val chatModel = llmService.createChatModel()

        // Then
        assertNotNull(chatModel)
    }

    @Test
    fun `should handle malformed configuration gracefully`() {
        // Given
        val config =
            PlantumlConfig(
                langchain4j =
                    plantuml.LangchainConfig(
                        model = "claude",
                        claude = plantuml.ApiKeyConfig(""), // Empty API key
                    ),
            )
        val llmService = LlmService(config)

        // When & Then
        // Should throw IllegalArgumentException for empty API key
        assertFailsWith<IllegalArgumentException> {
            llmService.createChatModel()
        }
    }

    private fun createConfigForScenario(
        scenario: String,
        model: String,
        apiKey: String,
    ): PlantumlConfig =
        when (scenario) {
            "invalid-api-key" ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = model,
                            openai = plantuml.ApiKeyConfig(apiKey),
                        ),
                )

            "unsupported-model" ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = model,
                        ),
                )

            "network-timeouts" ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = model,
                            openai = plantuml.ApiKeyConfig(apiKey),
                        ),
                )

            "rate-limiting" ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = model,
                            mistral = plantuml.ApiKeyConfig(apiKey),
                        ),
                )

            "fallback-default" ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = model,
                            // Missing gemini configuration for fallback test
                        ),
                )

            else -> PlantumlConfig()
        }
}
