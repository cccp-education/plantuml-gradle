package plantuml.repair

/**
 * One entry of the plantuml translation-report — a `[plantuml]` block with its
 * [PlantumlBlockStatus] (EPIC GBL-010, G-3b).
 *
 * The report is the plantuml **leaf** of the unified translation tree (G-4): the
 * `owner` is always `plantuml` (the borough owns its leaf), the [ref] identifies
 * the block (file name, AST anchor, or consumer-chosen key) and [status] is the
 * audited state.
 *
 * @property ref   stable reference of the block (non-blank).
 * @property status audited translation status.
 * @property owner leaf owner — always `plantuml`.
 */
data class PlantumlBlockEntry(
    val ref: String,
    val status: PlantumlBlockStatus,
    val owner: String = OWNER,
) {
    init {
        require(ref.isNotBlank()) { "ref must not be blank" }
    }

    companion object {
        /** The single owner of the `[plantuml]` leaf in the unified tree. */
        const val OWNER: String = "plantuml"
    }
}
