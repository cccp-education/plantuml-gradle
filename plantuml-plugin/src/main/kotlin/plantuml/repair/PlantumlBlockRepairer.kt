package plantuml.repair

/**
 * Port for AI-assisted correction of a `[plantuml]` block (EPIC GBL-010, G-3).
 *
 * The correction of a block that fails validation (the `PENDING` status of
 * [PlantumlBlockStatus]) is a two-step pipeline: a **deterministic** repair first
 * ([PlantumlLabelIntegrity.repair] — the D2 raw-newline defect), then an
 * **AI-assisted** correction for the remaining cases. This `fun interface` is the
 * seam of the second step: the caller injects a provider (an LLM adapter in
 * production, a fake in tests), so [PlantumlBlockCorrector] stays pure.
 *
 * The returned source is accepted only if it parses again (the round-trip gate is
 * enforced by the corrector, not by the port — a port may return anything).
 */
fun interface PlantumlBlockRepairer {
    /** Returns a corrected block source, or `null` when the repair failed. */
    fun repair(source: String): String?
}
