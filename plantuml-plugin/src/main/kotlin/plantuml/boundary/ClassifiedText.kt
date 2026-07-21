package plantuml.boundary

/**
 * Result of classifying a [TranslatableText] with a recommended strategy.
 *
 * @property source The original classified text
 * @property recommendedStrategy The strategy suggested by the classifier
 */
data class ClassifiedText(val source: TranslatableText, val recommendedStrategy: TranslationStrategy) {
    val text: String get() = source.text
    val category: TranslationCategory get() = source.category
}