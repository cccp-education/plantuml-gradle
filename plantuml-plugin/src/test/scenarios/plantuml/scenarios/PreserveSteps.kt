package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import plantuml.boundary.PlantumlTermShield
import plantuml.boundary.ShieldedText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MEM-I18N-PRESERVE / PRESERVE-2 — `@preserve` scenarios.
 *
 * Step wording is prefixed with "preserve" so it stays unique in the shared
 * `plantuml.scenarios` glue package (anti-collision rule S-088).
 */
class PreserveSteps {
    private val shield = PlantumlTermShield()
    private var label: String = ""
    private var shielded: ShieldedText = ShieldedText("", emptyList())

    @Given("a preserve label {string}")
    fun aPreserveLabel(value: String) {
        label = value
    }

    @When("the preserve shield protects the label")
    fun thePreserveShieldProtectsTheLabel() {
        shielded = shield.shield(label)
    }

    @Then("the preserve shield protected {string}")
    fun thePreserveShieldProtected(term: String) {
        assertTrue(term in shielded.terms, "expected $term in ${shielded.terms}")
    }

    @Then("the preserve label is fully shielded")
    fun thePreserveLabelIsFullyShielded() {
        assertTrue(shielded.isFullyShielded, "expected a fully shielded label")
    }

    @Then("the preserve label is not fully shielded")
    fun thePreserveLabelIsNotFullyShielded() {
        assertEquals(false, shielded.isFullyShielded)
    }

    @Then("the preserve shield protected nothing")
    fun thePreserveShieldProtectedNothing() {
        assertTrue(shielded.terms.isEmpty(), "expected no term, got ${shielded.terms}")
    }

    @Then("restoring the shielded label is byte-identical")
    fun restoringTheShieldedLabelIsByteIdentical() {
        val restored = shield.restore(shielded.text, shielded)
        assertEquals(label, restored)
    }
}
