package plantuml.service

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import net.sourceforge.plantuml.SourceStringReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import plantuml.PlantumlMessages
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Service for PlantUML diagram processing and syntax validation.
 *
 * Public API consumable by other boroughs (document-gradle, codex, slider, bakery)
 * via `education.cccp:plantuml-plugin` on Maven Central.
 *
 * @see contracts.plantuml.SyntaxValidationResult
 */
class PlantumlService {
    private val logger: Logger = LoggerFactory.getLogger(PlantumlService::class.java)

    /**
     * Validates PlantUML syntax using the native PlantUML parser.
     *
     * Performs two checks:
     * 1. A **matching** `@start<type>` / `@end<type>` delimiter pair — every
     *    PlantUML diagram type, not only sequence/class diagrams (`@startuml`):
     *    `@startmindmap`/`@endmindmap`, `@startwbs`, `@startgantt`,
     *    `@startjson`, `@startsalt`, `@startditaa`… (CHE-I18N-COMPLETION S-084 —
     *    the previous `contains("@startuml")` gate rejected every translated
     *    mindmap, so the diagram translation was silently discarded)
     * 2. Parsing via `net.sourceforge.plantuml.SourceStringReader`
     *
     * @param plantumlCode The PlantUML source code to validate
     * @return [SyntaxValidationResult.Valid] if syntax is correct,
     *         [SyntaxValidationResult.Invalid] with error details otherwise
     */
    fun validateSyntax(plantumlCode: String): SyntaxValidationResult {
        return try {
            SourceStringReader(plantumlCode)

            if (!hasMatchingDiagramDelimiters(plantumlCode)) {
                return SyntaxValidationResult.Invalid(
                    "Missing or mismatched diagram delimiters",
                    "PlantUML code must be wrapped in a matching @start<type> and @end<type> pair " +
                        "(e.g. @startuml/@enduml, @startmindmap/@endmindmap)",
                )
            }

            SyntaxValidationResult.Valid
        } catch (e: Exception) {
            SyntaxValidationResult.Invalid(
                "PlantUML parsing failed: ${e.message}",
                e.stackTraceToString(),
            )
        }
    }

    /**
     * True when every `@start<type>` is closed by the matching `@end<type>` in
     * document order. Pure structural guard (the parser above catches real syntax
     * errors); it only prevents a plain-text block from being accepted as a
     * diagram.
     */
    private fun hasMatchingDiagramDelimiters(code: String): Boolean {
        val starts = DIAGRAM_START.findAll(code).map { it.groupValues[1].lowercase() }.toList()
        val ends = DIAGRAM_END.findAll(code).map { it.groupValues[1].lowercase() }.toList()
        if (starts.isEmpty() || starts.size != ends.size) return false
        return starts.zip(ends).all { (start, end) -> start == end }
    }

    fun generateImage(
        plantumlCode: String,
        outputFile: File,
    ) {
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

    /**
     * Renders the PlantUML source to PNG bytes (EPIC PLT-AGENT, DIA-01).
     *
     * Unlike [generateImage], this never writes the textual fallback: on failure it
     * returns `null`, so the design reviewer degrades instead of reviewing a non-image.
     */
    fun renderPng(plantumlCode: String): ByteArray? =
        try {
            val reader = SourceStringReader(plantumlCode)
            val outputStream = ByteArrayOutputStream()
            reader.outputImage(outputStream)
            outputStream.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.warn("PlantUML render to PNG failed: ${e.message}")
            null
        }

    /**
     * The native parser exposed through the N0 validation port (PLT-CR3-2), so the
     * output probe can re-parse the produced diagram source.
     */
    fun syntaxValidator(): PlantUmlSyntaxValidator = PlantUmlSyntaxValidator { code -> validateSyntax(code) }

    private companion object {
        /** `@start<type>` at the beginning of a line (`@startuml`, `@startmindmap`…). */
        val DIAGRAM_START = Regex("""(?m)^\s*@start(\w+)\b""")

        /** `@end<type>` at the beginning of a line (`@enduml`, `@endmindmap`…). */
        val DIAGRAM_END = Regex("""(?m)^\s*@end(\w+)\b""")
    }
}
