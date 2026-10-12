package plantuml.agent

/**
 * Pure, I/O-free extraction of diagram source from a raw LLM response
 * (EPIC PLT-AGENT, DIA-04).
 *
 * Ported and hardened from `docToolchain/diag-agent` (`_strip_markdown_code_blocks`):
 * the upstream regex anchored on the whole response and required a newline before
 * the closing fence, so prose-wrapped or unterminated fences leaked through and
 * were then rejected by the validator. This object handles:
 *
 *  1. fenced code blocks (```` ```lang … ``` ````), any language tag (including
 *     `-`/`+`), prose before/after the block, and unterminated fences;
 *  2. raw PlantUML (`@startuml … @enduml`), trimming surrounding prose.
 *
 * The JSON payload case stays in [plantuml.service.DiagramProcessor] (Jackson),
 * because its error messages are i18n-governed (`processor.invalid_json` /
 * `processor.malformed_json`).
 */
object LlmCodeExtractor {

    private val FENCE = Regex(
        pattern = "```[^\\n]*\\n(.*?)(?:\\n?```|\\z)",
        option = RegexOption.DOT_MATCHES_ALL,
    )

    private const val START = "@startuml"
    private const val END = "@enduml"

    /**
     * Returns the content of the first fenced code block, trimmed, or `null` when
     * the response carries no fenced block. The language tag is ignored.
     */
    fun stripFences(response: String): String? {
        val match = FENCE.find(response) ?: return null
        return match.groupValues[1].trim()
    }

    /**
     * Returns the substring from `@startuml` to `@enduml` (inclusive), trimming
     * surrounding prose, or `null` when the response carries no `@startuml`.
     * An unclosed block returns everything from `@startuml` to the end.
     */
    fun rawPlantUml(response: String): String? {
        val start = response.indexOf(START)
        if (start < 0) return null
        val end = response.indexOf(END, start + START.length)
        return if (end < 0) response.substring(start) else response.substring(start, end + END.length)
    }
}
