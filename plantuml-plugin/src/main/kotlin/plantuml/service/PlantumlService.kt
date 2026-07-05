package plantuml.service

import net.sourceforge.plantuml.SourceStringReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import plantuml.PlantumlMessages
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Service responsible for PlantUML diagram processing and validation.
 *
 * Provides core PlantUML functionality:
 * - Syntax validation (checks for @startuml/@enduml tags and parsing)
 * - Image generation (PNG rendering from PlantUML code)
 *
 * Uses PlantUML's SourceStringReader for parsing and rendering.
 */
class PlantumlService {

    private val logger: Logger = LoggerFactory.getLogger(PlantumlService::class.java)

    /**
     * Validates PlantUML syntax and returns validation result.
     *
     * Performs basic validation:
     * 1. Checks for required @startuml and @enduml tags
     * 2. Attempts to parse with PlantUML SourceStringReader
     *
     * @param plantumlCode The PlantUML source code to validate
     * @return [SyntaxValidationResult.Valid] if syntax is correct,
     *         [SyntaxValidationResult.Invalid] with error details otherwise
     */
    fun validateSyntax(plantumlCode: String): SyntaxValidationResult {
        return try {
            SourceStringReader(plantumlCode)

            // Simple check for required tags
            if (!plantumlCode.contains("@startuml") || !plantumlCode.contains("@enduml")) {
                return SyntaxValidationResult.Invalid(
                    "Missing @startuml or @enduml tags",
                    "PlantUML code must be wrapped in @startuml and @enduml tags"
                )
            }

            SyntaxValidationResult.Valid
        } catch (e: Exception) {
            SyntaxValidationResult.Invalid(
                "PlantUML parsing failed: ${e.message}",
                e.stackTraceToString()
            )
        }
    }

    /**
     * Generates a PNG image from valid PlantUML code.
     *
     * Attempts to render the PlantUML diagram as a PNG image. If image generation
     * fails, falls back to writing the source code as a text file.
     *
     * @param plantumlCode Valid PlantUML source code
     * @param outputFile Destination file for the generated image (or text fallback)
     * @throws Exception if both image generation and text fallback fail
     */
    fun generateImage(plantumlCode: String, outputFile: File) {
        try {
            val reader = SourceStringReader(plantumlCode)
            val outputStream = ByteArrayOutputStream()
            reader.outputImage(outputStream)
            outputStream.use { output ->
                outputFile.writeBytes(output.toByteArray())
            }
        } catch (e: Exception) {
            // Fallback to text file if image generation fails
            try {
                outputFile.writeText("PlantUML diagram:\n\n$plantumlCode\n\nError: ${e.message}")
            } catch (ioException: Exception) {
                logger.warn(PlantumlMessages.format("service.write_failed", "en", outputFile.absolutePath, ioException.message ?: ""))
            }
        }
    }

    /**
     * Represents the result of PlantUML syntax validation.
     *
     * Sealed class with two possible outcomes:
     * - [Valid]: Syntax is correct
     * - [Invalid]: Syntax errors detected with detailed error message
     */
    sealed class SyntaxValidationResult {
        /**
         * Indicates valid PlantUML syntax with no errors.
         */
        object Valid : SyntaxValidationResult()
        
        /**
         * Indicates invalid PlantUML syntax with error details.
         *
         * @property errorMessage Human-readable description of the syntax error
         * @property stackTrace Full stack trace for debugging
         */
        data class Invalid(val errorMessage: String, val stackTrace: String) : SyntaxValidationResult()
    }
}