package plantuml.agent

/**
 * Verdict of the description pre-flight (EPIC PLT-AGENT, DIA-02).
 */
sealed interface PreflightVerdict {
    data object Valid : PreflightVerdict
    data class NeedsClarification(val questions: String) : PreflightVerdict
}

/**
 * Pre-flight validation of a diagram description before spending any generation
 * cycle (EPIC PLT-AGENT, DIA-02).
 *
 * Ported from `docToolchain/diag-agent`'s `validate_description`: the LLM checks the
 * description for completeness/consistency/clarity and returns clarifying questions
 * instead of a degraded diagram. **Fail-safe**: any malformed or unexpected LLM output
 * is treated as [PreflightVerdict.Valid], so the workflow never blocks on the validator.
 *
 * The object is pure (prompt building + response parsing); the LLM call lives at the
 * call site ([plantuml.tasks.PreflightPlantumlDescriptionTask]).
 */
object DescriptionPreflight {

    private const val VALID = "VALID"
    private const val INVALID = "INVALID"

    /**
     * Builds the validator prompt for the given description and optional hint
     * (e.g. the detected subtype). Instructs the model to answer `VALID`, or
     * `INVALID` followed by numbered questions.
     */
    fun prompt(description: String, diagramHint: String? = null): String = buildString {
        appendLine("You are a diagram description validator. Analyze the following description for a PlantUML diagram.")
        if (diagramHint != null) appendLine("Detected diagram kind: $diagramHint")
        appendLine("Check for:")
        appendLine("1. Completeness: are the essential elements specified?")
        appendLine("2. Consistency: are there contradictions?")
        appendLine("3. Clarity: is the description unambiguous?")
        appendLine("Only flag actual problems that would prevent a useful diagram. Be lenient.")
        appendLine("Response format:")
        appendLine("- If valid: return exactly \"VALID\"")
        appendLine("- If invalid: return \"INVALID\" on the first line, then numbered questions on separate lines.")
        appendLine()
        appendLine("Description to validate:")
        append(description)
    }

    /**
     * Parses the LLM response. Fail-safe: anything that is not an explicit `INVALID`
     * verdict yields [PreflightVerdict.Valid].
     */
    fun parse(response: String): PreflightVerdict {
        val content = response.trim()
        return when {
            content.startsWith(INVALID, ignoreCase = true) -> {
                val questions = content.substring(INVALID.length)
                    .trim()
                    .trimStart(':', ' ', '\n')
                    .trim()
                if (questions.isEmpty()) PreflightVerdict.Valid
                else PreflightVerdict.NeedsClarification(questions)
            }
            else -> PreflightVerdict.Valid
        }
    }
}
