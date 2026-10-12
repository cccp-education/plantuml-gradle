package plantuml.agent

/**
 * Convergence guard for the vision design loop (EPIC PLT-AGENT, DIA-07).
 *
 * Upstream `diag-agent` keeps re-calling the LLM + vision model until the iteration
 * limit even when the feedback no longer changes. This guard stops the loop as soon as
 * two consecutive reviews carry the same feedback (no progress), honouring the Loi de
 * l'Économie d'Encre: never burn an LLM/vision call that cannot change the outcome.
 */
object DesignConvergence {

    /** True when the current feedback is identical to the previous one (no progress). */
    fun isConverged(previous: String?, current: String): Boolean {
        if (previous == null) return false
        return previous.trim().equals(current.trim(), ignoreCase = true)
    }
}
