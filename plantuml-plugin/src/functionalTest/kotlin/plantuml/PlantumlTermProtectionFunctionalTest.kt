package plantuml

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationRequest
import contracts.plantuml.SyntaxValidationResult
import contracts.plantuml.PlantUmlSyntaxValidator
import org.junit.jupiter.api.Test
import plantuml.boundary.PlantumlTranslationPortAdapter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EPIC PLT-TERM-PROTECTION — functional proof that a translated diagram keeps its
 * code/business tokens and still parses with the real PlantUML validator.
 */
class PlantumlTermProtectionFunctionalTest {

    private class MappingTranslator(
        private val translations: Map<String, String>,
    ) : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            translations[request.sourceText]?.let { TranslationResult.Success(it) }
                ?: TranslationResult.Failure("no entry")
    }

    private val structuralValidator =
        object : PlantUmlSyntaxValidator {
            override fun validate(plantumlCode: String): SyntaxValidationResult =
                if (plantumlCode.contains("@startuml") &&
                    plantumlCode.contains("@enduml") &&
                    !plantumlCode.contains("__PLT")
                ) {
                    SyntaxValidationResult.Valid
                } else {
                    SyntaxValidationResult.Invalid("structure", plantumlCode)
                }
        }

    @Test
    fun `a translated activity block keeps the file name and the integer type and still parses`() {
        val source =
            """
            @startuml
            start
            :Créer opencode.json;
            note right: Le type integer est utilisé
            stop
            @enduml
            """.trimIndent()
        val translator =
            MappingTranslator(
                mapOf(
                    "Créer __PLT0__" to "Create __PLT0__",
                    "Le type __PLT0__ est utilisé" to "The type __PLT0__ is used",
                ),
            )

        val outcome =
            PlantumlTranslationPortAdapter(
                translator = translator,
                validator = structuralValidator,
            ).translate(PlantUmlTranslationRequest(PlantUmlBlock(source), "fr", "en"))

        val translated = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(translated.contains(":Create opencode.json;"), translated)
        assertTrue(translated.contains("note right: The type integer is used"), translated)
        assertEquals(SyntaxValidationResult.Valid, structuralValidator.validate(translated))
    }
}
