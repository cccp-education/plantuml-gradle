package plantuml.scenarios

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationRequest
import contracts.plantuml.SyntaxValidationResult
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.boundary.PlantumlTranslationPortAdapter

/**
 * Steps for `23_plantuml_roundtrip.feature` (EPIC PLT-DIAGRAM-OWNERSHIP US-3).
 *
 * Prefix-free step names are unique to this feature (anti-glue collision rule
 * S-088 — the shared `plantuml.scenarios` glue package).
 */
class RoundTripSteps(private val world: PlantumlWorld) {

    private var block: PlantUmlBlock? = null
    private var provider: TranslationService? = null
    private var validator: PlantUmlSyntaxValidator? = null

    @Given("a PlantUML block with a quoted label {string}")
    fun aPlantUmlBlockWithQuotedLabel(label: String) {
        block = PlantUmlBlock("@startuml\nclass \"$label\"\n@enduml")
    }

    @Given("a label provider returning {string} for {string}")
    fun aLabelProviderReturning(replacement: String, source: String) {
        val decoded = replacement.replace("\\n", "\n")
        provider =
            object : TranslationService {
                override fun translate(request: TranslationRequest): TranslationResult =
                    if (request.sourceText == source) {
                        TranslationResult.Success(decoded)
                    } else {
                        TranslationResult.Failure("no entry")
                    }
            }
    }

    @Given("a syntax validator rejecting the translated block")
    fun aSyntaxValidatorRejecting() {
        validator =
            object : PlantUmlSyntaxValidator {
                override fun validate(plantumlCode: String): SyntaxValidationResult =
                    SyntaxValidationResult.Invalid("broken", "")
            }
    }

    @Given("a syntax validator accepting the translated block")
    fun aSyntaxValidatorAccepting() {
        validator =
            object : PlantUmlSyntaxValidator {
                override fun validate(plantumlCode: String): SyntaxValidationResult =
                    SyntaxValidationResult.Valid
            }
    }

    @When("the PlantUML translation port translates the block from {string} to {string}")
    fun thePortTranslates(sourceLanguage: String, targetLanguage: String) {
        val adapter = PlantumlTranslationPortAdapter(provider!!, validator = validator)
        world.roundTripOutcome =
            adapter.translate(PlantUmlTranslationRequest(block!!, sourceLanguage, targetLanguage))
    }

    @Then("the translation outcome should be Preserved")
    fun theOutcomeShouldBePreserved() {
        assertThat(world.roundTripOutcome).isInstanceOf(PlantUmlTranslationOutcome.Preserved::class.java)
    }

    @Then("the translation outcome should be Translated")
    fun theOutcomeShouldBeTranslated() {
        assertThat(world.roundTripOutcome).isInstanceOf(PlantUmlTranslationOutcome.Translated::class.java)
    }

    @Then("the preservation reason should mention syntax")
    fun theReasonShouldMentionSyntax() {
        val outcome = world.roundTripOutcome as PlantUmlTranslationOutcome.Preserved
        assertThat(outcome.reason.lowercase()).contains("syntax")
    }

    @Then("the translated block should contain {string}")
    fun theTranslatedBlockShouldContain(expected: String) {
        val outcome = world.roundTripOutcome as PlantUmlTranslationOutcome.Translated
        // Cucumber `{string}` does not decode `\n` — `expected` is the literal
        // backslash-n that the escaped PlantUML output must contain.
        assertThat(outcome.block.raw).contains(expected)
    }
}
