package plantuml.boundary

data class ResolvedText(
    val sourceText: String,
    val translated: String,
    val strategy: TranslationStrategy,
    val category: TranslationCategory
)

class TranslationResolver(
    private val classifier: TextClassifier,
    private val glossary: IdiomaticGlossary,
    private val messageResolver: (String, String) -> String?,
    private val nonTranslatableRegistry: NonTranslatableTermRegistry? = null
) {

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