package plantuml.validation

/**
 * Result of PlantUML syntax validation.
 *
 * Public API consumable by other boroughs (document-gradle, codex, slider, bakery)
 * without depending on the full plantuml-plugin. This sealed class is a standalone
 * type in `plantuml.validation` — zero dependency on Gradle or PlantUML internals.
 *
 * Two possible outcomes:
 * - [Valid]: syntax is correct, diagram can be rendered
 * - [Invalid]: syntax errors detected with detailed error information
 *
 * @see plantuml.service.PlantumlService.validateSyntax
 */
sealed class SyntaxValidationResult {
    /**
     * Indicates valid PlantUML syntax with no errors.
     * The diagram can be safely rendered to PNG/SVG.
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
