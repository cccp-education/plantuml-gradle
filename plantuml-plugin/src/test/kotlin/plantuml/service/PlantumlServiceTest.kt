package plantuml.service

import contracts.plantuml.SyntaxValidationResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

    @ParameterizedTest
    @ValueSource(strings = ["mindmap", "wbs"])
    fun `should validate non-uml diagram types wrapped in their own delimiters`(type: String) {
        // Given — CHE-I18N-COMPLETION S-084: a mindmap is valid PlantUML; the
        // previous `contains("@startuml")` gate rejected every translated mindmap.
        val code =
            when (type) {
                "mindmap" -> "@startmindmap\n* Root\n** Branch\n@endmindmap"
                else -> "@startwbs\n* Root\n** Branch\n@endwbs"
            }

        // When
        val result = plantumlService.validateSyntax(code)

        // Then
        assertTrue(result is SyntaxValidationResult.Valid)
    }

    @Test
    fun `should reject code without diagram delimiters`() {
        // Given — plain prose is not a diagram.
        val code = "This is not a diagram"

        // When
        val result = plantumlService.validateSyntax(code)

        // Then
        assertTrue(result is SyntaxValidationResult.Invalid)
    }

    @Test
    fun `should reject mismatched diagram delimiters`() {
        // Given — a mindmap closed by @enduml is structurally wrong.
        val code = "@startmindmap\n* Root\n@enduml"

        // When
        val result = plantumlService.validateSyntax(code)

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
    private fun createMinimalValidPlantUml(): String =
        """
        @startuml
        actor User
        @enduml
        """.trimIndent()
}
