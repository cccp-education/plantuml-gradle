package plantuml.corpus

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlantumlCorpusGateTest {
    private val validator =
        object : PlantUmlSyntaxValidator {
            override fun validate(plantumlCode: String): SyntaxValidationResult =
                if (plantumlCode.contains("BROKEN")) {
                    SyntaxValidationResult.Invalid("Broken diagram", "stack trace")
                } else {
                    SyntaxValidationResult.Valid
                }
        }

    private fun document(
        id: String,
        body: String = "A --> B",
    ): String =
        """
        [plantuml, $id, svg]
        ----
        @startuml
        $body
        @enduml
        ----
        """.trimIndent()

    @Test
    fun `a valid corpus is clean`() {
        val report =
            PlantumlCorpusGate.evaluate(
                mapOf("fr/article.adoc" to document("diagram")),
                validator,
            )

        assertTrue(report.isClean)
        assertEquals(1, report.totalDocuments)
        assertEquals(1, report.totalBlocks)
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun `a broken diagram is a blocking finding named by article id and reason`() {
        val report =
            PlantumlCorpusGate.evaluate(
                mapOf("en/article.adoc" to document("diagram-2", "BROKEN")),
                validator,
            )

        assertFalse(report.isClean)
        val finding = report.blocking.single()
        assertEquals("en/article.adoc", finding.document)
        assertEquals("en", finding.language)
        assertEquals("diagram-2", finding.blockId)
        assertEquals("Broken diagram", finding.reason)
    }

    @Test
    fun `the report renders language article id line and reason`() {
        val report =
            PlantumlCorpusGate.evaluate(
                mapOf("en/article.adoc" to document("diagram-2", "BROKEN")),
                validator,
            )

        val rendered = report.render()
        assertTrue(rendered.contains("en/article.adoc [en] diagram-2:1 — Broken diagram"))
        assertTrue(rendered.contains("blocking: 1"))
    }

    @Test
    fun `an exact baseline entry tolerates the finding`() {
        val document = document("diagram-2", "BROKEN")
        val baseline =
            PlantumlCorpusBaseline.fromLines(
                listOf("en/article.adoc\tdiagram-2\tBroken diagram"),
            )

        val report = PlantumlCorpusGate.evaluate(mapOf("en/article.adoc" to document), validator, baseline)

        assertTrue(report.isClean)
        assertEquals(1, report.baselined.size)
        assertTrue(report.blocking.isEmpty())
    }

    @Test
    fun `a document and block baseline entry tolerates any reason`() {
        val document = document("diagram-2", "BROKEN")
        val baseline = PlantumlCorpusBaseline.fromLines(listOf("en/article.adoc\tdiagram-2"))

        val report = PlantumlCorpusGate.evaluate(mapOf("en/article.adoc" to document), validator, baseline)

        assertTrue(report.isClean)
        assertEquals(1, report.baselined.size)
    }

    @Test
    fun `a new finding is not masked by a baseline for another block`() {
        val document =
            """
            [plantuml, known, svg]
            ----
            @startuml
            BROKEN
            @enduml
            ----

            [plantuml, new, svg]
            ----
            @startuml
            BROKEN
            @enduml
            ----
            """.trimIndent()
        val baseline = PlantumlCorpusBaseline.fromLines(listOf("en/article.adoc\tknown"))

        val report = PlantumlCorpusGate.evaluate(mapOf("en/article.adoc" to document), validator, baseline)

        assertFalse(report.isClean)
        assertEquals("new", report.blocking.single().blockId)
        assertEquals(1, report.baselined.size)
    }

    @Test
    fun `findings are ordered by document then line`() {
        val report =
            PlantumlCorpusGate.evaluate(
                linkedMapOf(
                    "fr/a.adoc" to document("a", "BROKEN"),
                    "en/b.adoc" to document("b", "BROKEN"),
                ),
                validator,
            )

        assertEquals(listOf("en/b.adoc", "fr/a.adoc"), report.findings.map { it.document })
    }

    @Test
    fun `a validator failure becomes a blocking finding`() {
        val failing =
            object : PlantUmlSyntaxValidator {
                override fun validate(plantumlCode: String): SyntaxValidationResult = error("engine unavailable")
            }

        val report =
            PlantumlCorpusGate.evaluate(
                mapOf("fr/article.adoc" to document("diagram")),
                failing,
            )

        assertFalse(report.isClean)
        assertEquals("engine unavailable", report.blocking.single().reason)
    }

    @Test
    fun `a source listing is not validated`() {
        val content =
            """
            [source,plantuml]
            ----
            @startuml
            BROKEN
            @enduml
            ----
            """.trimIndent()

        val report = PlantumlCorpusGate.evaluate(mapOf("fr/article.adoc" to content), validator)

        assertTrue(report.isClean)
        assertEquals(0, report.totalBlocks)
    }

    @Test
    fun `baseline parsing ignores comments and blank lines`() {
        val baseline =
            PlantumlCorpusBaseline.fromLines(
                listOf("", "# known debt", "en/article.adoc\tdiagram-2\tBroken diagram"),
            )

        val finding = PlantumlCorpusFinding("en/article.adoc", "en", "diagram-2", 1, "Broken diagram")
        assertTrue(baseline.accepts(finding))
    }
}
