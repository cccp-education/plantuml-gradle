package plantuml.scenarios

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.service.DiagramOutputProbe
import plantuml.service.DiagramOutputVerdict

/**
 * Steps for `26_diagram_output_probe.feature` (PLT-CR3-2).
 *
 * Prefix-free step names are unique to this feature (anti-glue collision rule
 * S-088 — the shared `plantuml.scenarios` glue package).
 */
class DiagramOutputProbeSteps {

    private var bytes: ByteArray? = null
    private var source: String = ""
    private var parses: Boolean = true
    private var verdict: DiagramOutputVerdict? = null

    @Given("a diagram image whose bytes start with the PNG signature")
    fun aPngImage() {
        bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(32) { 1 }
    }

    @Given("an empty diagram image")
    fun anEmptyImage() {
        bytes = ByteArray(0)
    }

    @Given("a diagram image containing the generateImage textual fallback")
    fun theTextualFallback() {
        bytes = "PlantUML diagram:\n\n@startuml\nA --> B\n@enduml\n\nError: boom".toByteArray()
    }

    @Given("the diagram source {string} parses")
    fun theSourceParses(source: String) {
        this.source = source.replace("\\n", "\n")
        this.parses = true
    }

    @Given("the diagram source {string} does not parse")
    fun theSourceDoesNotParse(source: String) {
        this.source = source.replace("\\n", "\n")
        this.parses = false
    }

    @When("the diagram output probe checks the image")
    fun theProbeChecksTheImage() {
        val validator =
            PlantUmlSyntaxValidator { code ->
                if (parses && code == source) {
                    SyntaxValidationResult.Valid
                } else {
                    SyntaxValidationResult.Invalid("broken", "")
                }
            }
        verdict = DiagramOutputProbe.probe(bytes!!, source, validator)
    }

    @Then("the probe verdict should be Rendered")
    fun verdictRendered() {
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.Rendered::class.java)
    }

    @Then("the probe verdict should be EmptyImage")
    fun verdictEmptyImage() {
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.EmptyImage::class.java)
    }

    @Then("the probe verdict should be NotAnImage")
    fun verdictNotAnImage() {
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.NotAnImage::class.java)
    }

    @Then("the probe verdict should be InvalidSyntax")
    fun verdictInvalidSyntax() {
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.InvalidSyntax::class.java)
    }
}
