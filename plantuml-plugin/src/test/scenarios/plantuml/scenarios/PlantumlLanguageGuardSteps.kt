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
 * Steps for `37_plantuml_language_guard.feature` (PLT-OUT-3).
 *
 * Step wording is prefixed with “language guard” so it stays unique in the shared
 * `plantuml.scenarios` glue package (anti-collision rule S-088).
 */
class PlantumlLanguageGuardSteps {
    private var source: String = ""
    private var sourceLanguage: String = "fr"
    private var targetLanguage: String = "en"
    private var answers: List<String> = emptyList()
    private var attempts: Int = 0
    private var outcome: PlantUmlTranslationOutcome? = null

    @Given("a language guard block {string}")
    fun aLanguageGuardBlock(value: String) {
        source = "@startuml\n$value\n@enduml"
        sourceLanguage = "fr"
        targetLanguage = "en"
    }

    @Given("a language guard English block {string}")
    fun aLanguageGuardEnglishBlock(value: String) {
        source = "@startuml\n$value\n@enduml"
        sourceLanguage = "en"
        targetLanguage = "fr"
    }

    @Given("the language guard translator always answers {string}")
    fun theLanguageGuardTranslatorAlwaysAnswers(answer: String) {
        answers = listOf(answer)
    }

    @Given("the language guard translator answers {string} then {string}")
    fun theLanguageGuardTranslatorAnswersThen(
        first: String,
        second: String,
    ) {
        answers = listOf(first, second)
    }

    @When("the language guard translates the block")
    fun theLanguageGuardTranslatesTheBlock() {
        val translator =
            object : TranslationService {
                override fun translate(request: TranslationRequest): TranslationResult {
                    val answer = answers[if (attempts < answers.size) attempts else answers.lastIndex]
                    attempts++
                    return TranslationResult.Success(answer)
                }
            }
        outcome =
            PlantumlTranslationPortAdapter(translator)
                .translate(PlantUmlTranslationRequest(PlantUmlBlock(source), sourceLanguage, targetLanguage))
    }

    @Then("the language guard block is preserved")
    fun theLanguageGuardBlockIsPreserved() {
        assertThat(outcome).isInstanceOf(PlantUmlTranslationOutcome.Preserved::class.java)
    }

    @Then("the language guard block contains {string}")
    fun theLanguageGuardBlockContains(fragment: String) {
        val translated = outcome as PlantUmlTranslationOutcome.Translated
        assertThat(translated.block.raw).contains(fragment)
    }

    @Then("the language guard used {int} provider attempts")
    fun theLanguageGuardUsedProviderAttempts(expected: Int) {
        assertThat(attempts).isEqualTo(expected)
    }

    @Then("the language guard reason mentions {string}")
    fun theLanguageGuardReasonMentions(fragment: String) {
        val preserved = outcome as PlantUmlTranslationOutcome.Preserved
        assertThat(preserved.reason).contains(fragment)
    }
}
