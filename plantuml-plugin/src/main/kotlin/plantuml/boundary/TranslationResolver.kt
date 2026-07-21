package plantuml.boundary

/**
 * Result of resolving a text through the translation pipeline.
 *
 * @property sourceText The original untranslated text
 * @property translated The resolved (possibly translated) output
 * @property strategy The [TranslationStrategy] that was applied
 * @property category The [TranslationCategory] of the source text
 */
data class ResolvedText(
    val sourceText: String,
    val translated: String,
    val strategy: TranslationStrategy,
    val category: TranslationCategory
)

/**
 * Orchestrates the full translation resolution pipeline.
 *
 * For a given text and language:
 * 1. Checks the [NonTranslatableTermRegistry] (client-specific terms)
 * 2. Classifies the text via [TextClassifier]
 * 3. Decides the strategy based on category and [IdiomaticGlossary]
 * 4. Applies the strategy to produce the final translated text
 *
 * @param classifier Classifies text into a [TranslationCategory]
 * @param glossary Idiomatic glossary for domain terms
 * @param messageResolver Resolves i18n message keys (e.g., from [plantuml.PlantumlMessages])
 * @param nonTranslatableRegistry Optional registry of terms that must never be translated
 */
class TranslationResolver(
    private val classifier: TextClassifier,
    private val glossary: IdiomaticGlossary,
    private val messageResolver: (String, String) -> String?,
    private val nonTranslatableRegistry: NonTranslatableTermRegistry? = null
) {

    /**
     * Resolves a text for the given language through the full pipeline.
     *
     * @param text The raw text to resolve
     * @param language Target language code (e.g., "fr", "zh")
     * @return A [ResolvedText] with the translated output and metadata
     */
    fun resolve(text: String, language: String): ResolvedText {
        if (nonTranslatableRegistry?.contains(text) == true) {
            return ResolvedText(
                sourceText = text,
                translated = text,
                strategy = TranslationStrategy.PRESERVE,
                category = TranslationCategory.SEMANTIC_IDENTITY
            )
        }
        val translatable = classifier.classify(text)
        val strategy = decideStrategy(translatable, language)
        val translated = applyStrategy(text, translatable.category, strategy, language)

        return ResolvedText(
            sourceText = text,
            translated = translated,
            strategy = strategy,
            category = translatable.category
        )
    }

    private fun decideStrategy(translatable: TranslatableText, language: String): TranslationStrategy {
        return when (translatable.category) {
            TranslationCategory.SEMANTIC_IDENTITY -> TranslationStrategy.PRESERVE
            TranslationCategory.PRESENTATION -> TranslationStrategy.TRANSLATE
            TranslationCategory.LEXICAL_FIELD -> {
                val entry = glossary.lookup(translatable.text, language)
                entry?.strategy ?: TranslationStrategy.PRESERVE
            }
        }
    }

    private fun applyStrategy(
        text: String,
        category: TranslationCategory,
        strategy: TranslationStrategy,
        language: String
    ): String {
        return when (strategy) {
            TranslationStrategy.PRESERVE -> text
            TranslationStrategy.BORROW -> {
                glossary.lookup(text, language)?.translation ?: text
            }
            TranslationStrategy.TRANSLATE -> {
                when (category) {
                    TranslationCategory.PRESENTATION -> {
                        val key = messageKey(text)
                        messageResolver(key, language) ?: text
                    }
                    TranslationCategory.LEXICAL_FIELD -> {
                        glossary.lookup(text, language)?.translation ?: text
                    }
                    TranslationCategory.SEMANTIC_IDENTITY -> text
                }
            }
        }
    }

    private fun messageKey(text: String): String {
        return "label.${text.lowercase().replace(' ', '.')}"
    }
}