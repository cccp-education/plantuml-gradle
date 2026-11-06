package plantuml

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationRequest
import contracts.plantuml.SyntaxValidationResult
import org.junit.jupiter.api.Test
import plantuml.boundary.PlantumlSyntaxValidatorAdapter
import plantuml.boundary.PlantumlTranslationPortAdapter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional-level tests of the note leaf through the N0 translation port
 * (EPIC PLT-I18N-OUTPUT, PLT-OUT-2).
 *
 * The plantuml plugin exposes no Gradle translation task: the port is consumed
 * by document/bakery. The functional surface is therefore the real adapter +
 * the real PlantUML syntax validator, with a fake provider (no LLM, no network).
 */
class PlantumlNoteTranslationFunctionalTest {
    private fun provider(map: Map<String, String>): TranslationService =
        object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult =
                map[request.sourceText]?.let { TranslationResult.Success(it) }
                    ?: TranslationResult.Failure("no entry")
        }

    private fun translate(
        adapter: PlantumlTranslationPortAdapter,
        block: String,
    ): PlantUmlTranslationOutcome = adapter.translate(PlantUmlTranslationRequest(PlantUmlBlock(block), "fr", "en"))

    @Test
    fun `a quoted note translation is corrected and parses with the real validator`() {
        val adapter =
            PlantumlTranslationPortAdapter(
                provider(mapOf("Bonjour" to "Hello \"World\"")),
                validator = PlantumlSyntaxValidatorAdapter(),
            )

        val outcome = translate(adapter, "@startuml\nnote \"Bonjour\" as N1\nA -> B\n@enduml")

        assertTrue(outcome is PlantUmlTranslationOutcome.Translated)
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("note \"Hello \\\"World\\\"\" as N1"), raw)
        assertTrue(raw.contains("A -> B"), "the rest of the diagram must survive: $raw")
    }

    @Test
    fun `a quoted note with a colon is one note and parses with the real validator`() {
        val adapter =
            PlantumlTranslationPortAdapter(
                provider(mapOf("Warning: see below" to "Attention : voir ci-dessous")),
                validator = PlantumlSyntaxValidatorAdapter(),
            )

        val outcome = translate(adapter, "@startuml\nnote \"Warning: see below\" as N1\nA -> B\n@enduml")

        assertTrue(outcome is PlantUmlTranslationOutcome.Translated)
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("note \"Attention : voir ci-dessous\" as N1"), raw)
        assertTrue(PlantumlSyntaxValidatorAdapter().validate(raw) is SyntaxValidationResult.Valid)
    }

    @Test
    fun `a note rejected by the round-trip gate preserves the source block`() {
        val rejecting =
            object : PlantUmlSyntaxValidator {
                override fun validate(plantumlCode: String): SyntaxValidationResult = SyntaxValidationResult.Invalid("broken", "")
            }
        val adapter =
            PlantumlTranslationPortAdapter(
                provider(mapOf("Bonjour" to "end note")),
                validator = rejecting,
            )

        val outcome = translate(adapter, "@startuml\nnote right of B\n  Bonjour\nend note\n@enduml")

        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
        assertEquals(
            "translation breaks PlantUML syntax — source block preserved",
            (outcome as PlantUmlTranslationOutcome.Preserved).reason,
        )
    }
}
