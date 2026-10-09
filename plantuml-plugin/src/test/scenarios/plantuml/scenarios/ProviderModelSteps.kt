package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import plantuml.ApiKeyConfig
import plantuml.service.ProviderModelResolver

/**
 * Steps for `25_provider_model_config.feature` (PLT-CR3-1).
 *
 * Prefix-free step names are unique to this feature (anti-glue collision rule
 * S-088 — the shared `plantuml.scenarios` glue package).
 */
class ProviderModelSteps {

    private var huggingFaceConfig: ApiKeyConfig? = null
    private var groqConfig: ApiKeyConfig? = null

    @Given("a HuggingFace provider configured with model {string}")
    fun aHuggingFaceProviderConfiguredWith(model: String) {
        huggingFaceConfig = ApiKeyConfig(modelName = model)
    }

    @Given("a HuggingFace provider with no model configured")
    fun aHuggingFaceProviderWithNoModel() {
        huggingFaceConfig = ApiKeyConfig(modelName = "")
    }

    @Given("a Groq provider configured with model {string}")
    fun aGroqProviderConfiguredWith(model: String) {
        groqConfig = ApiKeyConfig(modelName = model)
    }

    @Given("a Groq provider with no model configured")
    fun aGroqProviderWithNoModel() {
        groqConfig = ApiKeyConfig(modelName = "")
    }

    @Then("the resolved HuggingFace model should be {string}")
    fun theResolvedHuggingFaceModelShouldBe(expected: String) {
        assertThat(ProviderModelResolver.huggingFace(huggingFaceConfig!!)).isEqualTo(expected)
    }

    @Then("the resolved Groq model should be {string}")
    fun theResolvedGroqModelShouldBe(expected: String) {
        assertThat(ProviderModelResolver.groq(groqConfig!!)).isEqualTo(expected)
    }
}
