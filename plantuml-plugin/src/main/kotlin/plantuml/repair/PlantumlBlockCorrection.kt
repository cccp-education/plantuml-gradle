package plantuml.repair

/**
 * Result of a `[plantuml]` block correction (EPIC GBL-010, G-3).
 *
 * @property source   the (possibly corrected) block source — the original source
 *                    verbatim when [strategy] is [PlantumlCorrectionStrategy.PRESERVED].
 * @property status   the block status after correction ([PlantumlBlockStatus.TRANSLATED]
 *                    when a correction converged, otherwise the initial status).
 * @property strategy the rule that produced the correction.
 */
data class PlantumlBlockCorrection(
    val source: String,
    val status: PlantumlBlockStatus,
    val strategy: PlantumlCorrectionStrategy,
) {
    /** True when the correction actually changed the source (a fix was applied). */
    val corrected: Boolean get() = strategy != PlantumlCorrectionStrategy.PRESERVED
}
