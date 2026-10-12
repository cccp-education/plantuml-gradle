package plantuml.repair

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult

/**
 * Pure classifier of a `[plantuml]` block's translation status (EPIC GBL-010, G-3b).
 *
 * Owned by the plantuml borough: the unified translation tree (G-4) walks the AST,
 * surfaces the `[plantuml]` blocks and asks this leaf for each block's status. The
 * classification is **source-only** and uses the N0 [PlantUmlSyntaxValidator] port
 * for the syntax oracle (native PlantUML parser behind the adapter) — never a
 * second parser, never an LLM (D-2 / GBL-007).
 *
 * Rules (first match wins):
 *
 *  1. blank source                              → [PlantumlBlockStatus.MISSING]
 *  2. quoted label spanning a raw newline      → [PlantumlBlockStatus.BROKEN]
 *     (the D2 / CHE-DIAGRAM defect — repairable via [PlantumlLabelIntegrity.repair])
 *  3. no validator injected                    → [PlantumlBlockStatus.PENDING]
 *     (cannot be confirmed — the assisted correction pass, G-3)
 *  4. validator says valid                     → [PlantumlBlockStatus.TRANSLATED]
 *  5. validator says invalid                   → [PlantumlBlockStatus.PENDING]
 *     (present but not converged — the assisted correction pass, G-3)
 */
object PlantumlBlockClassifier {

    /** Classifies [source] into a [PlantumlBlockStatus] using [validator] as oracle. */
    fun classify(
        source: String,
        validator: PlantUmlSyntaxValidator?,
    ): PlantumlBlockStatus {
        if (source.isBlank()) return PlantumlBlockStatus.MISSING
        if (PlantumlLabelIntegrity.hasBrokenQuotedLabel(source)) {
            return PlantumlBlockStatus.BROKEN
        }
        if (validator == null) return PlantumlBlockStatus.PENDING
        return when (validator.validate(source)) {
            is SyntaxValidationResult.Valid -> PlantumlBlockStatus.TRANSLATED
            is SyntaxValidationResult.Invalid -> PlantumlBlockStatus.PENDING
        }
    }
}
