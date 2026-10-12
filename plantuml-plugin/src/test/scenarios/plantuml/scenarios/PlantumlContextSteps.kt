package plantuml.scenarios

import contracts.context.CompositeContext
import contracts.context.CompositeContextConfig
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.context.PlantumlContext
import plantuml.context.PlantumlContextBuilder

/**
 * Steps for `29_plantuml_context.feature` (EPIC PLT-CONTEXT, CTX-02).
 *
 * All step names carry the `composite context` / `augmented context` / `plantuml context
 * builder` wording so they stay unique within the shared `plantuml.scenarios` glue
 * package (anti-collision rule S-088).
 */
class PlantumlContextSteps {

    private var composite: CompositeContext? = null
    private var context: PlantumlContext? = null

    @Given("a composite context with EAGER {string} and RAG {string}")
    fun aCompositeWithEagerAndRag(eager: String, rag: String) {
        composite = CompositeContext(eager, rag, "", "", CompositeContextConfig())
    }

    @Given("a composite context with RAG {string}")
    fun aCompositeWithRag(rag: String) {
        composite = CompositeContext("", rag, "", "", CompositeContextConfig())
    }

    @Given("an empty composite context")
    fun anEmptyComposite() {
        composite = CompositeContext("", "", "", "", CompositeContextConfig())
    }

    @When("the plantuml context builder assembles it")
    fun theBuilderAssemblesIt() {
        context = PlantumlContextBuilder.build(composite!!)
    }

    @Then("the augmented context contains {string}")
    fun theAugmentedContextContains(text: String) {
        assertThat(context!!.rendered).contains(text)
    }

    @Then("the augmented context has {int} non-empty channel")
    fun theAugmentedContextHasNonEmpty(count: Int) {
        assertThat(context!!.nonEmptyCount).isEqualTo(count)
    }

    @Then("the augmented context is empty")
    fun theAugmentedContextIsEmpty() {
        assertThat(context!!.isEmpty).isTrue()
    }
}
