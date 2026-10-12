package plantuml.agent

/**
 * Verdict of a vision design review (EPIC PLT-AGENT, DIA-01).
 */
sealed interface DesignVerdict {
    data object Approved : DesignVerdict
    data class NeedsImprovement(val feedback: String) : DesignVerdict
}

/**
 * Parsing of a vision model's design feedback on a *rendered* diagram (EPIC PLT-AGENT, DIA-01).
 *
 * Ported from `docToolChain/diag-agent`'s design feedback loop: the rendered image is
 * submitted to a vision-capable model that judges layout/readability/spacing and either
 * approves or suggests improvements. This object only interprets the raw feedback, so it
 * stays pure and offline; the vision call lives in the adapter
 * ([plantuml.service.VisionDesignReviewer]).
 */
object DesignReview {

    /** Instruction sent to the vision model. Kept short and deterministic. */
    fun prompt(): String =
        "Analyze this diagram for layout quality, readability and spacing. " +
            "If the design is good, respond with exactly 'approved'. " +
            "Otherwise, provide specific, actionable improvement suggestions."

    /** Interprets the feedback: any mention of `approved` is an approval. */
    fun parse(feedback: String): DesignVerdict =
        if (feedback.lowercase().contains("approved")) DesignVerdict.Approved
        else DesignVerdict.NeedsImprovement(feedback.trim())
}
