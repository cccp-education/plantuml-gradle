package plantuml.service

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import plantuml.PlantumlConfig
import kotlin.test.assertNotNull

class LlmServiceTest {
    enum class LangchainModel {
        OLLAMA,
        OPENAI,
        GEMINI,
        MISTRAL,
        CLAUDE,
        HUGGINGFACE,
        GROQ,
    }

    private fun createConfigForModel(model: LangchainModel): PlantumlConfig =
        when (model) {
            LangchainModel.OLLAMA -> PlantumlConfig()
            LangchainModel.OPENAI ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = "openai",
                            openai = plantuml.ApiKeyConfig("test-key"),
                        ),
                )

            LangchainModel.GEMINI ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = "gemini",
                            gemini = plantuml.ApiKeyConfig("test-key"),
                        ),
                )

            LangchainModel.MISTRAL ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = "mistral",
                            mistral = plantuml.ApiKeyConfig("test-key"),
                        ),
                )

            LangchainModel.CLAUDE ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = "claude",
                            claude = plantuml.ApiKeyConfig("test-key"),
                        ),
                )

            LangchainModel.HUGGINGFACE ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = "huggingface",
                            huggingface = plantuml.ApiKeyConfig("test-key"),
                        ),
                )

            LangchainModel.GROQ ->
                PlantumlConfig(
                    langchain4j =
                        plantuml.LangchainConfig(
                            model = "groq",
                            groq = plantuml.ApiKeyConfig("test-key"),
                        ),
                )
        }

    @ParameterizedTest
    @EnumSource(LangchainModel::class)
    fun `should create chat model for all supported providers`(model: LangchainModel) {
        // Given
        val config = createConfigForModel(model)
        val llmService = LlmService(config)

        // When
        val chatModel = llmService.createChatModel()

        // Then
        assertNotNull(chatModel)
    }
}
