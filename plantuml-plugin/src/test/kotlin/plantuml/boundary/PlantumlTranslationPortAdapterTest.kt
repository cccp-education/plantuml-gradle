package plantuml.boundary

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlStrategy
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlantumlTranslationPortAdapterTest {

    private fun translator(map: Map<String, String>) = object : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            map[request.sourceText]?.let { TranslationResult.Success(it) }
                ?: TranslationResult.Failure("no entry")
    }

    private fun port(map: Map<String, String>) = PlantumlTranslationPortAdapter(translator(map))

    @Test
    fun `preserves a block with only semantic identity`() {
        val adapter = port(emptyMap())
        val block = PlantUmlBlock("@startuml\nU --> Foo.Bar\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `translates a quoted label via the provider`() {
        val adapter = port(mapOf("Utilisateur" to "User"))
        val block = PlantUmlBlock("@startuml\nclass \"Utilisateur\"\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Translated)
        assertTrue((outcome as PlantUmlTranslationOutcome.Translated).block.raw.contains("\"User\""))
    }

    @Test
    fun `translates an unquoted directive value`() {
        val adapter = port(mapOf("Évolution Mensuelle" to "Monthly Evolution"))
        val block = PlantUmlBlock("@startuml\ntitle Évolution Mensuelle\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue((outcome as PlantUmlTranslationOutcome.Translated).block.raw.contains("title Monthly Evolution"))
    }

    @Test
    fun `converts a raw newline returned by the provider back to the plantuml escape`() {
        val adapter = port(mapOf("CLI de vibe coding" to "vibe coding CLI\nOpen source, free"))
        val block = PlantUmlBlock("@startuml\nrectangle \"CLI de vibe coding\" as CLI\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("\\n"), "newline must be escaped: $raw")
        assertTrue(!raw.contains("vibe coding CLI\nOpen"), "raw line break must not survive")
    }

    @Test
    fun `empty block is preserved`() {
        val adapter = port(emptyMap())
        val outcome = adapter.translate(PlantUmlTranslationRequest(PlantUmlBlock(""), "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `provider failure preserves the block`() {
        val adapter = port(emptyMap())
        val block = PlantUmlBlock("@startuml\nclass \"Utilisateur\"\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `unified strategy alias points to the N0 contract enum`() {
        // D4 — one vocabulary: the boundary alias resolves to the contract enum.
        assertEquals(
            listOf(PlantUmlStrategy.TRANSLATE, PlantUmlStrategy.BORROW, PlantUmlStrategy.PRESERVE),
            contracts.plantuml.PlantUmlStrategy.entries.toList(),
        )
    }
}
