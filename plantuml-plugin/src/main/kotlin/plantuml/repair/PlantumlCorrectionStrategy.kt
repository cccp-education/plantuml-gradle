package plantuml.repair

/**
 * Strategy that produced a [PlantumlBlockCorrection] (EPIC GBL-010, G-3).
 */
enum class PlantumlCorrectionStrategy {
    /** The deterministic repair rule fixed the block (label integrity — D2). */
    DETERMINISTIC,

    /** The injected [PlantumlBlockRepairer] fixed the block. */
    AI,

    /** No correction converged — the source block is preserved verbatim (D5). */
    PRESERVED,
}
