package plantuml.repair

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [PlantumlBlockCorrector] (EPIC GBL-010, G-3).
 *
 * Baby-step TDD (RED → GREEN): the pure correction pipeline — deterministic first
 * (label integrity), then the optional AI seam, with the round-trip gate. No I/O,
 * no LLM, no rendering.
 */
class PlantumlBlockCorrectorTest {

    private val acceptAll: PlantUmlSyntaxValidator =
        PlantUmlSyntaxValidator { SyntaxValidationResult.Valid }

    private val rejectAll: PlantUmlSyntaxValidator =
        PlantUmlSyntaxValidator { SyntaxValidationResult.Invalid("syntax error", "trace") }

    /** Rejects any code that does not contain `FIXED` — models the LLM oracle. */
    private val onlyFixed: PlantUmlSyntaxValidator = PlantUmlSyntaxValidator { code ->
        if (code.contains("FIXED")) SyntaxValidationResult.Valid
        else SyntaxValidationResult.Invalid("not fixed", "trace")
    }

    private val classDiagram = "@startuml\nclass Car\n@enduml"
    private val brokenLabel = "@startuml\nrectangle \"a\nb\" as R\n@enduml"
    private val pendingBlock = "@startuml\nclass Broken\n@enduml"
    private val fixedBlock = "@startuml\nclass FIXED\n@enduml"

    @Test
    fun `a TRANSLATED block is preserved verbatim`() {
        val result = PlantumlBlockCorrector.correct(classDiagram, acceptAll)
        assertEquals(PlantumlCorrectionStrategy.PRESERVED, result.strategy)
        assertEquals(PlantumlBlockStatus.TRANSLATED, result.status)
        assertEquals(classDiagram, result.source)
        assertFalse(result.corrected)
    }

    @Test
    fun `a MISSING block is preserved verbatim`() {
        val result = PlantumlBlockCorrector.correct("   \n ", acceptAll)
        assertEquals(PlantumlCorrectionStrategy.PRESERVED, result.strategy)
        assertEquals(PlantumlBlockStatus.MISSING, result.status)
        assertFalse(result.corrected)
    }

    @Test
    fun `a broken quoted label is repaired deterministically`() {
        val result = PlantumlBlockCorrector.correct(brokenLabel, acceptAll)
        assertEquals(PlantumlCorrectionStrategy.DETERMINISTIC, result.strategy)
        assertEquals(PlantumlBlockStatus.TRANSLATED, result.status)
        assertTrue(result.corrected)
        assertFalse(PlantumlLabelIntegrity.hasBrokenQuotedLabel(result.source))
    }

    @Test
    fun `a broken quoted label is repaired even without a validator`() {
        val result = PlantumlBlockCorrector.correct(brokenLabel, validator = null)
        assertEquals(PlantumlCorrectionStrategy.DETERMINISTIC, result.strategy)
        assertFalse(PlantumlLabelIntegrity.hasBrokenQuotedLabel(result.source))
    }

    @Test
    fun `a PENDING block is corrected by the AI seam when it parses again`() {
        val repairer = PlantumlBlockRepairer { fixedBlock }
        val result = PlantumlBlockCorrector.correct(pendingBlock, onlyFixed, repairer)
        assertEquals(PlantumlCorrectionStrategy.AI, result.strategy)
        assertEquals(PlantumlBlockStatus.TRANSLATED, result.status)
        assertEquals(fixedBlock, result.source)
        assertTrue(result.corrected)
    }

    @Test
    fun `an AI correction that does not parse is discarded`() {
        val repairer = PlantumlBlockRepairer { "@startuml\nclass Still\n@enduml" }
        val result = PlantumlBlockCorrector.correct(pendingBlock, onlyFixed, repairer)
        assertEquals(PlantumlCorrectionStrategy.PRESERVED, result.strategy)
        assertEquals(PlantumlBlockStatus.PENDING, result.status)
        assertEquals(pendingBlock, result.source)
        assertFalse(result.corrected)
    }

    @Test
    fun `a PENDING block with no repairer is preserved`() {
        val result = PlantumlBlockCorrector.correct(pendingBlock, rejectAll)
        assertEquals(PlantumlCorrectionStrategy.PRESERVED, result.strategy)
        assertEquals(PlantumlBlockStatus.PENDING, result.status)
    }

    @Test
    fun `a null AI proposal is ignored`() {
        val repairer = PlantumlBlockRepairer { null }
        val result = PlantumlBlockCorrector.correct(pendingBlock, onlyFixed, repairer)
        assertEquals(PlantumlCorrectionStrategy.PRESERVED, result.strategy)
        assertEquals(PlantumlBlockStatus.PENDING, result.status)
    }

    @Test
    fun `without a validator the AI seam is not consulted`() {
        val repairer = PlantumlBlockRepairer { fixedBlock }
        val result = PlantumlBlockCorrector.correct(pendingBlock, validator = null, repairer = repairer)
        assertEquals(PlantumlCorrectionStrategy.PRESERVED, result.strategy)
        assertEquals(PlantumlBlockStatus.PENDING, result.status)
    }
}
