package plantuml.corpus

/**
 * One `[plantuml]` block found in an AsciiDoc document (EPIC PLT-I18N-OUTPUT,
 * PLT-OUT-1).
 *
 * @property document corpus-relative document path (`/` separated)
 * @property id block id declared in the attribute line, or a deterministic
 *   `line-<n>` fallback when the author omitted it
 * @property line 1-based line of the `[plantuml]` attribute
 * @property source block body without the AsciiDoc delimiters
 */
data class PlantumlCorpusBlock(
    val document: String,
    val id: String,
    val line: Int,
    val source: String,
)
