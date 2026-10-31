package plantuml.boundary

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MEM-I18N-PRESERVE / PRESERVE-2 — the shared vectors of the "never translate"
 * contract, replayed by plantuml.
 *
 * The vectors file is **byte-identical** to document's
 * (`document-plugin/src/test/resources/document/translation/preserve-vectors.tsv`):
 * two consumers implement the same N0 rule on two formats; a divergence is the
 * drift this guard exists for. The drift test is skipped when the sibling
 * checkout is absent (isolated CI), never silently passed.
 */
class PreserveVectorsTest {

    private val vectors: List<Vector> = loadVectors()

    @Test
    fun `plantuml shields every N0 marker of the shared vectors`() {
        val markers = vectors.filter { it.fullyPreserved }

        assertTrue(markers.size >= 8, "the vectors must cover every rule")
        markers.forEach { vector ->
            val shielded = PlantumlTermShield().shield(vector.text)
            assertEquals(listOf(vector.text), shielded.terms, vector.id)
        }
    }

    @Test
    fun `prose is never fully shielded`() {
        vectors.filterNot { it.fullyPreserved }.forEach { vector ->
            val shielded = PlantumlTermShield().shield(vector.text)
            assertEquals(false, shielded.isFullyShielded, vector.id)
        }
    }

    @Test
    fun `the vectors file is byte-identical to the document one`() {
        val sibling = siblingVectors()
        assumeTrue(sibling != null) { "document checkout absent (isolated CI) — drift guard skipped" }

        assertEquals(sibling!!.readText(), vectorsFile().readText(), "preserve-vectors.tsv drifted from document")
    }

    private fun siblingVectors(): File? =
        File("../../document-gradle/document-plugin/src/test/resources/document/translation/preserve-vectors.tsv")
            .takeIf { it.isFile }

    private fun loadVectors(): List<Vector> =
        vectorsFile().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split('\t')
                Vector(parts[0], parts[1].toBoolean(), parts.drop(2).joinToString("\t"))
            }

    private fun vectorsFile(): File {
        val url = javaClass.classLoader.getResource("plantuml/boundary/preserve-vectors.tsv")
            ?: error("preserve-vectors.tsv missing")
        return File(url.toURI())
    }

    private data class Vector(
        val id: String,
        val fullyPreserved: Boolean,
        val text: String,
    )
}
