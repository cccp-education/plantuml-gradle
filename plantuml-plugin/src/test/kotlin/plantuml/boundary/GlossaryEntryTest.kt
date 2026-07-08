package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class GlossaryEntryTest {

    @Test
    fun `should hold translation and strategy`() {
        val entry = GlossaryEntry("injection de dépendances", TranslationStrategy.TRANSLATE)

        assertEquals("injection de dépendances", entry.translation)
        assertEquals(TranslationStrategy.TRANSLATE, entry.strategy)
    }

    @Test
    fun `should hold borrow strategy with source term as translation`() {
        val entry = GlossaryEntry("pipeline", TranslationStrategy.BORROW)

        assertEquals("pipeline", entry.translation)
        assertEquals(TranslationStrategy.BORROW, entry.strategy)
    }

    @Test
    fun `should be equal when translation and strategy match`() {
        val a = GlossaryEntry("管道", TranslationStrategy.TRANSLATE)
        val b = GlossaryEntry("管道", TranslationStrategy.TRANSLATE)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `should not be equal when strategy differs`() {
        val a = GlossaryEntry("pipeline", TranslationStrategy.BORROW)
        val b = GlossaryEntry("pipeline", TranslationStrategy.TRANSLATE)

        assertNotEquals(a, b)
    }

    @Test
    fun `should not be equal when translation differs`() {
        val a = GlossaryEntry("管道", TranslationStrategy.TRANSLATE)
        val b = GlossaryEntry("管线", TranslationStrategy.TRANSLATE)

        assertNotEquals(a, b)
    }
}