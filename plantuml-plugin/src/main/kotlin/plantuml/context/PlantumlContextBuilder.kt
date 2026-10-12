package plantuml.context

import contracts.context.ChannelBudget
import contracts.context.CompositeContext
import contracts.context.ContextChannel

/**
 * Prompt-ready augmented context for a PlantUML generation (EPIC PLT-CONTEXT, CTX-02).
 *
 * @property channels the non-empty channels that survived the token budget
 * @property rendered the sectioned block injected into the generation prompt
 */
data class PlantumlContext(
    val channels: List<ContextChannel>,
    val rendered: String,
) {
    val isEmpty: Boolean get() = channels.isEmpty()
    val nonEmptyCount: Int get() = channels.size
    val tokenEstimate: Int get() = ContextChannel.estimateTokens(rendered)
}

/**
 * Pure assembler of the plantuml augmented context (EPIC PLT-CONTEXT, CTX-02).
 *
 * Mirrors the capsule patron ([capsule.context.CapsuleContextBuilder]): plantuml
 * consumes the N0 `codebase-contracts` context contract ([CompositeContext] +
 * [ContextChannel] + [ChannelBudget]) and renders it into the prompt-ready block that
 * feeds [plantuml.service.DiagramProcessor]. This object is pure — no I/O, no Gradle,
 * no network — which keeps the assembly unit-testable and independent of the N1 engine
 * that produces the [CompositeContext].
 *
 * The canonical channels come from [CompositeContext.toChannels] (EAGER → RAG →
 * GRAPHIFY → DOCS → RESOURCE), the token budgeting from [CompositeContext.channelsWithBudget].
 */
object PlantumlContextBuilder {

    /** The 5 canonical channels of [composite], in contract order. */
    fun toChannels(composite: CompositeContext): List<ContextChannel> = composite.toChannels()

    /** The [composite] channels truncated to their per-channel token budget. */
    fun channelsWithBudget(
        composite: CompositeContext,
        budget: ChannelBudget,
    ): List<ContextChannel> = composite.channelsWithBudget(budget)

    /**
     * Merges the non-empty [channels] into a prompt-ready sectioned block.
     *
     * Each channel renders as `==== <sectionHeader> (<name>)` followed by its content.
     * Blank channels are dropped — an empty or all-blank input yields a blank block.
     */
    fun merge(channels: List<ContextChannel>): String =
        channels
            .filter { it.isNotEmpty() }
            .joinToString("\n\n") { channel ->
                val header = channel.sectionHeader
                if (header.isBlank()) channel.content
                else "==== $header (${channel.name})\n${channel.content}"
            }

    /**
     * Assembles the augmented context for a PlantUML generation: budgets the composite
     * channels via [channelsWithBudget], drops the empty ones and renders the survivors.
     *
     * When [tracker] is non-null (decision D-6, CTX-03), it is pruned to the channels
     * that survived the budget. The collector tracks the sources per channel before
     * calling this method, and the builder signals which channel names are still alive
     * via [ProvenanceTracker.retainOnly]. The builder does NOT compute sources — it only
     * validates the surviving channel names (the collector owns the source [java.io.File]
     * objects). Backward compatible: a null [tracker] (default) performs no tracking.
     */
    fun build(
        composite: CompositeContext,
        budget: ChannelBudget = defaultBudget(),
        tracker: ProvenanceTracker? = null,
    ): PlantumlContext {
        val channels = composite.channelsWithBudget(budget).filter { it.isNotEmpty() }
        if (tracker != null) {
            tracker.retainOnly(channels.map { it.type.name }.toSet())
        }
        return PlantumlContext(channels = channels, rendered = merge(channels))
    }

    /** The N0 default token budget (8000 tokens, 40/30/20/10/0). */
    fun defaultBudget(): ChannelBudget = ChannelBudget()
}
