package plantuml.scenarios

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import plantuml.corpus.PlantumlCorpusBaseline
import plantuml.corpus.PlantumlCorpusGate
import plantuml.corpus.PlantumlCorpusReport
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EPIC PLT-I18N-OUTPUT, PLT-OUT-1 — `@plantuml-corpus` scenarios.
 *
 * Step wording is prefixed with "corpus" so it stays unique in the shared
 * `plantuml.scenarios` glue package (anti-collision rule S-088). The validator
 * is a deterministic fake: the gate is what is under test, not the PlantUML
 * engine (already covered by `PlantumlSyntaxValidatorAdapterTest`).
 */
class PlantumlCorpusSteps {
    private val documents = linkedMapOf<String, String>()
    private var baseline = PlantumlCorpusBaseline.EMPTY
    private var report: PlantumlCorpusReport? = null

    private val validator =
        object : PlantUmlSyntaxValidator {
            override fun validate(plantumlCode: String): SyntaxValidationResult =
                if (plantumlCode.contains("BROKEN")) {
                    SyntaxValidationResult.Invalid("Broken diagram", "stack trace")
                } else {
                    SyntaxValidationResult.Valid
                }
        }

    @Given("a corpus document {string} with a valid diagram {string}")
    fun aCorpusDocumentWithAValidDiagram(
        document: String,
        id: String,
    ) {
        documents[document] = diagram(id, "A --> B")
    }

    @Given("a corpus document {string} with a broken diagram {string}")
    fun aCorpusDocumentWithABrokenDiagram(
        document: String,
        id: String,
    ) {
        documents[document] = diagram(id, "BROKEN")
    }

    @Given("a corpus document {string} with a source plantuml listing")
    fun aCorpusDocumentWithASourcePlantumlListing(document: String) {
        documents[document] =
            """
            = Article

            [source,plantuml]
            ----
            @startuml
            BROKEN
            @enduml
            ----
            """.trimIndent()
    }

    @Given("a corpus baseline accepting {string} block {string}")
    fun aCorpusBaselineAcceptingBlock(
        document: String,
        blockId: String,
    ) {
        baseline = PlantumlCorpusBaseline.fromLines(listOf("$document\t$blockId"))
    }

    @When("the corpus gate validates the documents")
    fun theCorpusGateValidatesTheDocuments() {
        report = PlantumlCorpusGate.evaluate(documents, validator, baseline)
    }

    @Then("the corpus report has {int} block and {int} blocking finding")
    fun theCorpusReportHasBlockAndBlockingFinding(
        blocks: Int,
        blocking: Int,
    ) {
        assertEquals(blocks, report().totalBlocks)
        assertEquals(blocking, report().blocking.size)
    }

    @Then("the corpus report has {int} blocking finding")
    fun theCorpusReportHasBlockingFinding(blocking: Int) {
        assertEquals(blocking, report().blocking.size)
    }

    @Then("the corpus report has {int} baselined finding")
    fun theCorpusReportHasBaselinedFinding(baselined: Int) {
        assertEquals(baselined, report().baselined.size)
    }

    @Then("the corpus report names the document {string}")
    fun theCorpusReportNamesTheDocument(document: String) {
        assertEquals(document, report().findings.single().document)
    }

    @Then("the corpus report names the block {string}")
    fun theCorpusReportNamesTheBlock(blockId: String) {
        assertEquals(blockId, report().findings.single().blockId)
    }

    @Then("the corpus report names the language {string}")
    fun theCorpusReportNamesTheLanguage(language: String) {
        assertEquals(language, report().findings.single().language)
    }

    @Then("the corpus report names the reason {string}")
    fun theCorpusReportNamesTheReason(reason: String) {
        assertEquals(reason, report().findings.single().reason)
        assertTrue(report().render().contains(reason))
    }

    @Then("the corpus findings are ordered by document")
    fun theCorpusFindingsAreOrderedByDocument() {
        assertEquals(listOf("en/b.adoc", "fr/a.adoc"), report().findings.map { it.document })
    }

    private fun report(): PlantumlCorpusReport = report ?: error("the corpus gate was not run")

    private fun diagram(
        id: String,
        body: String,
    ): String =
        """
        = Article

        [plantuml, $id, svg]
        ----
        @startuml
        $body
        @enduml
        ----
        """.trimIndent()
}
