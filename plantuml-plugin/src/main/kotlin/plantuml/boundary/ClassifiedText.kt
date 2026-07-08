package plantuml.boundary

data class ClassifiedText(val source: TranslatableText, val recommendedStrategy: TranslationStrategy) {
    val text: String get() = source.text
    val category: TranslationCategory get() = source.category
}