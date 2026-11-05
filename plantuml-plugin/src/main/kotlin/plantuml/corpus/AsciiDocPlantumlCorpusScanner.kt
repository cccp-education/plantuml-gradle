package plantuml.corpus

/**
 * Extracts the real `[plantuml]` diagrams of an AsciiDoc document
 * (EPIC PLT-I18N-OUTPUT, PLT-OUT-1).
 *
 * The scanner is a pure, line-oriented state machine: it never renders and
 * never calls the validator. Two rules matter for the corpus gate:
 *
 * - only a `[plantuml…]` block attribute starts a diagram — `[source,plantuml]`
 *   is a *listing* of PlantUML source, never a diagram to render;
 * - a diagram inside a literal block, a comment block (`////`) or a commented
 *   line is ignored, so the gate measures the published structure, not text
 *   quoted in an article.
 */
object AsciiDocPlantumlCorpusScanner {
    private const val COMMENT_FENCE = "////"

    private val ATTRIBUTE = Regex("""^\[plantuml([,\#][^\]]*)?]$""", RegexOption.IGNORE_CASE)
    private val DELIMITER = Regex("""^([-.]{4,})$""")
    private val ANCHOR = Regex("""^\[\[([^\]]+)]]$""")

    /**
     * Scans one document and returns its diagram blocks in document order.
     *
     * @param document corpus-relative path used in the report
     * @param content raw AsciiDoc document
     */
    fun scan(
        document: String,
        content: String,
    ): List<PlantumlCorpusBlock> {
        val lines = content.lines()
        val blocks = mutableListOf<PlantumlCorpusBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            when {
                line == COMMENT_FENCE -> index = skipUntil(lines, index + 1, COMMENT_FENCE)
                ATTRIBUTE.matches(line) -> {
                    val parsed = readBlock(document, lines, index)
                    if (parsed == null) {
                        index++
                    } else {
                        blocks += parsed.block
                        index = parsed.nextIndex
                    }
                }
                // A literal block opened without a [plantuml] attribute would
                // otherwise let its body be scanned as document structure.
                DELIMITER.matches(line) -> index = skipUntil(lines, index + 1, line)
                else -> index++
            }
        }
        return blocks
    }

    private data class ParsedBlock(
        val block: PlantumlCorpusBlock,
        val nextIndex: Int,
    )

    private fun readBlock(
        document: String,
        lines: List<String>,
        attributeIndex: Int,
    ): ParsedBlock? {
        var id = declaredId(lines[attributeIndex].trim())
        if (id == null && attributeIndex > 0) {
            id = ANCHOR.matchEntire(lines[attributeIndex - 1].trim())?.groupValues?.get(1)
        }
        var cursor = attributeIndex + 1
        while (cursor < lines.size) {
            val candidate = lines[cursor].trim()
            when {
                candidate.isEmpty() || candidate.startsWith("//") -> cursor++
                ANCHOR.matches(candidate) -> {
                    if (id == null) id = ANCHOR.matchEntire(candidate)?.groupValues?.get(1)
                    cursor++
                }
                DELIMITER.matches(candidate) -> return readBody(document, lines, attributeIndex, id, cursor, candidate)
                else -> return null
            }
        }
        return null
    }

    private fun readBody(
        document: String,
        lines: List<String>,
        attributeIndex: Int,
        id: String?,
        delimiterIndex: Int,
        delimiter: String,
    ): ParsedBlock? {
        val body = mutableListOf<String>()
        var cursor = delimiterIndex + 1
        while (cursor < lines.size) {
            if (lines[cursor].trim() == delimiter) {
                return ParsedBlock(
                    PlantumlCorpusBlock(
                        document = document,
                        id = id ?: "line-${attributeIndex + 1}",
                        line = attributeIndex + 1,
                        source = body.joinToString("\n"),
                    ),
                    cursor + 1,
                )
            }
            body += lines[cursor]
            cursor++
        }
        return null
    }

    private fun declaredId(attribute: String): String? {
        val suffix =
            ATTRIBUTE
                .matchEntire(attribute)
                ?.groupValues
                ?.get(1)
                .orEmpty()
                .trim()
        return when {
            suffix.startsWith("#") -> suffix.removePrefix("#").trim().ifBlank { null }
            suffix.startsWith(",") ->
                suffix
                    .removePrefix(",")
                    .split(',', limit = 2)
                    .first()
                    .trim()
                    .removeSurrounding("\"")
                    .ifBlank { null }
            else -> null
        }
    }

    private fun skipUntil(
        lines: List<String>,
        from: Int,
        terminator: String,
    ): Int {
        var index = from
        while (index < lines.size) {
            if (lines[index].trim() == terminator) return index + 1
            index++
        }
        return lines.size
    }
}
