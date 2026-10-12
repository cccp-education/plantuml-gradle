package plantuml.repair

/**
 * Pure extractor of PlantUML `note` texts (EPIC GBL-010, G-3 — the "notes" part).
 *
 * A PlantUML `note` carries **translatable prose** that is neither a quoted label
 * nor a `title`/`header`/`footer`/`caption` directive, so the N0
 * [contracts.plantuml.PlantUmlBlock.labels] surface does not catch it. This
 * plantuml-local leaf completes the translatable surface of a `[plantuml]` block
 * (the notes) **without extending the N0 contract** — the port / the unified tree
 * (G-4) consume this extractor to translate the notes.
 *
 * Handled forms:
 *
 *  - single-line: `note left of A : text` → `text`
 *  - multi-line:  `note right of A` … `end note` → the trimmed body
 *  - `note over A, B`, `note as N` … `end note` (any target spec)
 *
 * Distinct, in first-seen order. Pure: no I/O, no LLM, no rendering.
 */
object PlantumlNoteExtractor {

    private val NOTE_START = Regex("""^\s*note\b(.*)$""")
    private val END_NOTE = Regex("""^\s*end\s*note\b""")

    /** The translatable note texts of [source], distinct and in first-seen order. */
    fun notes(source: String): List<String> {
        val collected = mutableListOf<String>()
        var inNote = false
        val body = StringBuilder()

        for (line in source.lines()) {
            if (inNote) {
                if (END_NOTE.containsMatchIn(line)) {
                    flushInto(collected, body)
                    inNote = false
                } else {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(line)
                }
                continue
            }
            val match = NOTE_START.find(line) ?: continue
            val rest = match.groupValues[1]
            val separator = rest.indexOf(':')
            if (separator >= 0) {
                rest.substring(separator + 1).trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let(collected::add)
            } else {
                inNote = true
                body.clear()
            }
        }
        if (inNote) flushInto(collected, body)
        return collected.distinct()
    }

    /** Whether [source] carries at least one note. */
    fun hasNotes(source: String): Boolean = notes(source).isNotEmpty()

    private fun flushInto(collected: MutableList<String>, body: StringBuilder) {
        body.toString().trim()
            .takeIf { it.isNotEmpty() }
            ?.let(collected::add)
        body.clear()
    }
}
