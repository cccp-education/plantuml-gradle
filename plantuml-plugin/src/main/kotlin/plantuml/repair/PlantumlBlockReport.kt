package plantuml.repair

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import plantuml.toolchain.PlantUmlToolchain
import plantuml.toolchain.PlantUmlToolchainReference

/**
 * The plantuml leaf report of the unified translation tree (EPIC GBL-010, G-3b).
 *
 * Aggregates the [PlantumlBlockEntry] of every `[plantuml]` block plus the
 * **reference toolchain version** ([PlantUmlToolchainReference], D6). This is what
 * document / cheroliv read (G-4) to merge the three branch reports into
 * `translation-tree.json` (node → status → owner). The report is serialized as
 * pretty-printed JSON (Jackson `INDENT_OUTPUT`) for diff-ability.
 *
 * An empty report is legitimate (no `[plantuml]` block) — no fail-fast invariant.
 *
 * @property blocks    the audited `[plantuml]` blocks.
 * @property toolchain the PlantUML toolchain versions of reference (owner plantuml).
 * @property owner     leaf owner — always `plantuml`.
 */
data class PlantumlBlockReport(
    val blocks: List<PlantumlBlockEntry> = emptyList(),
    val toolchain: PlantUmlToolchainReference = PlantUmlToolchain.reference,
    val owner: String = PlantumlBlockEntry.OWNER,
) {

    /** Total number of audited blocks. */
    @get:JsonProperty("total")
    val total: Int get() = blocks.size

    /** Per-status block counts, keyed by [PlantumlBlockStatus] name. */
    @get:JsonProperty("counts")
    val counts: Map<String, Int> get() =
        PlantumlBlockStatus.entries.associate { status -> status.name to count(status) }

    /** Number of blocks in [status]. */
    fun count(status: PlantumlBlockStatus): Int = blocks.count { it.status == status }

    /** Serialises this report as pretty-printed JSON (Jackson `INDENT_OUTPUT`). */
    fun toJson(): String = mapper.writeValueAsString(this)

    companion object {
        private val mapper: ObjectMapper =
            jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    }
}
