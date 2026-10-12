package plantuml.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * DIA-01 — vision design review parsing.
 */
class DesignReviewTest {

    @Test
    fun `approved feedback is Approved`() {
        assertThat(DesignReview.parse("approved")).isEqualTo(DesignVerdict.Approved)
    }

    @Test
    fun `approval is case-insensitive`() {
        assertThat(DesignReview.parse("Approved, looks good")).isEqualTo(DesignVerdict.Approved)
    }

    @Test
    fun `suggestions yield NeedsImprovement`() {
        val verdict = DesignReview.parse("Nodes overlap; add spacing.")
        assertThat(verdict).isInstanceOf(DesignVerdict.NeedsImprovement::class.java)
        assertThat((verdict as DesignVerdict.NeedsImprovement).feedback).contains("overlap")
    }

    @Test
    fun `prompt mentions approved`() {
        assertThat(DesignReview.prompt()).contains("approved")
    }
}
