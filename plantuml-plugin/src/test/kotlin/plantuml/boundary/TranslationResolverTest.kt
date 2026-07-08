package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationResolverTest {

    @Test
    fun `should translate presentation label via messages fallback`() {
        val classifier = TextClassifier()
        val glossary = IdiomaticGlossary()
        val resolver = TranslationResolver(
            classifier = classifier,
            glossary = glossary,
            messageResolver = { _, _ -> "Classes" }
        )

        val result = resolver.resolve("Classes", "fr")

        assertEquals("Classes", result.translated)
        assertEquals(TranslationStrategy.TRANSLATE, result.strategy)
    }

    @Test
    fun `should preserve semantic identity`() {
        val classifier = TextClassifier()
        val glossary = IdiomaticGlossary()
        val resolver = TranslationResolver(
            classifier = classifier,
            glossary = glossary,
            messageResolver = { _, _ -> null }
        )

        val result = resolver.resolve("LlmService", "fr")

        assertEquals("LlmService", result.translated)
        assertEquals(TranslationStrategy.PRESERVE, result.strategy)
    }

    @Test
    fun `should borrow lexical term when glossary says borrow`() {
        val classifier = TextClassifier()
        val glossary = IdiomaticGlossary().apply {
            register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))
        }
        val resolver = TranslationResolver(
            classifier = classifier,
            glossary = glossary,
            messageResolver = { _, _ -> null }
        )

        val result = resolver.resolve("pipeline", "fr")

        assertEquals("pipeline", result.translated)
        assertEquals(TranslationStrategy.BORROW, result.strategy)
    }

    @Test
    fun `should translate lexical term when glossary says translate`() {
        val classifier = TextClassifier()
        val glossary = IdiomaticGlossary().apply {
            register("dependency injection", "fr", GlossaryEntry("injection de dépendances", TranslationStrategy.TRANSLATE))
        }
        val resolver = TranslationResolver(
            classifier = classifier,
            glossary = glossary,
            messageResolver = { _, _ -> null }
        )

        val result = resolver.resolve("dependency injection", "fr")

        assertEquals("injection de dépendances", result.translated)
        assertEquals(TranslationStrategy.TRANSLATE, result.strategy)
    }

    @Test
    fun `should preserve lexical term when not in glossary and no message`() {
        val classifier = TextClassifier()
        val glossary = IdiomaticGlossary()
        val resolver = TranslationResolver(
            classifier = classifier,
            glossary = glossary,
            messageResolver = { _, _ -> null }
        )

        val result = resolver.resolve("pipeline", "fr")

        assertEquals("pipeline", result.translated)
        assertEquals(TranslationStrategy.PRESERVE, result.strategy)
    }

    @Test
    fun `should translate presentation label using message resolver`() {
        val classifier = TextClassifier()
        val glossary = IdiomaticGlossary()
        val resolver = TranslationResolver(
            classifier = classifier,
            glossary = glossary,
            messageResolver = { key, language -> if (key == "label.classes" && language == "fr") "Classes" else null }
        )

        val result = resolver.resolve("Classes", "fr")

        assertEquals("Classes", result.translated)
        assertEquals(TranslationStrategy.TRANSLATE, result.strategy)
    }

    @Test
    fun `should expose classified text in result`() {
        val classifier = TextClassifier()
        val glossary = IdiomaticGlossary()
        val resolver = TranslationResolver(
            classifier = classifier,
            glossary = glossary,
            messageResolver = { _, _ -> null }
        )

        val result = resolver.resolve("LlmService", "fr")

        assertEquals(TranslationCategory.SEMANTIC_IDENTITY, result.category)
        assertEquals("LlmService", result.sourceText)
    }
}