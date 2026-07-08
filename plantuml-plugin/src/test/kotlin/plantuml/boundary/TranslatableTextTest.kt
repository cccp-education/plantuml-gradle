package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class TranslatableTextTest {

    @Test
    fun `should hold text and category`() {
        val text = TranslatableText("Classes", TranslationCategory.PRESENTATION)

        assertEquals("Classes", text.text)
        assertEquals(TranslationCategory.PRESENTATION, text.category)
    }

    @Test
    fun `should be equal when text and category match`() {
        val a = TranslatableText("LlmService", TranslationCategory.SEMANTIC_IDENTITY)
        val b = TranslatableText("LlmService", TranslationCategory.SEMANTIC_IDENTITY)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `should not be equal when text differs`() {
        val a = TranslatableText("LlmService", TranslationCategory.SEMANTIC_IDENTITY)
        val b = TranslatableText("DiagramProcessor", TranslationCategory.SEMANTIC_IDENTITY)

        assertNotEquals(a, b)
    }

    @Test
    fun `should not be equal when category differs`() {
        val a = TranslatableText("pipeline", TranslationCategory.LEXICAL_FIELD)
        val b = TranslatableText("pipeline", TranslationCategory.PRESENTATION)

        assertNotEquals(a, b)
    }

    @Test
    fun `should expose text as value object property`() {
        val text = TranslatableText("Empty Knowledge Graph", TranslationCategory.PRESENTATION)

        assertEquals("Empty Knowledge Graph", text.text)
    }
}