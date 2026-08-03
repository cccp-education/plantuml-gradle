package plantuml.service

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.mockito.Mockito.*
import org.slf4j.Logger
import plantuml.validation.SyntaxValidationResult
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests to cover retry branches of DiagramProcessor
 * Target: do-while loops with multiple iterations (lines 59-82, 115-150)
 */
class DiagramProcessorRetryTest {
    private val logger: Logger = LoggerFactory.getLogger(DiagramProcessorRetryTest::class.java)

    @Test
    fun `processPrompt should handle multiple retry iterations before success`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(SyntaxValidationResult.Invalid("Error 1", "stack1"))
            .thenReturn(SyntaxValidationResult.Invalid("Error 2", "stack2"))
            .thenReturn(SyntaxValidationResult.Valid)

        val processor = DiagramProcessor(mockPlantumlService, null, null)
        val result = processor.processPrompt("Test prompt with retries", logger = logger)

        assertNotNull(result)
        assertTrue(result.conversation.size >= 3, "Should have at least 3 attempts")
    }

    @Test
    fun `processPrompt should return null after max iterations exceeded`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(SyntaxValidationResult.Invalid("Always invalid", "stack"))

        val processor = DiagramProcessor(mockPlantumlService, null, null)
        val result = processor.processPrompt("Test prompt that always fails", maxIterations = 3, logger = logger)

        assertNull(result, "Should return null after 3 iterations")
    }

    @Test
    fun `processPrompt should succeed on first try without retries`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(SyntaxValidationResult.Valid)

        val processor = DiagramProcessor(mockPlantumlService, null, null)
        val result = processor.processPrompt("Test prompt with immediate success", logger = logger)

        assertNotNull(result)
        assertTrue(result.conversation.isNotEmpty(), "Should have at least 1 attempt")
    }

    @Test
    fun `processPrompt should include error messages in attempt history on failure`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(SyntaxValidationResult.Invalid("Error: Missing @enduml", "stack"))
            .thenReturn(SyntaxValidationResult.Valid)

        val processor = DiagramProcessor(mockPlantumlService, null, null)
        val result = processor.processPrompt("Test with error tracking", logger = logger)

        assertNotNull(result)
        assertTrue(result.conversation.size >= 2, "Should have at least 2 attempts")
    }

    @Test
    fun `processPrompt should archive attempt history when multiple iterations`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(SyntaxValidationResult.Invalid("First error", "stack1"))
            .thenReturn(SyntaxValidationResult.Valid)

        val processor = DiagramProcessor(mockPlantumlService, null, null)
        val result = processor.processPrompt("Test with multiple attempts", logger = logger)

        assertNotNull(result)
        assertTrue(result.conversation.size >= 2, "Should have at least 2 attempts")
    }

    @Test
    fun `processPrompt should handle fixCommonPlantUmlIssues in retry loop`() {
        val mockPlantumlService = mock(PlantumlService::class.java)
        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(SyntaxValidationResult.Invalid("Missing tags", "stack"))
            .thenReturn(SyntaxValidationResult.Valid)

        val processor = DiagramProcessor(mockPlantumlService, null, null)
        val result = processor.processPrompt(
            "Test prompt",
            logger = logger
        )

        assertNotNull(result)
        assertTrue(result.plantuml.code.contains("@startuml"))
        assertTrue(result.plantuml.code.contains("@enduml"))
    }
}
