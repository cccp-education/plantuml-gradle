package plantuml.service

import net.sourceforge.plantuml.SourceStringReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import plantuml.PlantumlMessages
import plantuml.validation.SyntaxValidationResult
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Service for PlantUML diagram processing and syntax validation.
 *
 * Public API consumable by other boroughs (document-gradle, codex, slider, bakery)
 * via `education.cccp:plantuml-plugin` on Maven Central.
 *
 * @see plantuml.validation.SyntaxValidationResult
 */
class PlantumlService {

    private val logger: Logger = LoggerFactory.getLogger(PlantumlService::class.java)

    /**
     * Validates PlantUML syntax using the native PlantUML parser.
     *
     * Performs two checks:
     * 1. Required `@startuml` and `@enduml` tags
     * 2. Parsing via `net.sourceforge.plantuml.SourceStringReader`
     *
     * @param plantumlCode The PlantUML source code to validate
     * @return [SyntaxValidationResult.Valid] if syntax is correct,
     *         [SyntaxValidationResult.Invalid] with error details otherwise
     */
    fun validateSyntax(plantumlCode: String): SyntaxValidationResult {
        return try {
            SourceStringReader(plantumlCode)

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

    fun generateImage(plantumlCode: String, outputFile: File) {
        try {
            val reader = SourceStringReader(plantumlCode)
            val outputStream = ByteArrayOutputStream()
            reader.outputImage(outputStream)
            outputStream.use { output ->
                outputFile.writeBytes(output.toByteArray())
            }
        } catch (e: Exception) {
            try {
                outputFile.writeText("PlantUML diagram:\n\n$plantumlCode\n\nError: ${e.message}")
            } catch (ioException: Exception) {
                logger.warn(PlantumlMessages.format("service.write_failed", "en", outputFile.absolutePath, ioException.message ?: ""))
            }
        }
    }
}