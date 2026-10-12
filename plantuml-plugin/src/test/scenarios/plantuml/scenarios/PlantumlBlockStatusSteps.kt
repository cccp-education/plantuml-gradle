package plantuml.scenarios

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.repair.PlantumlBlockClassifier
import plantuml.repair.PlantumlBlockEntry
import plantuml.repair.PlantumlBlockReport
import plantuml.repair.PlantumlBlockStatus

/**
 * Steps for `32_plantuml_block_status.feature` (EPIC GBL-010, G-3b).
 *
 * All step names carry the `block status` / `plantuml block` / `report JSON`
 * wording so they stay unique within the shared `plantuml.scenarios` glue package
 * (anti-collision rule S-088).
 */
class PlantumlBlockStatusSteps {

    private var source: String = ""
    private var status: PlantumlBlockStatus? = null
    private var report: PlantumlBlockReport? = null

    @Given("a plantuml block source {string}")
    fun aPlantumlBlockSource(value: String) {
        source = value
    }

    @Given("a plantuml block with a broken quoted label")
    fun aPlantumlBlockWithABrokenQuotedLabel() {
        source = "@startuml\nrectangle \"a\nb\" as R\n@enduml"
    }

    @When("the block status is audited against an accepting oracle")
    fun auditedAgainstAnAcceptingOracle() {
        status = PlantumlBlockClassifier.classify(
            source,
            PlantUmlSyntaxValidator { SyntaxValidationResult.Valid },
        )
    }

    @When("the block status is audited against a rejecting oracle")
    fun auditedAgainstARejectingOracle() {
        status = PlantumlBlockClassifier.classify(
            source,
            PlantUmlSyntaxValidator { SyntaxValidationResult.Invalid("syntax error", "trace") },
        )
    }

    @Then("the block status is {string}")
    fun theBlockStatusIs(expected: String) {
        assertThat(status!!.name).isEqualTo(expected)
    }

    @When("the audited block is added to the report under ref {string}")
    fun theAuditedBlockIsAddedToTheReport(ref: String) {
        report = PlantumlBlockReport(blocks = listOf(PlantumlBlockEntry(ref, status!!)))
    }

    @Then("the report JSON contains the owner {string}")
    fun theReportJsonContainsTheOwner(owner: String) {
        assertThat(report!!.toJson()).contains(owner)
    }

    @Then("the report JSON exposes the toolchain version")
    fun theReportJsonExposesTheToolchainVersion() {
        val json = report!!.toJson()
        assertThat(json).contains("toolchain")
        assertThat(json).contains("engineVersion")
    }
}
