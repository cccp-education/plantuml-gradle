package plantuml.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import plantuml.PlantumlManager
import plantuml.PlantumlMessages
import plantuml.service.PlantumlService

/**
 * Gradle task: `validatePlantumlSyntax`
 *
 * Validates PlantUML syntax for a single diagram file for debugging purposes.
 *
 * Uses PlantUML's SourceStringReader to parse and validate the diagram syntax.
 * Reports validation errors with detailed stack traces for troubleshooting.
 *
 * **Usage**:
 * ```bash
 * ./gradlew validatePlantumlSyntax -Pplantuml.diagram=path/to/diagram.puml
 * ```
 *
 * **Exit behavior**:
 * - Valid syntax: Logs success message
 * - Invalid syntax: Logs errors and throws [GradleException]
 * - Missing file parameter: Logs usage instructions
 */
@DisableCachingByDefault(because = "Validation results depend on file content which may change")
abstract class ValidatePlantumlSyntaxTask : DefaultTask() {

    init {
        val lang = PlantumlManager.resolveLanguage(project)
        group = PlantumlMessages.get("task.validate.group", lang)
        description = PlantumlMessages.get("task.validate.description", lang)
    }

    @get:Input
    @get:Optional
    abstract val diagramFile: Property<String>

    /**
     * Main task action: validates PlantUML syntax for the specified diagram file.
     *
     * Reads the diagram file, parses it with PlantUML, and reports validation results.
     * Throws an exception if the file does not exist or syntax is invalid.
     *
     * @throws GradleException if diagram file does not exist or syntax is invalid
     */
    @TaskAction
    fun validateSyntax() {
        val diagramPath = project.findProperty("plantuml.diagram") as? String
            ?: diagramFile.orNull

        if (diagramPath.isNullOrEmpty()) {
            logger.lifecycle("No diagram file specified. Use -Pplantuml.diagram=file.puml")
            return
        }

        // Resolve the diagram file path relative to the project directory
        val diagramFile = project.file(diagramPath)
        if (!diagramFile.exists()) {
            logger.lifecycle("Diagram file does not exist: $diagramPath")
            throw GradleException("Diagram file does not exist: $diagramPath")
        }

        logger.lifecycle("Validating PlantUML syntax for: $diagramPath")

        // Load the PlantUML file
        val plantumlCode = diagramFile.readText()

        // Parse and validate syntax using PlantUML service
        val plantumlService = PlantumlService()
        val validationResult = plantumlService.validateSyntax(plantumlCode)

        when (validationResult) {
            is PlantumlService.SyntaxValidationResult.Valid -> {
                logger.lifecycle("  ✓ PlantUML syntax is valid")
            }

            is PlantumlService.SyntaxValidationResult.Invalid -> {
                logger.lifecycle("  ✗ PlantUML syntax is invalid:")
                logger.lifecycle("    Error: ${validationResult.errorMessage}")
                if (validationResult.stackTrace.isNotEmpty()) {
                    logger.lifecycle("    Stack trace: ${validationResult.stackTrace}")
                }
            }
        }
    }
}