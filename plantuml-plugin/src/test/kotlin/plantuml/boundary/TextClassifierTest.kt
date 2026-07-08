package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextClassifierTest {

    private val classifier = TextClassifier()

    @Test
    fun `should classify presentation label Classes`() {
        val result = classifier.classify("Classes")

        assertEquals(TranslationCategory.PRESENTATION, result.category)
    }

    @Test
    fun `should classify presentation label Files`() {
        val result = classifier.classify("Files")

        assertEquals(TranslationCategory.PRESENTATION, result.category)
    }

    @Test
    fun `should classify presentation label Empty Knowledge Graph`() {
        val result = classifier.classify("Empty Knowledge Graph")

        assertEquals(TranslationCategory.PRESENTATION, result.category)
    }

    @Test
    fun `should classify presentation legend Extracted`() {
        val result = classifier.classify("Extracted")

        assertEquals(TranslationCategory.PRESENTATION, result.category)
    }

    @Test
    fun `should classify presentation legend Inferred`() {
        val result = classifier.classify("Inferred")

        assertEquals(TranslationCategory.PRESENTATION, result.category)
    }

    @Test
    fun `should classify presentation legend Ambiguous`() {
        val result = classifier.classify("Ambiguous")

        assertEquals(TranslationCategory.PRESENTATION, result.category)
    }

    @Test
    fun `should classify pascal case identifier as semantic identity`() {
        val result = classifier.classify("LlmService")

        assertEquals(TranslationCategory.SEMANTIC_IDENTITY, result.category)
    }

    @Test
    fun `should classify pascal case identifier DiagramProcessor as semantic identity`() {
        val result = classifier.classify("DiagramProcessor")

        assertEquals(TranslationCategory.SEMANTIC_IDENTITY, result.category)
    }

    @Test
    fun `should classify snake case identifier as semantic identity`() {
        val result = classifier.classify("community_0")

        assertEquals(TranslationCategory.SEMANTIC_IDENTITY, result.category)
    }

    @Test
    fun `should classify lowercase verb as semantic identity`() {
        val result = classifier.classify("calls")

        assertEquals(TranslationCategory.SEMANTIC_IDENTITY, result.category)
    }

    @Test
    fun `should classify lexical field term pipeline`() {
        val result = classifier.classify("pipeline")

        assertEquals(TranslationCategory.LEXICAL_FIELD, result.category)
    }

    @Test
    fun `should classify lexical field term rollback`() {
        val result = classifier.classify("rollback")

        assertEquals(TranslationCategory.LEXICAL_FIELD, result.category)
    }

    @Test
    fun `should classify lexical field term dependency injection`() {
        val result = classifier.classify("dependency injection")

        assertEquals(TranslationCategory.LEXICAL_FIELD, result.category)
    }

    @Test
    fun `should classify lexical field term cache`() {
        val result = classifier.classify("cache")

        assertEquals(TranslationCategory.LEXICAL_FIELD, result.category)
    }

    @Test
    fun `should classify lexical field term endpoint`() {
        val result = classifier.classify("endpoint")

        assertEquals(TranslationCategory.LEXICAL_FIELD, result.category)
    }

    @Test
    fun `should classify lexical field term feature flag`() {
        val result = classifier.classify("feature flag")

        assertEquals(TranslationCategory.LEXICAL_FIELD, result.category)
    }

    @Test
    fun `should return translatable text with original text`() {
        val result = classifier.classify("Classes")

        assertEquals("Classes", result.text)
    }
}