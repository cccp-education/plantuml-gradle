package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.context.AugmentedContextPrompt

/**
 * Steps for `31_plantuml_context_wiring.feature` (EPIC PLT-CONTEXT, CTX-04).
 *
 * All step names carry the `composed prompt` / `generation prompt` wording so
 * they stay unique within the shared `plantuml.scenarios` glue package
 * (anti-collision rule S-088).
 */
class AugmentedContextPromptSteps {

    private var augmentedContext: String = ""
    private var composed: String = ""

    @Given("an augmented context {string}")
    fun anAugmentedContext(context: String) {
        augmentedContext = context
    }

    @Given("an empty augmented context")
    fun anEmptyAugmentedContext() {
        augmentedContext = ""
    }

    @When("the generation prompt {string} is composed")
    fun theGenerationPromptIsComposed(prompt: String) {
        composed = AugmentedContextPrompt.prepend(augmentedContext, prompt)
    }

    @Then("the composed prompt contains the context header")
    fun theComposedPromptContainsTheContextHeader() {
        assertThat(composed).contains(AugmentedContextPrompt.CONTEXT_HEADER)
    }

    @Then("the composed prompt does not contain the context header")
    fun theComposedPromptDoesNotContainTheContextHeader() {
        assertThat(composed).doesNotContain(AugmentedContextPrompt.CONTEXT_HEADER)
    }

    @Then("the composed prompt contains {string} before {string}")
    fun theComposedPromptContainsBefore(first: String, second: String) {
        assertThat(composed).contains(first)
        assertThat(composed).contains(second)
        assertThat(composed.indexOf(first)).isLessThan(composed.indexOf(second))
    }

    @Then("the composed prompt is {string}")
    fun theComposedPromptIs(expected: String) {
        assertThat(composed).isEqualTo(expected)
    }
}
