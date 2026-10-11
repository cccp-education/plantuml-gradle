package plantuml.boundary

/**
 * CHE-DIAGRAM D2 / GBL-010 — batches the labels of a single PlantUML diagram into
 * **one** translation request, instead of one request per label.
 *
 * The N0 [contracts.i18n.TranslationService] is a baby-step contract (one source
 * string → one translated string); a diagram with ~20 labels therefore cost ~20
 * round-trips. Sending the labels joined by a sentinel and splitting the response
 * back cuts the diagram cost by the label count — the run bottleneck (S-073).
 *
 * Splitting is **defensive**: if the provider did not return the sentinel or
 * changed the label count, [split] returns `null` and the port falls back to the
 * per-label path, so a batching failure is never a translation loss.
 *
 * Pure domain: no I/O, no LLM.
 */
object PlantumlLabelBatch {

    /**
     * A non-linguistic sentinel on its own line. Chosen so a translator is unlikely
     * to translate, merge or reflow it, while being trivially splittable.
     */
    const val SEPARATOR = "@@PLT@@"

    /** Joins [labels] into a single request body, one label per line, separated by [SEPARATOR]. */
    fun join(labels: List<String>): String = labels.joinToString("\n$SEPARATOR\n")

    /**
     * Splits a batched [response] back into exactly [expected] translations, or
     * `null` when the provider dropped the sentinel or changed the label count.
     */
    fun split(
        response: String,
        expected: Int,
    ): List<String>? {
        if (expected < 2) return null
        if (!response.contains(SEPARATOR)) return null
        val parts = response.split(SEPARATOR).map { it.trim() }
        return if (parts.size == expected) parts else null
    }
}
