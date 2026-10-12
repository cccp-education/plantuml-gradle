package plantuml.scenarios

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.repair.PlantumlBlockCorrection
import plantuml.repair.PlantumlBlockCorrector
import plantuml.repair.PlantumlBlockRepairer
import plantuml.repair.PlantumlLabelIntegrity

/**
 * Steps for `33_plantuml_block_correction.feature` (EPIC GBL-010, G-3).
 *
 * All step names carry the `correction block` / `correction source` / `corrected
 * block` / `AI repairer` wording so they stay unique within the shared
 * `plantuml.scenarios` glue package (anti-collision rule S-088).
 */
class PlantumlBlockCorrectionSteps {

    private var source: String = ""
    private var repairer: PlantumlBlockRepairer? = null
    private var result: PlantumlBlockCorrection? = null

    @Given("a correction block with a broken quoted label")
    fun aCorrectionBlockWithABrokenQuotedLabel() {
        source = "@startuml\nrectangle \"a\nb\" as R\n@enduml"
    }

    @Given("a correction source {string}")
    fun aCorrectionSource(value: String) {
        source = value
    }

    @Given("an AI repairer proposing {string}")
    fun anAiRepairerProposing(proposal: String) {
        repairer = PlantumlBlockRepairer { proposal }
    }

    @When("the correction block is corrected against an accepting oracle")
    fun correctedAgainstAnAcceptingOracle() {
        result = PlantumlBlockCorrector.correct(
            source,
            PlantUmlSyntaxValidator { SyntaxValidationResult.Valid },
            repairer,
        )
    }

    @When("the correction block is corrected against a fix-only oracle")
    fun correctedAgainstAFixOnlyOracle() {
        val fixOnly = PlantUmlSyntaxValidator { code ->
            if (code.contains("FIXED")) SyntaxValidationResult.Valid
            else SyntaxValidationResult.Invalid("not fixed", "trace")
        }
        result = PlantumlBlockCorrector.correct(source, fixOnly, repairer)
    }

    @Then("the correction strategy is {string}")
    fun theCorrectionStrategyIs(expected: String) {
        assertThat(result!!.strategy.name).isEqualTo(expected)
    }

    @Then("the corrected block status is {string}")
    fun theCorrectedBlockStatusIs(expected: String) {
        assertThat(result!!.status.name).isEqualTo(expected)
    }

    @Then("the corrected block has no broken quoted label")
    fun theCorrectedBlockHasNoBrokenQuotedLabel() {
        assertThat(PlantumlLabelIntegrity.hasBrokenQuotedLabel(result!!.source)).isFalse()
    }
}
