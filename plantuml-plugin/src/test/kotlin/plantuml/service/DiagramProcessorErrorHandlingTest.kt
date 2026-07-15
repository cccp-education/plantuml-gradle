package plantuml.service

import dev.langchain4j.model.chat.ChatModel
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import plantuml.PlantumlConfig
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests to cover error handling branches in DiagramProcessor
 * Target: do-while loop with real ChatModel (lines 220-251)
 * 
 * Covers uncovered branches:
 * - if (validationResult is SyntaxValidationResult.Invalid) — true branch
 * - correctionPrompt + chatModel.chat()
 * - while (iterations < maxIterations)
 */
class DiagramProcessorErrorHandlingTest {
    val logger: Logger = LoggerFactory.getLogger(DiagramProcessorErrorHandlingTest::class.java)

    @Test
    fun `should handle invalid response from ChatModel and retry with correction prompt`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        val mockChatModel = mock(ChatModel::class.java)
        val mockConfig = mock(PlantumlConfig::class.java)
        val mockLangchainConfig = mock(plantuml.LangchainConfig::class.java)

        // Setup: first response invalid, second response valid
        `when`(mockChatModel.chat(anyString()))
            .thenReturn("@startuml\nInvalid syntax")  // First response invalid
            .thenReturn("@startuml\nFixed\n@enduml")  // Second response valid

        // Setup validation: first invalid, then valid
        `when`(mockPlantumlService.validateSyntax("@startuml\nInvalid syntax"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Invalid("Syntax error", "stack"))
        `when`(mockPlantumlService.validateSyntax("@startuml\nFixed\n@enduml"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Valid)

        // Setup config
        `when`(mockConfig.langchain4j).thenReturn(mockLangchainConfig)
        `when`(mockLangchainConfig.validationPrompt).thenReturn("Validate this PlantUML")

        val processor = DiagramProcessor(mockPlantumlService, mockChatModel, mockConfig)
        val result = processor.processPrompt("Test prompt with error handling", logger = logger)

        // Verify: ChatModel was called 2 times (initial + correction)
        verify(mockChatModel, times(2)).chat(anyString())

        // Verify: validation was called 2 times
        verify(mockPlantumlService, times(2)).validateSyntax(anyString())

        assertNotNull(result)
        assertTrue(result.conversation.isNotEmpty())
    }

    @Test
    fun `should handle multiple correction attempts before success`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        val mockChatModel = mock(ChatModel::class.java)
        val mockConfig = mock(PlantumlConfig::class.java)
        val mockLangchainConfig = mock(plantuml.LangchainConfig::class.java)

        // Setup: 2 invalid responses, then valid
        `when`(mockChatModel.chat(anyString()))
            .thenReturn("@startuml\nInvalid 1")
            .thenReturn("@startuml\nInvalid 2")
            .thenReturn("@startuml\nValid\n@enduml")

        `when`(mockPlantumlService.validateSyntax("@startuml\nInvalid 1"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Invalid("Error 1", "stack1"))
        `when`(mockPlantumlService.validateSyntax("@startuml\nInvalid 2"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Invalid("Error 2", "stack2"))
        `when`(mockPlantumlService.validateSyntax("@startuml\nValid\n@enduml"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Valid)

        `when`(mockConfig.langchain4j).thenReturn(mockLangchainConfig)
        `when`(mockLangchainConfig.validationPrompt).thenReturn("Validate this")

        val processor = DiagramProcessor(mockPlantumlService, mockChatModel, mockConfig)
        val result = processor.processPrompt(
            prompt = "Test with multiple corrections",
            logger = logger
        )

        // Verify: ChatModel was called 3 times
        verify(mockChatModel, times(3)).chat(anyString())

        assertNotNull(result)
        assertTrue(result.conversation.size >= 3)
    }

    @Test
    fun `should return null when ChatModel always returns invalid responses`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        val mockChatModel = mock(ChatModel::class.java)
        val mockConfig = mock(PlantumlConfig::class.java)
        val mockLangchainConfig = mock(plantuml.LangchainConfig::class.java)

        // Setup: ChatModel always returns invalid responses
        `when`(mockChatModel.chat(anyString()))
            .thenReturn("@startuml\nAlways invalid")

        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(PlantumlService.SyntaxValidationResult.Invalid("Always error", "stack"))

        `when`(mockConfig.langchain4j).thenReturn(mockLangchainConfig)
        `when`(mockLangchainConfig.validationPrompt).thenReturn("Validate this")

        val processor = DiagramProcessor(mockPlantumlService, mockChatModel, mockConfig)
        val result = processor.processPrompt(
            "Test that always fails", maxIterations = 2,
            logger = logger
        )

        // Verify: ChatModel was called 3 times (1 initial + 2 retries)
        verify(mockChatModel, times(3)).chat(anyString())

        assertNull(result, "Should return null after maxIterations")
    }

    @Test
    fun `should build history context for correction prompt`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        val mockChatModel = mock(ChatModel::class.java)
        val mockConfig = mock(PlantumlConfig::class.java)
        val mockLangchainConfig = mock(plantuml.LangchainConfig::class.java)

        // Setup to force call to buildHistoryContext
        `when`(mockChatModel.chat(anyString()))
            .thenReturn("@startuml\nFirst attempt")
            .thenReturn("@startuml\nSecond attempt")
            .thenReturn("@startuml\nThird attempt\n@enduml")

        `when`(mockPlantumlService.validateSyntax("@startuml\nFirst attempt"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Invalid("Error 1", "stack1"))
        `when`(mockPlantumlService.validateSyntax("@startuml\nSecond attempt"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Invalid("Error 2", "stack2"))
        `when`(mockPlantumlService.validateSyntax("@startuml\nThird attempt\n@enduml"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Valid)

        `when`(mockConfig.langchain4j).thenReturn(mockLangchainConfig)
        `when`(mockLangchainConfig.validationPrompt).thenReturn("Validate this")

        val processor = DiagramProcessor(mockPlantumlService, mockChatModel, mockConfig)
        val result = processor.processPrompt(
            "Test with history context",
            logger = logger
        )

        // Verify: ChatModel was called 3 times, including correction prompts with history
        verify(mockChatModel, times(3)).chat(anyString())

        assertNotNull(result)
        assertTrue(result.conversation.size >= 3)
    }

    @Test
    fun `should archive attempt history after successful correction`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        val mockChatModel = mock(ChatModel::class.java)
        val mockConfig = mock(PlantumlConfig::class.java)
        val mockLangchainConfig = mock(plantuml.LangchainConfig::class.java)

        // Setup: initial failure then success
        `when`(mockChatModel.chat(anyString()))
            .thenReturn("@startuml\nInitial invalid")
            .thenReturn("@startuml\nCorrected\n@enduml")

        `when`(mockPlantumlService.validateSyntax("@startuml\nInitial invalid"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Invalid("Initial error", "stack"))
        `when`(mockPlantumlService.validateSyntax("@startuml\nCorrected\n@enduml"))
            .thenReturn(PlantumlService.SyntaxValidationResult.Valid)

        `when`(mockConfig.langchain4j).thenReturn(mockLangchainConfig)
        `when`(mockLangchainConfig.validationPrompt).thenReturn("Validate this")

        val processor = DiagramProcessor(mockPlantumlService, mockChatModel, mockConfig)
        val result = processor.processPrompt(
            prompt = "Test with archiving",
            logger = logger
        )

        assertNotNull(result)
        // History should be archived because there were multiple attempts
        assertTrue(result.conversation.size >= 2)
    }

    @Test
    fun `should parse LLM JSON validation response into ValidationFeedback`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        val mockChatModel = mock(ChatModel::class.java)
        val mockConfig = mock(PlantumlConfig::class.java)
        val mockLangchainConfig = mock(plantuml.LangchainConfig::class.java)

        val llmJsonResponse = """{"score": 9, "feedback": "Excellent diagram with clear architecture", "recommendations": ["Add legend", "Use consistent colors"]}"""

        `when`(mockConfig.langchain4j).thenReturn(mockLangchainConfig)
        `when`(mockLangchainConfig.validationPrompt).thenReturn("Validate this PlantUML")
        `when`(mockChatModel.chat(anyString())).thenReturn(llmJsonResponse)

        val processor = DiagramProcessor(mockPlantumlService, mockChatModel, mockConfig)

        val diagram = plantuml.PlantumlDiagram(
            conversation = listOf("Test"),
            plantuml = plantuml.PlantumlCode(
                code = "@startuml\nactor User\n@enduml",
                description = "Test"
            )
        )

        val result = processor.validateDiagram(diagram)

        assertEquals(9, result.score)
        assertEquals("Excellent diagram with clear architecture", result.feedback)
        assertEquals(2, result.recommendations.size)
        assertEquals("Add legend", result.recommendations[0])
        assertEquals("Use consistent colors", result.recommendations[1])
    }

    @Test
    fun `should fallback to default ValidationFeedback when LLM returns invalid JSON`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        val mockChatModel = mock(ChatModel::class.java)
        val mockConfig = mock(PlantumlConfig::class.java)
        val mockLangchainConfig = mock(plantuml.LangchainConfig::class.java)

        val garbageResponse = "This is not JSON at all"

        `when`(mockConfig.langchain4j).thenReturn(mockLangchainConfig)
        `when`(mockLangchainConfig.validationPrompt).thenReturn("Validate this PlantUML")
        `when`(mockChatModel.chat(anyString())).thenReturn(garbageResponse)

        val processor = DiagramProcessor(mockPlantumlService, mockChatModel, mockConfig)

        val diagram = plantuml.PlantumlDiagram(
            conversation = listOf("Test"),
            plantuml = plantuml.PlantumlCode(
                code = "@startuml\nactor User\n@enduml",
                description = "Test"
            )
        )

        val result = processor.validateDiagram(diagram)

        assertTrue(result.score in 1..10, "Fallback score should be in valid range 1-10")
        assertNotNull(result.feedback)
        assertTrue(result.feedback.isNotEmpty(), "Fallback feedback should not be empty")
    }

    @Test
    fun `should handle validationPrompt from config in validateDiagram`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        val mockChatModel = mock(ChatModel::class.java)
        val mockConfig = mock(PlantumlConfig::class.java)
        val mockLangchainConfig = mock(plantuml.LangchainConfig::class.java)

        `when`(mockConfig.langchain4j).thenReturn(mockLangchainConfig)
        `when`(mockLangchainConfig.validationPrompt).thenReturn("Custom validation prompt")

        val processor = DiagramProcessor(mockPlantumlService, mockChatModel, mockConfig)

        val diagram = plantuml.PlantumlDiagram(
            conversation = listOf("Test"),
            plantuml = plantuml.PlantumlCode(
                code = "@startuml\nactor User\n@enduml",
                description = "Test"
            )
        )

        val result = processor.validateDiagram(diagram)

        assertNotNull(result)
    }
}
