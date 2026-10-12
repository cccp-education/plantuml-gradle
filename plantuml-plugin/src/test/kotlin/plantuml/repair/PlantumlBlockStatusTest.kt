package plantuml.repair

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the plantuml block-status leaf (EPIC GBL-010, G-3b).
 *
 * Baby-step TDD (RED → GREEN): [PlantumlBlockClassifier], [PlantumlBlockEntry] and
 * [PlantumlBlockReport] are the pure classification + report types exposed to the
 * unified translation tree (G-4). No I/O, no LLM, no rendering.
 */
class PlantumlBlockStatusTest {

    private val mapper: ObjectMapper = ObjectMapper()

    private val valid: PlantUmlSyntaxValidator =
        PlantUmlSyntaxValidator { SyntaxValidationResult.Valid }

    private val invalid: PlantUmlSyntaxValidator =
        PlantUmlSyntaxValidator { SyntaxValidationResult.Invalid("syntax error", "trace") }

    private val classDiagram = "@startuml\nclass Car\n@enduml"

    private val brokenLabel = "@startuml\nrectangle \"a\nb\" as R\n@enduml"

    // ─── PlantumlBlockClassifier ─────────────────────────────────────────

    @Test
    fun `a blank source is MISSING`() {
        assertEquals(PlantumlBlockStatus.MISSING, PlantumlBlockClassifier.classify("   \n  ", valid))
    }

    @Test
    fun `a valid block is TRANSLATED`() {
        assertEquals(PlantumlBlockStatus.TRANSLATED, PlantumlBlockClassifier.classify(classDiagram, valid))
    }

    @Test
    fun `an invalid block is PENDING`() {
        assertEquals(PlantumlBlockStatus.PENDING, PlantumlBlockClassifier.classify(classDiagram, invalid))
    }

    @Test
    fun `a block whose quoted label spans a raw newline is BROKEN`() {
        assertEquals(PlantumlBlockStatus.BROKEN, PlantumlBlockClassifier.classify(brokenLabel, valid))
    }

    @Test
    fun `BROKEN takes precedence over a valid syntax oracle`() {
        // The D2 defect is structural: even a permissive oracle must not mask it.
        assertEquals(PlantumlBlockStatus.BROKEN, PlantumlBlockClassifier.classify(brokenLabel, valid))
    }

    @Test
    fun `without a validator a non-blank block is PENDING`() {
        assertEquals(PlantumlBlockStatus.PENDING, PlantumlBlockClassifier.classify(classDiagram, null))
    }

    // ─── PlantumlBlockEntry ──────────────────────────────────────────────

    @Test
    fun `an entry defaults its owner to plantuml`() {
        val entry = PlantumlBlockEntry(ref = "diagram.adoc#b1", status = PlantumlBlockStatus.TRANSLATED)
        assertEquals(PlantumlBlockEntry.OWNER, entry.owner)
        assertEquals("plantuml", entry.owner)
    }

    @Test
    fun `an entry rejects a blank ref`() {
        assertThrows<IllegalArgumentException> {
            PlantumlBlockEntry(ref = "  ", status = PlantumlBlockStatus.TRANSLATED)
        }
    }

    // ─── PlantumlBlockReport ─────────────────────────────────────────────

    @Test
    fun `an empty report has zero total and zero counts`() {
        val report = PlantumlBlockReport()
        assertEquals(0, report.total)
        assertTrue(report.counts.values.all { it == 0 })
    }

    @Test
    fun `the report counts blocks per status`() {
        val report = PlantumlBlockReport(
            blocks = listOf(
                PlantumlBlockEntry("a", PlantumlBlockStatus.TRANSLATED),
                PlantumlBlockEntry("b", PlantumlBlockStatus.TRANSLATED),
                PlantumlBlockEntry("c", PlantumlBlockStatus.BROKEN),
                PlantumlBlockEntry("d", PlantumlBlockStatus.PENDING),
            ),
        )
        assertEquals(4, report.total)
        assertEquals(2, report.count(PlantumlBlockStatus.TRANSLATED))
        assertEquals(1, report.count(PlantumlBlockStatus.BROKEN))
        assertEquals(1, report.count(PlantumlBlockStatus.PENDING))
        assertEquals(0, report.count(PlantumlBlockStatus.MISSING))
    }

    @Test
    fun `the report serialises as JSON with owner, toolchain and blocks`() {
        val report = PlantumlBlockReport(
            blocks = listOf(PlantumlBlockEntry("diagram-1", PlantumlBlockStatus.BROKEN)),
        )
        val json: JsonNode = mapper.readTree(report.toJson())

        assertEquals("plantuml", json["owner"].asText())
        assertEquals(1, json["total"].asInt())
        assertEquals(1, json["blocks"].size())
        assertEquals("diagram-1", json["blocks"][0]["ref"].asText())
        assertEquals("BROKEN", json["blocks"][0]["status"].asText())
        assertEquals("plantuml", json["blocks"][0]["owner"].asText())
        // The reference toolchain version is exposed alongside the statuses (D6).
        assertTrue(json["toolchain"]["engineVersion"].asText().isNotBlank())
        assertTrue(json["toolchain"]["diagramVersion"].asText().isNotBlank())
        assertTrue(json["toolchain"]["diagramPlantumlVersion"].asText().isNotBlank())
        assertEquals(1, json["counts"]["BROKEN"].asInt())
    }
}
