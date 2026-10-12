package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.agent.DescriptionPreflight
import plantuml.agent.DesignConvergence
import plantuml.agent.DesignReview
import plantuml.agent.DesignVerdict
import plantuml.agent.DiagramExampleCatalog
import plantuml.agent.DiagramSubtypeDetector
import plantuml.agent.GenerationBudget
import plantuml.agent.GenerationStopReason
import plantuml.agent.LlmCodeExtractor
import plantuml.agent.PreflightVerdict

/**
 * Steps for `28_agent_loop.feature` (EPIC PLT-AGENT).
 *
 * All step names carry the `LLM code extractor` / `the LLM response` /
 * `the extracted code` prefixes so they stay unique within the shared
 * `plantuml.scenarios` glue package (anti-collision rule S-088).
 */
class AgentLoopSteps {

    private var response: String = ""
    private var extracted: String? = null
    private var budget: GenerationBudget? = null
    private var stopReason: GenerationStopReason? = null
    private var subtype: DiagramSubtypeDetector.Subtype? = null
    private var preflightVerdict: PreflightVerdict? = null
    private var designVerdict: DesignVerdict? = null
    private var converged: Boolean? = null

    private fun unescape(raw: String): String = raw.replace("\\n", "\n")

    @Given("the LLM response is {string}")
    fun theLlmResponseIs(raw: String) {
        response = unescape(raw)
        extracted = null
    }

    @When("the LLM code extractor strips fences")
    fun theExtractorStripsFences() {
        extracted = LlmCodeExtractor.stripFences(response)
    }

    @When("the LLM code extractor cuts raw PlantUML")
    fun theExtractorCutsRawPlantUml() {
        extracted = LlmCodeExtractor.rawPlantUml(response)
    }

    @Then("the extracted code is {string}")
    fun theExtractedCodeIs(raw: String) {
        assertThat(extracted).isEqualTo(unescape(raw))
    }

    @Given("a generation budget of {string} iterations and {string} seconds")
    fun aGenerationBudget(iterations: String, seconds: String) {
        budget = GenerationBudget(iterations.toInt(), seconds.toLong())
        stopReason = null
    }

    @When("{string} corrections have been done after {string} seconds")
    fun correctionsHaveBeenDone(corrections: String, seconds: String) {
        stopReason = budget!!.stopReason(corrections.toInt(), seconds.toDouble())
    }

    @Then("the budget stop reason is {string}")
    fun theBudgetStopReasonIs(expected: String) {
        if (expected == "none") {
            assertThat(stopReason).isNull()
        } else {
            assertThat(stopReason?.name).isEqualTo(expected)
        }
    }

    @Given("the diagram description {string}")
    fun theDiagramDescription(description: String) {
        subtype = DiagramSubtypeDetector.detect(description)
    }

    @Then("the detected subtype is {string}")
    fun theDetectedSubtypeIs(expected: String) {
        assertThat(subtype?.name).isEqualTo(expected)
    }

    @Given("the pre-flight LLM response is {string}")
    fun thePreflightResponseIs(raw: String) {
        preflightVerdict = DescriptionPreflight.parse(unescape(raw))
    }

    @Then("the pre-flight verdict is Valid")
    fun thePreflightVerdictIsValid() {
        assertThat(preflightVerdict).isEqualTo(PreflightVerdict.Valid)
    }

    @Then("the pre-flight verdict is NeedsClarification containing {string}")
    fun thePreflightVerdictNeedsClarification(text: String) {
        assertThat(preflightVerdict).isInstanceOf(PreflightVerdict.NeedsClarification::class.java)
        assertThat((preflightVerdict as PreflightVerdict.NeedsClarification).questions).contains(text)
    }

    @Then("the example catalog returns a PlantUML example")
    fun theExampleCatalogReturnsAnExample() {
        val example = DiagramExampleCatalog.selectExample(subtype!!)
        assertThat(example).isNotNull
        assertThat(example).contains("@startuml")
    }

    @Then("the example catalog returns no example")
    fun theExampleCatalogReturnsNoExample() {
        assertThat(DiagramExampleCatalog.selectExample(subtype!!)).isNull()
    }

    @Given("the vision design feedback {string}")
    fun theVisionDesignFeedback(feedback: String) {
        designVerdict = DesignReview.parse(feedback)
    }

    @Then("the design verdict is Approved")
    fun theDesignVerdictIsApproved() {
        assertThat(designVerdict).isEqualTo(DesignVerdict.Approved)
    }

    @Then("the design verdict is NeedsImprovement containing {string}")
    fun theDesignVerdictNeedsImprovement(text: String) {
        assertThat(designVerdict).isInstanceOf(DesignVerdict.NeedsImprovement::class.java)
        assertThat((designVerdict as DesignVerdict.NeedsImprovement).feedback).contains(text)
    }

    @Given("the previous design feedback {string} and the current {string}")
    fun thePreviousAndCurrentDesignFeedback(previous: String, current: String) {
        converged = DesignConvergence.isConverged(previous, current)
    }

    @Then("the design review has converged")
    fun theDesignReviewHasConverged() {
        assertThat(converged).isTrue()
    }

    @Then("the design review has not converged")
    fun theDesignReviewHasNotConverged() {
        assertThat(converged).isFalse()
    }
}
