package plantuml.repair

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult

/**
 * Pure corrector of a `[plantuml]` block (EPIC GBL-010, G-3).
 *
 * Owned by the plantuml borough: the unified translation tree (G-4) audits each
 * `[plantuml]` block with [PlantumlBlockClassifier] and asks this leaf to
 * **correct** the blocks that did not converge (`BROKEN` / `PENDING`). The
 * correction is a **two-step pipeline**, in order:
 *
 *  1. **Deterministic** — [PlantumlLabelIntegrity.repair] re-escapes a quoted
 *     label that spans a raw newline (the D2 / CHE-DIAGRAM defect). Applied first
 *     because it is free, deterministic and already proven.
 *  2. **AI-assisted** — the injected [PlantumlBlockRepairer] (optional) proposes a
 *     corrected source. Accepted **only if it parses again** (round-trip gate D5) —
 *     a provider that returns an unrenderable block is discarded and the source is
 *     preserved.
 *
 * A block already [PlantumlBlockStatus.TRANSLATED] (or [PlantumlBlockStatus.MISSING])
 * is returned verbatim ([PlantumlCorrectionStrategy.PRESERVED]). Pure: no I/O, no
 * LLM, no rendering — the oracle is the N0 [PlantUmlSyntaxValidator] port.
 */
object PlantumlBlockCorrector {

    /**
     * Corrects [source], using [validator] as the syntax oracle and [repairer] as
     * the optional AI correction provider.
     */
    fun correct(
        source: String,
        validator: PlantUmlSyntaxValidator?,
        repairer: PlantumlBlockRepairer? = null,
    ): PlantumlBlockCorrection {
        val initial = PlantumlBlockClassifier.classify(source, validator)
        if (initial == PlantumlBlockStatus.TRANSLATED || initial == PlantumlBlockStatus.MISSING) {
            return PlantumlBlockCorrection(source, initial, PlantumlCorrectionStrategy.PRESERVED)
        }

        // 1. Deterministic repair (label integrity — the D2 defect).
        if (PlantumlLabelIntegrity.hasBrokenQuotedLabel(source)) {
            val repaired = PlantumlLabelIntegrity.repair(source)
            if (repaired != source && (validator == null || validator.validate(repaired) is SyntaxValidationResult.Valid)) {
                return PlantumlBlockCorrection(repaired, PlantumlBlockStatus.TRANSLATED, PlantumlCorrectionStrategy.DETERMINISTIC)
            }
        }

        // 2. AI-assisted correction — accepted only when it parses again (D5).
        if (repairer != null && validator != null) {
            val candidate = repairer.repair(source)
            if (candidate != null && validator.validate(candidate) is SyntaxValidationResult.Valid) {
                return PlantumlBlockCorrection(candidate, PlantumlBlockStatus.TRANSLATED, PlantumlCorrectionStrategy.AI)
            }
        }

        // 3. No correction converged — preserve the source verbatim (D5).
        return PlantumlBlockCorrection(source, initial, PlantumlCorrectionStrategy.PRESERVED)
    }
}
