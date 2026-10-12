package plantuml.agent

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * DIA-03 — hard generation budget (iterations + wall-clock).
 */
class GenerationBudgetTest {

    @Test
    fun `continues while under both limits`() {
        val budget = GenerationBudget(maxIterations = 5, maxSeconds = 60)
        assertThat(budget.stopReason(0, 0.0)).isNull()
        assertThat(budget.stopReason(4, 59.0)).isNull()
    }

    @Test
    fun `stops at max iterations`() {
        val budget = GenerationBudget(maxIterations = 5, maxSeconds = 0)
        assertThat(budget.stopReason(5, 1.0)).isEqualTo(GenerationStopReason.MAX_ITERATIONS)
    }

    @Test
    fun `stops at max time`() {
        val budget = GenerationBudget(maxIterations = 10, maxSeconds = 30)
        assertThat(budget.stopReason(1, 30.0)).isEqualTo(GenerationStopReason.MAX_TIME)
    }

    @Test
    fun `zero seconds means no wall-clock limit`() {
        val budget = GenerationBudget(maxIterations = 3, maxSeconds = 0)
        assertThat(budget.stopReason(1, 99999.0)).isNull()
        assertThat(budget.stopReason(3, 0.0)).isEqualTo(GenerationStopReason.MAX_ITERATIONS)
    }

    @Test
    fun `iteration limit takes precedence over time`() {
        val budget = GenerationBudget(maxIterations = 2, maxSeconds = 1)
        assertThat(budget.stopReason(2, 5.0)).isEqualTo(GenerationStopReason.MAX_ITERATIONS)
    }

    @Test
    fun `rejects negative parameters`() {
        assertThatThrownBy { GenerationBudget(-1, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { GenerationBudget(1, -1) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
