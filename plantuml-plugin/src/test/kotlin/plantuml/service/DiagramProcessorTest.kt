package plantuml.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import plantuml.PlantumlCode
import plantuml.PlantumlDiagram
import plantuml.ValidationFeedback
import plantuml.validation.SyntaxValidationResult
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagramProcessorTest {
    val logger: Logger = LoggerFactory.getLogger(DiagramProcessorTest::class.java)
    private lateinit var mockPlantumlService: PlantumlService
    private lateinit var diagramProcessor: DiagramProcessor

    @BeforeEach
    fun setUp() {
        mockPlantumlService = mock(PlantumlService::class.java)
        // For tests, we use the simulated implementation that does not require a ChatModel
        diagramProcessor = DiagramProcessor(mockPlantumlService, null, null)
    }

    @Test
    fun `should process prompt with mock llm response`() {
        // Given
        val prompt = "Create a user diagram"
        setupValidSyntaxMock()

        // When
        val result = diagramProcessor.processPrompt(prompt = prompt, logger = logger)

        // Then
        assertNotNull(result)
        // Check that the conversation contains at least one entry
        assertTrue(result.conversation.isNotEmpty())
        // Check that the PlantUML code contains the expected elements
        assertTrue(result.plantuml.code.contains("@startuml"))
        assertTrue(result.plantuml.code.contains("@enduml"))
        assertTrue(result.plantuml.description.contains("Auto-generated diagram based on prompt: $prompt"))
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `should handle syntax validation scenarios`(iterations: Int) {
        when (iterations) {
            1 -> logger.testMaxIterationsExceeded()
            2 -> logger.testIterateOnSyntaxErrors()
        }
    }

    private fun Logger.testIterateOnSyntaxErrors() {
        // Given
        val prompt = "Create a user diagram"
        setupInvalidThenValidSyntaxMock()

        // When
        val result = diagramProcessor.processPrompt(prompt, maxIterations = 2, this)

        // Then
        assertNotNull(result)
        // Check that the attempt history is included in the conversation
        assertTrue(result.conversation.size > 1)
        assertTrue(result.conversation.any { it.contains("->") })
    }

    private fun Logger.testMaxIterationsExceeded() {
        // Given
        val prompt = "Create a user diagram"
        setupInvalidSyntaxMock()

        // When
        val result = diagramProcessor.processPrompt(prompt, maxIterations = 1, this)

        // Then
        assertNull(result)
        // Check that validation calls were made
        verify(mockPlantumlService, times(1)).validateSyntax(anyString())
    }

    @Test
    fun `should validate diagram quality`() {
        // Given
        val diagram = PlantumlDiagram(
            conversation = listOf("Test conversation"),
            plantuml = PlantumlCode(
                code = "@startuml\nactor User\n@enduml",
                description = "Test diagram"
            )
        )

        // When
        val result = diagramProcessor.validateDiagram(diagram)

        // Then
        assertEquals(8, result.score)
        assertNotNull(result.feedback)
        assertEquals(3, result.recommendations.size)
    }

    @Test
    fun `should save for rag training`() {
        // Given
        val diagram = PlantumlDiagram(
            conversation = listOf("Test conversation"),
            plantuml = PlantumlCode(
                code = "@startuml\nactor User\n@enduml",
                description = "Test diagram"
            )
        )
        val validation = ValidationFeedback(
            score = 8,
            feedback = "Good diagram",
            recommendations = listOf("Add more details")
        )

        // When
        diagramProcessor.saveForRagTraining(diagram, validation)

        // Then
        // The test passes if no exception is thrown
    }

    // Utility methods for mock configuration
    private fun setupValidSyntaxMock() {
        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(SyntaxValidationResult.Valid)
    }

    private fun setupInvalidSyntaxMock() {
        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(SyntaxValidationResult.Invalid("Test error", "Stack trace"))
    }

    private fun setupInvalidThenValidSyntaxMock() {
        `when`(mockPlantumlService.validateSyntax(anyString()))
            .thenReturn(SyntaxValidationResult.Invalid("Test error", "Stack trace"))
            .thenReturn(SyntaxValidationResult.Valid)
    }
}