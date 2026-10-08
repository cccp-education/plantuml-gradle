package plantuml.boundary

import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlStrategy
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlantumlTranslationPortAdapterTest {

    private fun resolverWith(messages: Map<String, String>, glossary: IdiomaticGlossary = IdiomaticGlossary()) =
        TranslationResolver(
            classifier = TextClassifier(),
            glossary = glossary,
            messageResolver = { key, _ -> messages[key] },
        )

    private fun port(resolver: TranslationResolver) = PlantumlTranslationPortAdapter(resolver)

    @Test
    fun `preserves a block with only semantic identity`() {
        val adapter = port(resolverWith(emptyMap()))
        val block = PlantUmlBlock("@startuml\nU --> Foo.Bar\n@enduml")
        val outcome =
            adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `translates a quoted label via the boundary resolver`() {
        val resolver = resolverWith(mapOf("label.classes" to "Klassen"))
        val adapter = port(resolver)
        val block = PlantUmlBlock("@startuml\nclass \"Classes\"\n@enduml")
        val outcome =
            adapter.translate(PlantUmlTranslationRequest(block, "en", "de"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Translated)
        assertTrue((outcome as PlantUmlTranslationOutcome.Translated).block.raw.contains("\"Klassen\""))
    }

    @Test
    fun `converts a raw newline returned by the translator back to the plantuml escape`() {
        // The LLM may return a real line break where the source had `\n`.
        val resolver = resolverWith(mapOf("label.classes" to "Cla\nsses"))
        val adapter = port(resolver)
        val block = PlantUmlBlock("@startuml\nclass \"Classes\"\n@enduml")
        val outcome =
            adapter.translate(PlantUmlTranslationRequest(block, "en", "de"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("\\n"), "newline must be escaped: $raw")
        assertTrue(!raw.contains("Cla\nsses"), "raw line break must not survive")
    }

    @Test
    fun `preserves borrowed vocabulary blocks`() {
        val glossary =
            IdiomaticGlossary().apply {
                register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))
            }
        val adapter = port(resolverWith(emptyMap(), glossary))
        val block =
            PlantUmlBlock(
                raw = "@startuml\nrectangle \"pipeline\"\n@enduml",
                borrowedVocabulary = setOf("pipeline"),
            )
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "en", "fr"))
        // BORROW keeps the term; the block is unchanged → Preserved.
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `empty block is preserved`() {
        val adapter = port(resolverWith(emptyMap()))
        val outcome =
            adapter.translate(PlantUmlTranslationRequest(PlantUmlBlock(""), "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `unified strategy alias points to the N0 contract enum`() {
        // D4 — one vocabulary: the boundary alias resolves to the contract enum.
        assertEquals(PlantUmlStrategy.TRANSLATE, contracts.plantuml.PlantUmlStrategy.TRANSLATE)
        assertEquals(
            listOf(PlantUmlStrategy.TRANSLATE, PlantUmlStrategy.BORROW, PlantUmlStrategy.PRESERVE),
            contracts.plantuml.PlantUmlStrategy.entries.toList(),
        )
    }
}
