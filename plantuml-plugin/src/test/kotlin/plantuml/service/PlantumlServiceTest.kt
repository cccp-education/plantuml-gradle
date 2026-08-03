package plantuml.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import plantuml.validation.SyntaxValidationResult
import java.io.File
import kotlin.test.assertTrue

class PlantumlServiceTest {

    private lateinit var plantumlService: PlantumlService

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        plantumlService = PlantumlService()
    }

    @ParameterizedTest
    @ValueSource(strings = ["valid", "invalid"])
    fun `should validate plantuml syntax`(syntaxType: String) {
        when (syntaxType) {
            "valid" -> testValidSyntax()
            "invalid" -> testInvalidSyntax()
        }
    }

    private fun testValidSyntax() {
        // Given
        val validPlantuml = createMinimalValidPlantUml()

        // When
        val result = plantumlService.validateSyntax(validPlantuml)

        // Then
        assertTrue(result is SyntaxValidationResult.Valid)
    }

    private fun testInvalidSyntax() {
        // Given
        val invalidPlantuml = "@startuml\nactor User\nrectangle \"System\"" // Missing @enduml

        // When
        val result = plantumlService.validateSyntax(invalidPlantuml)

        // Then
        assertTrue(result is SyntaxValidationResult.Invalid)
    }

    @Test
    fun `should generate image from plantuml code`() {
        // Given
        val plantumlCode = createMinimalValidPlantUml()
        val outputFile = File(tempDir, "test.png")

        // When
        plantumlService.generateImage(plantumlCode, outputFile)

        // Then
        assertTrue(outputFile.exists())
        // The file should contain data (not empty)
        assertTrue(outputFile.length() > 0)
    }

    // Utility method to create a minimal valid PlantUML
    private fun createMinimalValidPlantUml(): String {
        return """
            @startuml
            actor User
            @enduml
        """.trimIndent()
    }
}