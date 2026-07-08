package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdiomaticGlossaryTest {

    @Test
    fun `should register and retrieve entry by term and language`() {
        val glossary = IdiomaticGlossary()
        glossary.register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))

        val entry = glossary.lookup("pipeline", "fr")

        assertTrue(entry != null)
        assertEquals("pipeline", entry!!.translation)
        assertEquals(TranslationStrategy.BORROW, entry.strategy)
    }

    @Test
    fun `should register translated term for zh`() {
        val glossary = IdiomaticGlossary()
        glossary.register("pipeline", "zh", GlossaryEntry("管道", TranslationStrategy.TRANSLATE))

        val entry = glossary.lookup("pipeline", "zh")

        assertEquals("管道", entry!!.translation)
        assertEquals(TranslationStrategy.TRANSLATE, entry.strategy)
    }

    @Test
    fun `should return null when term not found`() {
        val glossary = IdiomaticGlossary()

        val entry = glossary.lookup("unknown", "fr")

        assertNull(entry)
    }

    @Test
    fun `should return null when language not found for term`() {
        val glossary = IdiomaticGlossary()
        glossary.register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))

        val entry = glossary.lookup("pipeline", "zh")

        assertNull(entry)
    }

    @Test
    fun `should support multiple terms`() {
        val glossary = IdiomaticGlossary()
        glossary.register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))
        glossary.register("rollback", "fr", GlossaryEntry("rollback", TranslationStrategy.BORROW))
        glossary.register("dependency injection", "fr", GlossaryEntry("injection de dépendances", TranslationStrategy.TRANSLATE))

        assertEquals(TranslationStrategy.BORROW, glossary.lookup("pipeline", "fr")!!.strategy)
        assertEquals(TranslationStrategy.BORROW, glossary.lookup("rollback", "fr")!!.strategy)
        assertEquals("injection de dépendances", glossary.lookup("dependency injection", "fr")!!.translation)
    }

    @Test
    fun `should support multiple languages for same term`() {
        val glossary = IdiomaticGlossary()
        glossary.register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))
        glossary.register("pipeline", "zh", GlossaryEntry("管道", TranslationStrategy.TRANSLATE))
        glossary.register("pipeline", "es", GlossaryEntry("tubería", TranslationStrategy.TRANSLATE))

        assertEquals("pipeline", glossary.lookup("pipeline", "fr")!!.translation)
        assertEquals("管道", glossary.lookup("pipeline", "zh")!!.translation)
        assertEquals("tubería", glossary.lookup("pipeline", "es")!!.translation)
    }

    @Test
    fun `should know if term is registered for language`() {
        val glossary = IdiomaticGlossary()
        glossary.register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))

        assertTrue(glossary.contains("pipeline", "fr"))
        assertTrue(!glossary.contains("pipeline", "zh"))
        assertTrue(!glossary.contains("unknown", "fr"))
    }

    @Test
    fun `should support bulk registration from map`() {
        val glossary = IdiomaticGlossary()
        val entries = mapOf(
            "pipeline" to mapOf("fr" to GlossaryEntry("pipeline", TranslationStrategy.BORROW)),
            "rollback" to mapOf("fr" to GlossaryEntry("rollback", TranslationStrategy.BORROW))
        )
        glossary.registerAll(entries)

        assertEquals(TranslationStrategy.BORROW, glossary.lookup("pipeline", "fr")!!.strategy)
        assertEquals(TranslationStrategy.BORROW, glossary.lookup("rollback", "fr")!!.strategy)
    }
}