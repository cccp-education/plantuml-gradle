package plantuml.scenarios

import contracts.context.CompositeContext
import contracts.context.CompositeContextConfig
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.context.ContextProvenance
import plantuml.context.PlantumlContextBuilder
import plantuml.context.ProvenanceSource
import plantuml.context.ProvenanceTracker

/**
 * Steps for `30_plantuml_provenance.feature` (EPIC PLT-CONTEXT, CTX-03).
 *
 * All step names carry the `provenance` wording so they stay unique within the
 * shared `plantuml.scenarios` glue package (anti-collision rule S-088).
 */
class PlantumlProvenanceSteps {

    private var tracker: ProvenanceTracker? = null
    private var provenance: ContextProvenance? = null

    @Given("a provenance tracker with EAGER source {string} and RAG source {string}")
    fun aTrackerWithEagerAndRagSources(eagerSource: String, ragSource: String) {
        val tracker = ProvenanceTracker()
        tracker.trackChannel(Channel.EAGER, listOf(source(eagerSource)))
        tracker.trackChannel(Channel.RAG, listOf(source(ragSource)))
        this.tracker = tracker
    }

    @When("the plantuml context is assembled from RAG {string} with the provenance tracker")
    fun theContextIsAssembledFromRag(rag: String) {
        val composite = CompositeContext(
            eagerSection = "",
            ragSection = rag,
            graphifySection = "",
            docsSection = "",
            config = CompositeContextConfig(),
        )
        PlantumlContextBuilder.build(composite = composite, tracker = tracker)
        provenance = tracker!!.build()
    }

    @Then("the provenance contains the channel {string}")
    fun theProvenanceContainsTheChannel(channel: String) {
        assertThat(provenance!!.channels.map { it.channel }).contains(channel)
    }

    @Then("the provenance does not contain the channel {string}")
    fun theProvenanceDoesNotContainTheChannel(channel: String) {
        assertThat(provenance!!.channels.map { it.channel }).doesNotContain(channel)
    }

    @Then("the provenance total character count is {int}")
    fun theProvenanceTotalCharacterCountIs(total: Int) {
        assertThat(provenance!!.totalChars).isEqualTo(total)
    }

    @Then("the provenance JSON contains {string}")
    fun theProvenanceJsonContains(fragment: String) {
        assertThat(provenance!!.toJson()).contains(fragment)
    }

    private fun source(fileName: String): ProvenanceSource =
        ProvenanceSource(fileName = fileName, chars = fileName.length, tokens = 1)

    private object Channel {
        const val EAGER = "EAGER"
        const val RAG = "RAG"
    }
}
