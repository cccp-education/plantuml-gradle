package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.repair.PlantumlNoteExtractor

/**
 * Steps for `34_plantuml_notes.feature` (EPIC GBL-010, G-3 — "notes").
 *
 * All step names carry the `note block` / `note texts` wording so they stay unique
 * within the shared `plantuml.scenarios` glue package (anti-collision rule S-088).
 */
class PlantumlNoteSteps {

    private var source: String = ""
    private var noteTexts: List<String> = emptyList()

    @Given("a note block containing {string}")
    fun aNoteBlockContaining(line: String) {
        source = "@startuml\n$line\n@enduml"
    }

    @Given("a multi-line note block")
    fun aMultiLineNoteBlock() {
        source = "@startuml\nnote right of Car\n  line one\n  line two\nend note\n@enduml"
    }

    @When("the note texts are extracted")
    fun theNoteTextsAreExtracted() {
        noteTexts = PlantumlNoteExtractor.notes(source)
    }

    @Then("the note texts contain {string}")
    fun theNoteTextsContain(fragment: String) {
        assertThat(noteTexts).anyMatch { it.contains(fragment) }
    }

    @Then("the note texts are empty")
    fun theNoteTextsAreEmpty() {
        assertThat(noteTexts).isEmpty()
    }
}
