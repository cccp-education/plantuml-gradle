package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdiomaticGlossaryLoaderTest {

    private val loader = IdiomaticGlossaryLoader()

    @Test
    fun `should parse single term single language`() {
        val yaml = """
            pipeline:
              fr:
                translation: pipeline
                strategy: BORROW
        """.trimIndent()

        val glossary = loader.load(yaml)

        assertTrue(glossary.contains("pipeline", "fr"))
        val entry = glossary.lookup("pipeline", "fr")!!
        assertEquals("pipeline", entry.translation)
        assertEquals(TranslationStrategy.BORROW, entry.strategy)
    }

    @Test
    fun `should parse translate strategy`() {
        val yaml = """
            dependency injection:
              fr:
                translation: injection de dépendances
                strategy: TRANSLATE
        """.trimIndent()

        val glossary = loader.load(yaml)

        val entry = glossary.lookup("dependency injection", "fr")!!
        assertEquals("injection de dépendances", entry.translation)
        assertEquals(TranslationStrategy.TRANSLATE, entry.strategy)
    }

    @Test
    fun `should parse multiple languages for same term`() {
        val yaml = """
            pipeline:
              fr:
                translation: pipeline
                strategy: BORROW
              zh:
                translation: 管道
                strategy: TRANSLATE
        """.trimIndent()

        val glossary = loader.load(yaml)

        assertEquals("pipeline", glossary.lookup("pipeline", "fr")!!.translation)
        assertEquals("管道", glossary.lookup("pipeline", "zh")!!.translation)
    }

    @Test
    fun `should parse multiple terms`() {
        val yaml = """
            pipeline:
              fr:
                translation: pipeline
                strategy: BORROW
            rollback:
              fr:
                translation: rollback
                strategy: BORROW
            dependency injection:
              fr:
                translation: injection de dépendances
                strategy: TRANSLATE
        """.trimIndent()

        val glossary = loader.load(yaml)

        assertEquals(TranslationStrategy.BORROW, glossary.lookup("pipeline", "fr")!!.strategy)
        assertEquals(TranslationStrategy.BORROW, glossary.lookup("rollback", "fr")!!.strategy)
        assertEquals(TranslationStrategy.TRANSLATE, glossary.lookup("dependency injection", "fr")!!.strategy)
    }

    @Test
    fun `should parse preserve strategy`() {
        val yaml = """
            REAC:
              fr:
                translation: REAC
                strategy: PRESERVE
        """.trimIndent()

        val glossary = loader.load(yaml)

        val entry = glossary.lookup("REAC", "fr")!!
        assertEquals("REAC", entry.translation)
        assertEquals(TranslationStrategy.PRESERVE, entry.strategy)
    }

    @Test
    fun `should return empty glossary for blank yaml`() {
        val yaml = ""

        val glossary = loader.load(yaml)

        assertTrue(glossary.contains("nothing", "fr").not())
    }
}