package plantuml.context

/**
 * Mutable accumulator of plantuml context provenance (EPIC PLT-CONTEXT,
 * CTX-03, decision D-6).
 *
 * [plantuml.tasks.CollectPlantumlAugmentedContextTask] tracks the sources per
 * channel as it assembles the composite context, passes this tracker to
 * [PlantumlContextBuilder.build] (which prunes the channels that did not
 * survive the budget via [retainOnly]), then freezes the result with [build]
 * and serialises it ([ContextProvenance.toJson]).
 *
 * The tracker is deliberately NOT a pure object — it accumulates state during
 * the build. [build] returns an immutable [ContextProvenance] snapshot, so a
 * later [trackChannel] call never mutates an already-frozen snapshot.
 *
 * [trackChannel] replaces the sources of a repeated channel (last write wins)
 * and ignores an empty source list (a channel with no source is not recorded).
 */
class ProvenanceTracker {

    private val tracked = LinkedHashMap<String, ProvenanceChannel>()

    /**
     * Records [sources] for [channel], replacing any previous sources of the
     * same channel. An empty [sources] list is ignored.
     */
    fun trackChannel(channel: String, sources: List<ProvenanceSource>) {
        if (sources.isEmpty()) return
        tracked[channel] = ProvenanceChannel(channel = channel, sources = sources.toList())
    }

    /**
     * Prunes every tracked channel whose name is not in [survivingChannels].
     *
     * Called by [PlantumlContextBuilder.build] with the channel names that
     * survived the token budget (a channel truncated to zero content is
     * dropped from the provenance).
     */
    fun retainOnly(survivingChannels: Set<String>) {
        tracked.keys.retainAll(survivingChannels)
    }

    /** Freezes the accumulated channels into an immutable [ContextProvenance] snapshot. */
    fun build(): ContextProvenance = ContextProvenance(channels = tracked.values.toList())
}
