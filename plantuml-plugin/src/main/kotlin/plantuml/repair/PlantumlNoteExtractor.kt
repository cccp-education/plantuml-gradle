package plantuml.repair

/**
 * A PlantUML `note` occurrence: the [raw] substring as it appears in the source
 * (the in-place region to replace) and its trimmed [text] (the translatable prose).
 *
 * [singleLine] is the grammar discriminator: the colon form `note right: text`
 * is one physical line (a real newline breaks the diagram), while the block form
 * `note right … end note` legitimately spans lines. The translation port must
 * escape newlines for the former and keep them for the latter.
 */
data class PlantumlNote(
    val raw: String,
    val text: String,
    val singleLine: Boolean,
)

/**
 * Pure extractor of PlantUML `note` texts (EPIC GBL-010, G-3 — the "notes" part).
 *
 * A PlantUML `note` carries **translatable prose** that is neither a quoted label
 * nor a `title`/`header`/`footer`/`caption` directive, so the N0
 * [contracts.plantuml.PlantUmlBlock.labels] surface does not catch it. This
 * plantuml-local leaf completes the translatable surface of a `[plantuml]` block
 * (the notes) **without extending the N0 contract**: the N0 translation port's
 * implementation ([plantuml.boundary.PlantumlTranslationPortAdapter]) consumes
 * [occurrences] to translate the note prose in place.
 *
 * Handled forms:
 *
 *  - single-line: `note left of A : text` → [PlantumlNote.raw] = `text` (trimmed)
 *  - multi-line:  `note right of A` … `end note` → [PlantumlNote.raw] = the body
 *    exactly as in the source; [PlantumlNote.text] = the body with each line trimmed,
 *    blanks dropped
 *  - `note over A, B`, `note as N` (any target spec)
 *
 * Distinct, in first-seen order. Pure: no I/O, no LLM, no rendering.
 */
object PlantumlNoteExtractor {

    private val NOTE_START = Regex("""^\s*note\b(.*)$""")
    private val END_NOTE = Regex("""^\s*end\s*note\b""")

    /** The note occurrences of [source], in first-seen order. */
    fun occurrences(source: String): List<PlantumlNote> {
        val collected = mutableListOf<PlantumlNote>()
        var inNote = false
        val rawBody = StringBuilder()
        val textBody = StringBuilder()

        for (line in source.lines()) {
            if (inNote) {
                // A note body ends at `end note` or at the diagram boundary `@…`
                // (an unterminated note must not swallow the closing directive).
                if (END_NOTE.containsMatchIn(line) || line.trimStart().startsWith("@")) {
                    flushInto(collected, rawBody, textBody)
                    inNote = false
                    continue
                }
                if (rawBody.isNotEmpty()) rawBody.append('\n')
                rawBody.append(line)
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    if (textBody.isNotEmpty()) textBody.append('\n')
                    textBody.append(trimmed)
                }
                continue
            }
            val match = NOTE_START.find(line) ?: continue
            val rest = match.groupValues[1]
            val separator = rest.indexOf(':')
            if (separator >= 0) {
                rest.substring(separator + 1).trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { collected.add(PlantumlNote(raw = it, text = it, singleLine = true)) }
            } else {
                inNote = true
                rawBody.clear()
                textBody.clear()
            }
        }
        if (inNote) flushInto(collected, rawBody, textBody)
        return collected.distinctBy { it.raw }
    }

    /** The translatable note texts of [source], distinct and in first-seen order. */
    fun notes(source: String): List<String> = occurrences(source).map { it.text }.distinct()

    /** Whether [source] carries at least one note. */
    fun hasNotes(source: String): Boolean = occurrences(source).isNotEmpty()

    private fun flushInto(
        collected: MutableList<PlantumlNote>,
        rawBody: StringBuilder,
        textBody: StringBuilder,
    ) {
        val text = textBody.toString()
        if (text.isNotEmpty()) {
            collected.add(PlantumlNote(raw = rawBody.toString(), text = text, singleLine = false))
        }
        rawBody.clear()
        textBody.clear()
    }
}
