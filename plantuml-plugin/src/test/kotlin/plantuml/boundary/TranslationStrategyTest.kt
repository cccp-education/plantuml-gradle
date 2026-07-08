package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationStrategyTest {

    @Test
    fun `should define three strategies`() {
        val strategies = TranslationStrategy.entries

        assertEquals(3, strategies.size)
        assertTrue(strategies.contains(TranslationStrategy.TRANSLATE))
        assertTrue(strategies.contains(TranslationStrategy.BORROW))
        assertTrue(strategies.contains(TranslationStrategy.PRESERVE))
    }

    @Test
    fun `should expose translate strategy`() {
        assertEquals("TRANSLATE", TranslationStrategy.TRANSLATE.name)
    }

    @Test
    fun `should expose borrow strategy`() {
        assertEquals("BORROW", TranslationStrategy.BORROW.name)
    }

    @Test
    fun `should expose preserve strategy`() {
        assertEquals("PRESERVE", TranslationStrategy.PRESERVE.name)
    }
}