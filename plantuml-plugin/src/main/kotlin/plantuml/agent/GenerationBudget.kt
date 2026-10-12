package plantuml.agent

/**
 * Reason the generation loop stopped short of success (EPIC PLT-AGENT, DIA-03).
 */
enum class GenerationStopReason { MAX_ITERATIONS, MAX_TIME }

/**
 * Hard generation budget (EPIC PLT-AGENT, DIA-03).
 *
 * Upstream `diag-agent` checks its wall-clock limit only at the top of the loop,
 * so a single slow LLM/vision call can blow far past the budget; and its two loops
 * disagree on the iteration count. This value object centralises the decision; it is
 * consulted before each expensive correction so the budget is authoritative.
 *
 * `maxSeconds == 0` means "no wall-clock limit" (backward-compatible default). The
 * correction-count semantics match the existing `DiagramProcessor` loop: `corrections`
 * is the number of corrections already performed, and the loop stops once it reaches
 * `maxIterations` (total attempts = 1 initial + maxIterations corrections).
 */
data class GenerationBudget(
    val maxIterations: Int,
    val maxSeconds: Long,
) {
    init {
        require(maxIterations >= 0) { "maxIterations must be >= 0" }
        require(maxSeconds >= 0) { "maxSeconds must be >= 0" }
    }

    /**
     * Given the number of corrections already performed and the elapsed seconds,
     * returns the reason to stop, or `null` to continue. The iteration limit takes
     * precedence (deterministic, checked first).
     */
    fun stopReason(corrections: Int, elapsedSeconds: Double): GenerationStopReason? = when {
        corrections >= maxIterations -> GenerationStopReason.MAX_ITERATIONS
        maxSeconds > 0 && elapsedSeconds >= maxSeconds -> GenerationStopReason.MAX_TIME
        else -> null
    }
}
