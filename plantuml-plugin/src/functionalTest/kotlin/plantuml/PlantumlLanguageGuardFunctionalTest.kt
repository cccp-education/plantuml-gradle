package plantuml

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationRequest
import contracts.plantuml.SyntaxValidationResult
import org.junit.jupiter.api.Test
import plantuml.boundary.PlantumlSyntaxValidatorAdapter
import plantuml.boundary.PlantumlTranslationPortAdapter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PLT-OUT-3 — functional proof that the language gate never publishes a false
 * `TRANSLATED` and that a preserved/translated block still parses with the real
 * PlantUML engine (no structural fake here).
 */
class PlantumlLanguageGuardFunctionalTest {
    private val validator = PlantumlSyntaxValidatorAdapter()

    private class MappingTranslator(
        private val translations: Map<String, String>,
    ) : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            translations[request.sourceText]?.let { TranslationResult.Success(it) }
                ?: TranslationResult.Failure("no entry")
    }

    @Test
    fun `a French residue preserves the block and the real validator accepts the source`() {
        val source = "@startuml\nclass \"Utilisateur\"\n@enduml"
        val adapter =
            PlantumlTranslationPortAdapter(
                translator = MappingTranslator(mapOf("Utilisateur" to "Utilisateur.")),
                validator = validator,
            )

        val outcome = adapter.translate(PlantUmlTranslationRequest(PlantUmlBlock(source), "fr", "en"))

        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
        assertTrue(validator.validate(source) is SyntaxValidationResult.Valid)
    }

    @Test
    fun `a mixed block translates the safe label preserves the residue note and still parses`() {
        val source = "@startuml\nparticipant \"Utilisateur\" as U\nnote right of U : Connexion\n@enduml"
        val adapter =
            PlantumlTranslationPortAdapter(
                translator =
                    MappingTranslator(
                        mapOf(
                            "Utilisateur" to "User",
                            "Connexion" to "Connexion.",
                        ),
                    ),
                validator = validator,
            )

        val outcome = adapter.translate(PlantUmlTranslationRequest(PlantUmlBlock(source), "fr", "en"))

        val translated = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(translated.contains("\"User\""), translated)
        assertTrue(translated.contains("note right of U : Connexion"), translated)
        assertTrue(!translated.contains("__PLT"), translated)
        assertEquals(SyntaxValidationResult.Valid, validator.validate(translated))
    }

    @Test
    fun `a bounded retry recovers and the translated block still parses`() {
        var calls = 0
        val translator =
            object : TranslationService {
                override fun translate(request: TranslationRequest): TranslationResult {
                    calls++
                    return if (calls == 1) {
                        TranslationResult.Success("Créer le fichier.")
                    } else {
                        TranslationResult.Success("Create the file")
                    }
                }
            }
        val adapter =
            PlantumlTranslationPortAdapter(
                translator = translator,
                validator = validator,
            )

        val outcome =
            adapter.translate(
                PlantUmlTranslationRequest(
                    PlantUmlBlock("@startuml\nA -> B : Créer le fichier\n@enduml"),
                    "fr",
                    "en",
                ),
            )

        val translated = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(translated.contains("A -> B : Create the file"), translated)
        assertEquals(2, calls)
        assertEquals(SyntaxValidationResult.Valid, validator.validate(translated))
    }
}
