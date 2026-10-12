package plantuml.service

import dev.langchain4j.model.chat.ChatModel
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import plantuml.PlantumlConfig
import contracts.plantuml.SyntaxValidationResult
import kotlin.test.assertTrue

/**
 * Unit tests for the augmented-context injection in [DiagramProcessor]
 * (EPIC PLT-CONTEXT, CTX-04).
 *
 * Baby-step TDD (RED → GREEN): the processor must prepend the collected
 * augmented context to the prompt it sends to the LLM, while a blank context
 * leaves the prompt unchanged (offline fallback, D-3).
 */
class DiagramProcessorContextTest {

    private val logger: Logger = LoggerFactory.getLogger(DiagramProcessorContextTest::class.java)

    private fun processorWith(context: String): Pair<DiagramProcessor, ChatModel> {
        val mockPlantumlService = mock(PlantumlService::class.java)
        val mockChatModel = mock(ChatModel::class.java)
        `when`(mockChatModel.chat(any<String>()))
            .thenReturn("@startuml\nclass X\n@enduml")
        `when`(mockPlantumlService.validateSyntax(any<String>()))
            .thenReturn(SyntaxValidationResult.Valid)
        val processor = DiagramProcessor(
            mockPlantumlService,
            mockChatModel,
            PlantumlConfig(),
            null,
            context,
        )
        return processor to mockChatModel
    }

    private fun capturePrompt(chatModel: ChatModel): String {
        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(chatModel).chat(captor.capture())
        return captor.value
    }

    @Test
    fun `the augmented context is prepended to the llm prompt`() {
        val (processor, chatModel) = processorWith("EAGER-RULES-MARKER")

        processor.processPrompt("Create a class diagram", maxIterations = 0, logger = logger)

        val prompt = capturePrompt(chatModel)
        assertTrue(prompt.contains("EAGER-RULES-MARKER"), "expected injected context in the prompt")
        assertTrue(prompt.contains("Create a class diagram"), "expected the base description preserved")
    }

    @Test
    fun `a blank augmented context leaves the prompt untouched`() {
        val (processor, chatModel) = processorWith("")

        processor.processPrompt("Create a class diagram", maxIterations = 0, logger = logger)

        val prompt = capturePrompt(chatModel)
        assertTrue(
            !prompt.contains("Augmented context (governance EAGER + retrieved RAG/Graphify/Docs):"),
            "no context header must be injected when the context is blank",
        )
        assertTrue(prompt.contains("Create a class diagram"))
    }
}
