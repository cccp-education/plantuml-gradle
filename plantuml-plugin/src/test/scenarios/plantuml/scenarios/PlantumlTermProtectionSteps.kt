package plantuml.scenarios

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationRequest
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.boundary.PlantumlTranslationPortAdapter

/**
 * Steps for `35_plantuml_term_protection.feature` (EPIC PLT-TERM-PROTECTION).
 *
 * Step wording is prefixed with “protected” / “term translator” so it stays unique
 * in the shared `plantuml.scenarios` glue package (anti-collision rule S-088).
 */
class PlantumlTermProtectionSteps {

    private var line: String = ""
    private var translations: Map<String, String> = emptyMap()
    private var outcome: PlantUmlTranslationOutcome? = null

    @Given("a protected sequence message {string}")
    fun aProtectedSequenceMessage(value: String) {
        line = value
    }

    @Given("a protected activity label {string}")
    fun aProtectedActivityLabel(value: String) {
        line = value
    }

    @Given("the term translator maps {string} to {string}")
    fun theTermTranslatorMaps(
        from: String,
        to: String,
    ) {
        translations = translations + (from to to)
    }

    @When("the block is translated with term protection")
    fun theBlockIsTranslatedWithTermProtection() {
        val translator =
            object : TranslationService {
                override fun translate(request: TranslationRequest): TranslationResult =
                    translations[request.sourceText]?.let { TranslationResult.Success(it) }
                        ?: TranslationResult.Failure("no entry")
            }
        outcome =
            PlantumlTranslationPortAdapter(translator)
                .translate(PlantUmlTranslationRequest(PlantUmlBlock("@startuml\n$line\n@enduml"), "fr", "en"))
    }

    @Then("the protected block contains {string}")
    fun theProtectedBlockContains(fragment: String) {
        val translated = outcome as PlantUmlTranslationOutcome.Translated
        assertThat(translated.block.raw).contains(fragment)
    }

    @Then("the block translation is preserved")
    fun theBlockTranslationIsPreserved() {
        assertThat(outcome).isInstanceOf(PlantUmlTranslationOutcome.Preserved::class.java)
    }
}
