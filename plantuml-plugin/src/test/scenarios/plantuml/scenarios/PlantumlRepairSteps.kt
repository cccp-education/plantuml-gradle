package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.repair.PlantumlLabelIntegrity

/**
 * Steps for `27_plantuml_repair.feature` (GBL-010 / G-3).
 *
 * Prefix-free step names are unique to this feature (anti-glue collision rule
 * S-088 — the shared `plantuml.scenarios` glue package).
 */
class PlantumlRepairSteps(private val world: PlantumlWorld) {

    private var source: String = ""
    private var repaired: String = ""

    @Given("a PlantUML block whose quoted label spans a real newline")
    fun aBrokenBlock() {
        source = "@startuml\nrectangle \"a\nb\" as X\n@enduml"
    }

    @Given("a PlantUML block whose quoted label uses the escape")
    fun anIntactBlock() {
        source = "@startuml\nrectangle \"a\\nb\" as X\n@enduml"
    }

    @When("the label integrity repairs the block")
    fun repair() {
        repaired = PlantumlLabelIntegrity.repair(source)
    }

    @Then("the label integrity reports the block broken")
    fun reportsBroken() {
        assertThat(PlantumlLabelIntegrity.hasBrokenQuotedLabel(source)).isTrue
    }

    @Then("the label integrity reports the block intact")
    fun reportsIntact() {
        assertThat(PlantumlLabelIntegrity.hasBrokenQuotedLabel(source)).isFalse
    }

    @Then("the repaired block is intact")
    fun repairedIntact() {
        assertThat(PlantumlLabelIntegrity.hasBrokenQuotedLabel(repaired)).isFalse
    }

    @Then("the repaired block contains {string}")
    fun repairedContains(value: String) {
        assertThat(repaired).contains(value)
    }
}
