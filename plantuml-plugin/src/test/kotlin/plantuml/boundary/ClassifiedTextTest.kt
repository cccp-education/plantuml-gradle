package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ClassifiedTextTest {

    @Test
    fun `should hold translatable text and recommended strategy`() {
        val translatable = TranslatableText("Classes", TranslationCategory.PRESENTATION)
        val classified = ClassifiedText(translatable, TranslationStrategy.TRANSLATE)

        assertEquals(translatable, classified.source)
        assertEquals(TranslationStrategy.TRANSLATE, classified.recommendedStrategy)
    }

    @Test
    fun `should preserve strategy when semantic identity`() {
        val translatable = TranslatableText("LlmService", TranslationCategory.SEMANTIC_IDENTITY)
        val classified = ClassifiedText(translatable, TranslationStrategy.PRESERVE)

        assertEquals(TranslationStrategy.PRESERVE, classified.recommendedStrategy)
    }

    @Test
    fun `should borrow when lexical field borrowed`() {
        val translatable = TranslatableText("pipeline", TranslationCategory.LEXICAL_FIELD)
        val classified = ClassifiedText(translatable, TranslationStrategy.BORROW)

        assertEquals(TranslationStrategy.BORROW, classified.recommendedStrategy)
    }

    @Test
    fun `should be equal when source and strategy match`() {
        val translatable = TranslatableText("pipeline", TranslationCategory.LEXICAL_FIELD)
        val a = ClassifiedText(translatable, TranslationStrategy.BORROW)
        val b = ClassifiedText(translatable, TranslationStrategy.BORROW)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `should not be equal when strategy differs`() {
        val translatable = TranslatableText("pipeline", TranslationCategory.LEXICAL_FIELD)
        val a = ClassifiedText(translatable, TranslationStrategy.BORROW)
        val b = ClassifiedText(translatable, TranslationStrategy.TRANSLATE)

        assertNotEquals(a, b)
    }

    @Test
    fun `should expose category shortcut from source`() {
        val translatable = TranslatableText("Classes", TranslationCategory.PRESENTATION)
        val classified = ClassifiedText(translatable, TranslationStrategy.TRANSLATE)

        assertEquals(TranslationCategory.PRESENTATION, classified.category)
    }

    @Test
    fun `should expose text shortcut from source`() {
        val translatable = TranslatableText("Classes", TranslationCategory.PRESENTATION)
        val classified = ClassifiedText(translatable, TranslationStrategy.TRANSLATE)

        assertEquals("Classes", classified.text)
    }
}