package plantuml.context

/**
 * Pure composer of the plantuml generation prompt with the augmented context
 * (EPIC PLT-CONTEXT, CTX-04, decision D-3/D-4).
 *
 * The collector ([plantuml.tasks.CollectPlantumlAugmentedContextTask], CTX-03)
 * renders the augmented context into `build/plantuml/augmented-context.txt`; the
 * generation task reads that artefact and injects it into the LLM prompt through
 * this object. The injection is **additive and backward compatible**: a blank or
 * missing augmented context leaves the base prompt untouched, so the offline
 * path (`DocKnowledgeBase` in [plantuml.service.GraphifyPromptAdapter]) remains
 * the fallback (D-3).
 *
 * This object is pure — no I/O, no Gradle, no network — which keeps the prompt
 * composition unit-testable.
 */
object AugmentedContextPrompt {

    /** Marker line opening the injected block, so provenance stays auditable. */
    const val CONTEXT_HEADER: String =
        "Augmented context (governance EAGER + retrieved RAG/Graphify/Docs):"

    /**
     * Prepends [augmentedContext] to [prompt] under [CONTEXT_HEADER].
     *
     * When [augmentedContext] is blank (or only whitespace), [prompt] is returned
     * unchanged — the offline fallback path. The context is trimmed so the
     * rendered block stays stable regardless of the collector's trailing newline.
     */
    fun prepend(augmentedContext: String, prompt: String): String {
        val context = augmentedContext.trim()
        if (context.isEmpty()) return prompt
        return "$CONTEXT_HEADER\n$context\n\n$prompt"
    }
}
