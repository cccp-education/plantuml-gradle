package plantuml.boundary

/**
 * EPIC PLT-TERM-PROTECTION — a translatable **unquoted** prose label of a
 * PlantUML block.
 *
 * @property line the complete physical line (the exact substring to rewrite)
 * @property text the prose part of the line (replaced in [line])
 */
data class PlantumlProseLabel(
    val line: String,
    val text: String,
)

/**
 * Extracts the unquoted prose labels that the N0 [contracts.plantuml.PlantUmlBlock.labels]
 * surface does not see, so a translated diagram is fully translated (not only
 * its quoted class labels and directives).
 *
 * Supported forms:
 *
 *  - sequence message: `A -> B : Lance le terminal`
 *  - activity label:   `:Créer le fichier;`
 *  - state description: `state Ready : Prêt`
 *  - use case:         `:Utilisateur: --> (Rédiger documentation)`
 *  - mindmap node:     `* Syntaxe`
 *
 * Deliberately conservative: quoted labels, directives (`title`…), notes and
 * `legend … endlegend` blocks are not returned (they have their own owner). A
 * code-only message is returned too; the term shield decides it has no prose
 * and skips the provider call. Pure: no I/O, no LLM, no rendering.
 */
object PlantumlProseLabelExtractor {

    /** `prefix -> suffix : message` (prefix without a colon, so notes are excluded). */
    private val SEQUENCE_MESSAGE =
        Regex("""^\s*(?<prefix>[^:]*?(?:->|<-|--|\.\.|==)[^:]*?)\s*:\s*(?<text>.+?)\s*$""")

    /** `:activity label;` — one physical line, `\n` escapes allowed inside. */
    private val ACTIVITY_LABEL = Regex("""^\s*:(?<text>.*);\s*$""")

    /** `state X : description` */
    private val STATE_DESCRIPTION = Regex("""^\s*state\s+\S+\s*:\s*(?<text>.+?)\s*$""")

    /** `:Actor: --> (Use case)` — the actor and the parenthesized use cases. */
    private val USE_CASE_ACTOR_LINE =
        Regex("""^\s*:\s*(?<actor>[^:\n]+?)\s*:\s*(?:-->|->|\.\.>).*""")

    /** `(Use case) ..> (Other case)` — the parenthesized use cases. */
    private val USE_CASE_LINE = Regex("""^\s*\([^)\n]+\)\s*(?:-->|->|\.\.>).*""")

    private val PARENTHESIZED = Regex("""\((?<name>[^)\n]+)\)""")

    /** Mindmap/WBS node line (`*`, `**`, `***`…). */
    private val MINDMAP_NODE = Regex("""^\s*\*+\s*(?<text>.+?)\s*$""")
    private val MINDMAP_START = Regex("""^\s*@start(?:mindmap|wbs)\b""")
    private val MINDMAP_END = Regex("""^\s*@end(?:mindmap|wbs)\b""")

    /** Lines that belong to another construct (quoted labels, directives, notes…). */
    private val SKIP_PREFIX =
        Regex(
            """^\s*(?:@|'|//|!|skinparam\b|title\b|header\b|footer\b|caption\b|note\b|""" +
                """end\s*note\b|legend\b|endlegend\b|class\b|interface\b|enum\b|object\b|""" +
                """package\b|component\b|actor\b|participant\b|database\b|rectangle\b|node\b|""" +
                """folder\b|frame\b|cloud\b|queue\b|usecase\b|boundary\b|control\b|entity\b|""" +
                """collections\b|artifact\b|card\b|storage\b|agent\b|start\b|stop\b|if\b|else\b|""" +
                """endif\b|partition\b|return\b|deactivate\b|activate\b|destroy\b|create\b|""" +
                """hide\b|show\b|remove\b|restore\b|\}|\{|#|\[)""",
        )

    /** The prose labels of [source], in document order, distinct. */
    fun occurrences(source: String): List<PlantumlProseLabel> {
        val found = mutableListOf<PlantumlProseLabel>()
        var inLegend = false
        var inMindmap = false

        for (line in source.lines()) {
            val trimmed = line.trim()
            if (MINDMAP_START.containsMatchIn(trimmed)) {
                inMindmap = true
                continue
            }
            if (inMindmap) {
                if (MINDMAP_END.containsMatchIn(trimmed)) {
                    inMindmap = false
                } else {
                    MINDMAP_NODE.find(line)?.groups?.get("text")?.value?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        found += PlantumlProseLabel(line, it)
                    }
                }
                continue
            }
            if (trimmed.startsWith("legend")) {
                inLegend = true
                continue
            }
            if (inLegend) {
                if (trimmed.startsWith("endlegend")) inLegend = false
                continue
            }

            val actor = USE_CASE_ACTOR_LINE.find(line)
            if (actor != null) {
                actor.groups["actor"]?.value?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    found += PlantumlProseLabel(line, it)
                }
                addParenthesized(line, found)
                continue
            }
            if (USE_CASE_LINE.containsMatchIn(trimmed)) {
                addParenthesized(line, found)
                // The relation label (`… : inclut`) is handled by the sequence path below.
            }

            val state = STATE_DESCRIPTION.find(line)
            if (state != null) {
                state.groups["text"]?.value?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    found += PlantumlProseLabel(line, it)
                }
                continue
            }

            if (SKIP_PREFIX.containsMatchIn(trimmed)) continue

            val sequence = SEQUENCE_MESSAGE.find(line)
            if (sequence != null) {
                val text = sequence.groups["text"]?.value?.trim().orEmpty()
                if (text.isNotEmpty() && !text.startsWith("\"")) {
                    found += PlantumlProseLabel(line, text)
                }
                continue
            }

            val activity = ACTIVITY_LABEL.find(line)
            if (activity != null) {
                activity.groups["text"]?.value?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    found += PlantumlProseLabel(line, it)
                }
            }
        }
        return found.distinctBy { it.line to it.text }
    }

    private fun addParenthesized(
        line: String,
        found: MutableList<PlantumlProseLabel>,
    ) {
        PARENTHESIZED.findAll(line).forEach { match ->
            match.groups["name"]?.value?.trim()?.takeIf { it.isNotEmpty() }?.let {
                found += PlantumlProseLabel(line, it)
            }
        }
    }

    /** The prose texts of [source], distinct and in document order. */
    fun texts(source: String): List<String> = occurrences(source).map { it.text }.distinct()
}
