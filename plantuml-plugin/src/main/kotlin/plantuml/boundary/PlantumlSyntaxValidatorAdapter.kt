package plantuml.boundary

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import plantuml.service.PlantumlService

/**
 * Implementation of the N0 [PlantUmlSyntaxValidator] port (EPIC
 * PLT-DIAGRAM-OWNERSHIP US-2).
 *
 * Wraps the native PlantUML parser ([PlantumlService.validateSyntax]) behind the
 * shared contract, so document-gradle and any consumer validate PlantUML without
 * depending on this plugin (D2 — ends the N2→N2 coupling).
 */
class PlantumlSyntaxValidatorAdapter(
    private val service: PlantumlService = PlantumlService(),
) : PlantUmlSyntaxValidator {
    override fun validate(plantumlCode: String): SyntaxValidationResult =
        service.validateSyntax(plantumlCode)
}
