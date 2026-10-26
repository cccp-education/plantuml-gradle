package plantuml.boundary

/**
 * EPIC PLT-TERM-PROTECTION — the pure **term shield** of the PlantUML translation.
 *
 * A translation provider must translate the prose of a diagram label but never a
 * code/business token: a primitive type (`integer`, `String`), a file
 * (`opencode.json`, `AGENT.adoc`), a path, a Gradle option, an identifier
 * (`PlantumlManager`, `get_user`), a version, a colour, a backticked code span,
 * or a client term registered as non-translatable.
 *
 * The shield replaces each protected token with a stable placeholder
 * (`__PLT0__`, `__PLT1__`, …), lets the provider translate the remaining prose,
 * then restores the original tokens. If the provider drops a placeholder, the
 * label is **not** written: the caller preserves the source (never a silently
 * corrupted diagram). Pure function: text in, text out.
 *
 * The policy is deliberately narrow: common prose words are not shielded even
 * when they are also language keywords, so a sentence such as “the class of the
 * object” still translates. Only **type names** and **code-shaped tokens** are
 * protected by default.
 *
 * @param registry client-specific non-translatable terms (exact, word-bounded)
 * @param borrowedVocabulary terms the N0 block declares as borrowed verbatim
 */
class PlantumlTermShield(
    private val registry: NonTranslatableTermRegistry = NonTranslatableTermRegistry(),
    private val borrowedVocabulary: Set<String> = emptySet(),
) {

    /**
     * Shields every protected token of [text].
     *
     * @return the shielded text and the original tokens in placeholder order
     */
    fun shield(text: String): ShieldedText {
        if (text.isBlank()) return ShieldedText(text, emptyList())

        val candidates = mutableListOf<Candidate>()
        (registry.terms() + borrowedVocabulary + DOMAIN_TERMS).forEach { term ->
            candidates += occurrencesOf(text, term)
        }
        TERM_PATTERNS.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                candidates += Candidate(match.range.first, match.range.last + 1, match.value)
            }
        }

        val selected = selectNonOverlapping(candidates)
        if (selected.isEmpty()) return ShieldedText(text, emptyList())

        val shielded = StringBuilder(text.length)
        val terms = ArrayList<String>(selected.size)
        var cursor = 0
        selected.forEachIndexed { index, candidate ->
            shielded.append(text, cursor, candidate.start)
            shielded.append(placeholder(index))
            terms += text.substring(candidate.start, candidate.end)
            cursor = candidate.end
        }
        shielded.append(text, cursor, text.length)
        return ShieldedText(shielded.toString(), terms)
    }

    /**
     * Restores the protected tokens of [shield] in a provider [translated] text.
     *
     * @return the restored text, or `null` when the provider dropped/changed a
     *   placeholder — the caller must then preserve the source label
     */
    fun restore(
        translated: String,
        shield: ShieldedText,
    ): String? {
        if (!shield.hasTerms) return translated
        var restored = translated
        shield.terms.forEachIndexed { index, term ->
            val placeholder = placeholder(index)
            if (!restored.contains(placeholder)) return null
            restored = restored.replace(placeholder, term)
        }
        if (PLACEHOLDER.containsMatchIn(restored)) return null
        return restored
    }

    private fun occurrencesOf(
        text: String,
        term: String,
    ): List<Candidate> {
        if (term.isBlank()) return emptyList()
        val found = mutableListOf<Candidate>()
        var from = 0
        while (from <= text.length - term.length) {
            val start = text.indexOf(term, from)
            if (start < 0) break
            val end = start + term.length
            if (isWordBounded(text, start, end)) {
                found += Candidate(start, end, term)
            }
            from = start + 1
        }
        return found
    }

    /** Greedy non-overlapping selection: earliest start, longest token first. */
    private fun selectNonOverlapping(candidates: List<Candidate>): List<Candidate> {
        val sorted = candidates.sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))
        val selected = mutableListOf<Candidate>()
        var lastEnd = -1
        for (candidate in sorted) {
            if (candidate.start >= lastEnd) {
                selected += candidate
                lastEnd = candidate.end
            }
        }
        return selected
    }

    private fun isWordBounded(
        text: String,
        start: Int,
        end: Int,
    ): Boolean {
        val before = text.getOrNull(start - 1)
        val after = text.getOrNull(end)
        return !isIdentifierChar(before) && !isIdentifierChar(after)
    }

    private fun isIdentifierChar(character: Char?): Boolean =
        character != null && (character.isLetterOrDigit() || character == '_')

    private data class Candidate(
        val start: Int,
        val end: Int,
        @Suppress("unused") val original: String,
    )

    companion object {
        /** Placeholder shape — kept ASCII so providers keep it verbatim. */
        private val PLACEHOLDER = Regex("""__PLT(\d+)__""")

        fun placeholder(index: Int): String = "__PLT${index}__"

        /**
         * Primitive/type names that are code in a diagram, never prose. Common
         * words that are also keywords (`class`, `object`, `return`…) are
         * deliberately absent: shielding them would break a prose sentence.
         */
        val TYPE_TERMS: Set<String> =
            setOf(
                "integer", "int", "long", "short", "byte", "float", "double", "decimal",
                "boolean", "bool", "char", "void", "null", "true", "false",
                "string", "String", "Integer", "Long", "Short", "Byte", "Float", "Double",
                "Boolean", "Char", "Character", "Void", "Any", "Unit", "Nothing", "Number",
                "Object", "List", "Map", "Set", "Array", "Collection", "Optional", "Result",
                "Either", "Pair", "Triple", "Duration", "Instant", "LocalDate", "LocalDateTime",
                "OffsetDateTime", "ZonedDateTime", "UUID", "BigDecimal", "BigInteger",
                "json", "xml", "yaml", "toml",
            )

        /** Product/company names that must survive any translation. */
        val DOMAIN_TERMS: Set<String> =
            setOf(
                "cheroliv", "talaria", "edster", "cccp", "opencode", "PlantUML", "Gradle",
                "Kotlin", "Java", "GitHub", "Maven", "Ollama", "Firebase", "Supabase", "JBake",
                "AsciiDoc", "Asciidoctor", "Thymeleaf", "Playwright", "FFmpeg", "Remotion",
                "Manim", "Piper", "espeak", "Docker", "Kubernetes",
            )

        private val TERM_PATTERNS =
            listOf(
                // whole backticked code span
                Regex("""`[^`\n]+`"""),
                Regex("""https?://\S+"""),
                // relative/absolute paths
                Regex("""(?:\.{1,2}/|/)[\w./-]+"""),
                // file names (the extension makes it a code token)
                Regex(
                    """\b[\w.-]+\.(?:adoc|md|json|ya?ml|toml|gradle|kts|kt|java|xml|properties|""" +
                        """png|svg|mp4|webm|sh|txt|sql|html|css|js|ts|puml|pdf|epub)\b""",
                    RegexOption.IGNORE_CASE,
                ),
                // Gradle/JVM options
                Regex("""(?:--[A-Za-z][\w.-]*|-P[\w.]+(?:=\S+)?|-D[\w.]+(?:=\S+)?)"""),
                // annotations
                Regex("""@[A-Za-z]\w*"""),
                // dotted identifiers
                Regex("""\b[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+\b"""),
                // generic types
                Regex("""\b[A-Z]\w*<[^<>\n]+>"""),
                // function/method calls (`getUser()`, `call(x)`)
                Regex("""\b[A-Za-z_]\w*\([^()\n]*\)"""),
                // SCREAMING_SNAKE constants
                Regex("""\b[A-Z][A-Z0-9_]{2,}\b"""),
                // snake_case identifiers
                Regex("""\b[a-z]+(?:_[a-z0-9]+)+\b"""),
                // camelCase identifiers
                Regex("""\b[a-z]+(?:[A-Z][a-z0-9]+)+\b"""),
                // multi-hump PascalCase / mixed-case product identifiers
                Regex(
                    """\b(?=[A-Za-z0-9_]*[A-Z])(?=[A-Za-z0-9_]*[a-z])""" +
                        """[A-Za-z][A-Za-z0-9_]*[A-Z][A-Za-z0-9_]*\b""",
                ),
                // PlantUML colour tokens
                Regex("""<#[0-9A-Fa-f]{3,8}>"""),
                Regex("""#[0-9A-Fa-f]{3,8}\b"""),
                // version numbers
                Regex("""\b\d+(?:\.\d+)+\b"""),
            ) + TYPE_TERMS.map { term -> Regex("""\b${Regex.escape(term)}\b""") }
    }
}

/**
 * A text with its protected tokens replaced by placeholders.
 *
 * @property text the shielded text sent to the provider
 * @property terms the original tokens, in placeholder order
 */
data class ShieldedText(
    val text: String,
    val terms: List<String>,
) {
    val hasTerms: Boolean get() = terms.isNotEmpty()

    /**
     * True when the text carries no translatable prose: only placeholders,
     * whitespace and punctuation. Such a label is never sent to the provider.
     */
    val isFullyShielded: Boolean
        get() =
            hasTerms &&
                text
                    .replace(Regex("""__PLT\d+__"""), "")
                    .none { it.isLetterOrDigit() }
}
