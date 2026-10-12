package plantuml.repair

/**
 * Translation status of a `[plantuml]` block (EPIC GBL-010, G-3b).
 *
 * The unified translation tree (GBL-010) assigns every node a single owner and a
 * status. This enum is the **plantuml leaf** vocabulary — the four states the
 * `[plantuml]` block can be in, as seen from the source (no I/O, no rendering):
 *
 *  - [MISSING]    — the block is absent / blank (nothing to translate).
 *  - [TRANSLATED] — the block is present and its syntax is valid (converged).
 *  - [PENDING]    — the block is present but not (yet) valid, and not a
 *                   structurally-broken label — it needs the assisted correction
 *                   pass (GBL-010 G-3). Retriable.
 *  - [BROKEN]     — the block is structurally invalid: a quoted label spans a raw
 *                   newline (the D2 / CHE-DIAGRAM defect). Repairable by
 *                   [PlantumlLabelIntegrity.repair].
 */
enum class PlantumlBlockStatus {
    MISSING,
    PENDING,
    TRANSLATED,
    BROKEN,
}
