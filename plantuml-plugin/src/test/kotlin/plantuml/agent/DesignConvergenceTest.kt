package plantuml.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * DIA-07 — design feedback convergence guard.
 */
class DesignConvergenceTest {

    @Test
    fun `no previous feedback is not converged`() {
        assertThat(DesignConvergence.isConverged(null, "add spacing")).isFalse()
    }

    @Test
    fun `identical feedback is converged`() {
        assertThat(DesignConvergence.isConverged("add spacing", "add spacing")).isTrue()
    }

    @Test
    fun `comparison is case and whitespace insensitive`() {
        assertThat(DesignConvergence.isConverged(" Add Spacing ", "add spacing")).isTrue()
    }

    @Test
    fun `different feedback does not converge`() {
        assertThat(DesignConvergence.isConverged("add spacing", "reduce labels")).isFalse()
    }
}
