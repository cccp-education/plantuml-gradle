package plantuml.scenarios

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.corpus.PlantumlCorpusGate
import plantuml.corpus.PlantumlCorpusReport

/**
 * Steps for `38_plantuml_corpus_language.feature` (PLT-OUT-4).
 *
 * Step wording is prefixed with “language corpus” / “corpus language” so it
 * stays unique in the shared `plantuml.scenarios` glue package (S-088).
 */
class PlantumlCorpusLanguageSteps {
    private var document: String = ""
    private var content: String = ""
    private var sourceLanguage: String = "fr"
    private var report: PlantumlCorpusReport? = null

    private val alwaysValid =
        object : PlantUmlSyntaxValidator {
            override fun validate(plantumlCode: String): SyntaxValidationResult = SyntaxValidationResult.Valid
        }

    @Given("a language corpus document {string} with body {string}")
    fun aLanguageCorpusDocumentWithBody(
        path: String,
        body: String,
    ) {
        document = path
        content =
            """
            [plantuml, diagram, svg]
            ----
            @startuml
            $body
            @enduml
            ----
            """.trimIndent()
    }

    @Given("the corpus language source is {string}")
    fun theCorpusLanguageSourceIs(source: String) {
        sourceLanguage = source
    }

    @When("the corpus language gate runs")
    fun theCorpusLanguageGateRuns() {
        report =
            PlantumlCorpusGate.evaluate(
                mapOf(document to content),
                alwaysValid,
                sourceLanguage = sourceLanguage,
            )
    }

    @Then("the corpus language report has {int} finding")
    fun theCorpusLanguageReportHasFinding(expected: Int) {
        assertThat(report!!.languageReport.findings).hasSize(expected)
    }

    @Then("the corpus language report has {int} findings")
    fun theCorpusLanguageReportHasFindings(expected: Int) {
        assertThat(report!!.languageReport.findings).hasSize(expected)
    }

    @Then("the corpus syntax gate is clean")
    fun theCorpusSyntaxGateIsClean() {
        assertThat(report!!.isClean).isTrue()
    }

    @Then("the corpus language finding has evidence {string}")
    fun theCorpusLanguageFindingHasEvidence(evidence: String) {
        val languageReport = report!!.languageReport
        assertThat(languageReport.findings.single().evidence).contains(evidence)
    }

    @Then("the corpus language report JSON names {string}")
    fun theCorpusLanguageReportJsonNames(expected: String) {
        assertThat(report!!.languageReport.toJson()).contains(expected)
    }

    @Then("the corpus language report JSON has a total of {int}")
    fun theCorpusLanguageReportJsonHasTotal(expected: Int) {
        assertThat(report!!.languageReport.toJson()).contains("\"total\" : $expected")
    }
}
