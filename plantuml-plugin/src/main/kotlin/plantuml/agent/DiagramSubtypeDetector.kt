package plantuml.agent

/**
 * Deterministic detection of the PlantUML diagram subtype from the natural-language
 * description (EPIC PLT-AGENT, DIA-06).
 *
 * Ported from `docToolchain/diag-agent`'s LLM-based `_detect_subtype`, but made
 * deterministic and offline: keyword matching runs first, so no LLM call is needed
 * in the common case (Loi de l'Économie d'Encre). The result steers the generation
 * prompt; an LLM fallback can override it at the call site when the description is
 * ambiguous.
 */
object DiagramSubtypeDetector {

    enum class Subtype { CONTEXT, CONTAINER, COMPONENT, SEQUENCE, ACTIVITY, CLASS, UNKNOWN }

    private val RULES: List<Pair<Subtype, Regex>> = listOf(
        Subtype.SEQUENCE to Regex("(?i)\\bsequence\\b"),
        Subtype.CONTEXT to Regex("(?i)\\bc4\\b[^\\n]*\\bcontext\\b|\\bcontext diagram\\b"),
        Subtype.CONTAINER to Regex("(?i)\\bcontainer\\b"),
        Subtype.COMPONENT to Regex("(?i)\\bcomponent\\b"),
        Subtype.ACTIVITY to Regex("(?i)\\bactivity\\b|\\bworkflow\\b|\\bflow ?chart\\b"),
        Subtype.CLASS to Regex("(?i)\\bclass diagram\\b|\\buml class\\b"),
    )

    /** Returns the first matching subtype, or [Subtype.UNKNOWN]. */
    fun detect(description: String): Subtype =
        RULES.firstOrNull { it.second.containsMatchIn(description) }?.first ?: Subtype.UNKNOWN

    /** A short English hint to steer the LLM, or `null` when the subtype is unknown. */
    fun hint(description: String): String? = when (val subtype = detect(description)) {
        Subtype.UNKNOWN -> null
        else -> "Prefer a ${subtype.name.lowercase()} diagram."
    }
}
