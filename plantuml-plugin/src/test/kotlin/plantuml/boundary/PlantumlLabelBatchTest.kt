package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * CHE-DIAGRAM D2 (S-073) — the label batching sentinel.
 */
class PlantumlLabelBatchTest {

    @Test
    fun `join then split round-trips`() {
        val labels = listOf("A", "B", "C")

        assertEquals(labels, PlantumlLabelBatch.split(PlantumlLabelBatch.join(labels), 3))
    }

    @Test
    fun `split returns null when the sentinel is missing`() {
        assertNull(PlantumlLabelBatch.split("A B C", 3))
    }

    @Test
    fun `split returns null on a count mismatch`() {
        assertNull(PlantumlLabelBatch.split("A\n${PlantumlLabelBatch.SEPARATOR}\nB", 3))
    }

    @Test
    fun `a single label is never a batch`() {
        assertNull(PlantumlLabelBatch.split("A", 1))
    }

    @Test
    fun `split trims each part`() {
        assertEquals(
            listOf("A", "B"),
            PlantumlLabelBatch.split(" A \n${PlantumlLabelBatch.SEPARATOR}\n B \n", 2),
        )
    }
}
