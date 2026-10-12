package plantuml.context

/**
 * Immutable aggregate of one [contracts.context.ContextChannel]-like provenance
 * group (EPIC PLT-CONTEXT, CTX-03).
 *
 * A channel carries the [channel] label (the N0 channel name, e.g. `EAGER`,
 * `RAG`, `GRAPHIFY`, `DOCS`) plus the list of [sources] that fed it. The
 * aggregate totals derive from the sources — no stored state, always
 * consistent.
 *
 * @property channel  provenance channel label (must match the N0
 *                     [contracts.context.ChannelType] name).
 * @property sources  the sources that survived the budget for this channel.
 */
data class ProvenanceChannel(
    val channel: String,
    val sources: List<ProvenanceSource> = emptyList(),
) {

    /** Sum of the [ProvenanceSource.chars] of all [sources]. */
    fun totalChars(): Int = sources.sumOf { it.chars }

    /** Sum of the [ProvenanceSource.tokens] of all [sources]. */
    fun totalTokens(): Int = sources.sumOf { it.tokens }
}
