package plantuml.boundary

/**
 * A text fragment annotated with its translation category.
 *
 * @property text The raw text to classify
 * @property category The assigned [TranslationCategory]
 */
data class TranslatableText(val text: String, val category: TranslationCategory)