package plantuml.repair

/**
 * GBL-010 / G-3 — the PlantUML **label integrity** rule, owned by the plantuml
 * borough.
 *
 * CHE-DIAGRAM proved the D2 break in production: a translation introduced a real
 * line break inside a quoted label. PlantUML requires the escape `\n`, not a raw
 * line break, so the block fails (`Syntax Error?`) and degrades to a literal
 * listing — the variant ships no diagram.
 *
 * This object is the **single source of truth** for that rule:
 *
 *  - [escapeNewlines] — the canonical correction, applied to a translated label
 *    (the N0 translation port delegates here, D5);
 *  - [hasBrokenQuotedLabel] — the source-level audit that finds a frozen block
 *    before a bake (a quoted label spanning a raw newline);
 *  - [repair] — the in-place correction of a whole block's quoted labels.
 *
 * Pure domain: no I/O, no LLM, no rendering.
 */
object PlantumlLabelIntegrity {

    /**
     * Converts a raw line break produced by a translator back to the PlantUML
     * escape `\n`. Without this, `rectangle "a\nb"` becomes two lines and PlantUML
     * fails. Windows (`\r\n`) and classic-Mac (`\r`) line endings are normalised
     * first so an already-escaped label is left untouched.
     */
    fun escapeNewlines(text: String): String =
        text.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

    /**
     * True when a quoted label spans a raw newline — the exact D2 break. Escaped
     * quotes (`\"`) and escaped backslashes (`\\`) inside the label are honoured.
     */
    fun hasBrokenQuotedLabel(source: String): Boolean {
        var inQuote = false
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c == '\\' && i + 1 < source.length) {
                i += 2
                continue
            }
            when {
                c == '"' -> inQuote = !inQuote
                inQuote && (c == '\n' || c == '\r') -> return true
            }
            i++
        }
        return false
    }

    /**
     * Re-escapes every raw newline **inside a quoted label**, leaving the rest of
     * the block byte-identical. Directive values (`title …`) are out of scope: the
     * port handles them separately and PlantUML's directive grammar differs.
     */
    fun repair(source: String): String {
        val out = StringBuilder(source.length)
        var inQuote = false
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '\\' && i + 1 < source.length -> {
                    out.append(c).append(source[i + 1])
                    i += 2
                    continue
                }

                c == '"' -> {
                    inQuote = !inQuote
                    out.append(c)
                }

                inQuote && c == '\r' -> {
                    out.append("\\n")
                    if (i + 1 < source.length && source[i + 1] == '\n') i++
                }

                inQuote && c == '\n' -> out.append("\\n")

                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }
}
