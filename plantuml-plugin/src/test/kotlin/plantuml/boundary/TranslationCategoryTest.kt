package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationCategoryTest {

    @Test
    fun `should define three categories`() {
        val categories = TranslationCategory.entries

        assertEquals(3, categories.size)
        assertTrue(categories.contains(TranslationCategory.PRESENTATION))
        assertTrue(categories.contains(TranslationCategory.SEMANTIC_IDENTITY))
        assertTrue(categories.contains(TranslationCategory.LEXICAL_FIELD))
    }

    @Test
    fun `should expose presentation nature`() {
        assertEquals("PRESENTATION", TranslationCategory.PRESENTATION.name)
    }

    @Test
    fun `should expose semantic identity nature`() {
        assertEquals("SEMANTIC_IDENTITY", TranslationCategory.SEMANTIC_IDENTITY.name)
    }

    @Test
    fun `should expose lexical field nature`() {
        assertEquals("LEXICAL_FIELD", TranslationCategory.LEXICAL_FIELD.name)
    }
}