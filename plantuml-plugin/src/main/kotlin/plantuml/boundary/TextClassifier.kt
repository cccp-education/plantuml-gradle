package plantuml.boundary

class TextClassifier {

    private val presentationLabels = setOf(
        "Classes",
        "Files",
        "Empty Knowledge Graph",
        "Extracted",
        "Inferred",
        "Ambiguous",
        "Generated Diagram",
        "System",
        "User",
        "Feature",
        "Cross-community edges",
        "Intra-community edges",
        "Unassigned nodes"
    )

    private val lexicalTerms = setOf(
        "pipeline",
        "rollback",
        "dependency injection",
        "cache",
        "endpoint",
        "feature flag",
        "circuit breaker",
        "backpressure",
        "rate limiting",
        "graceful shutdown",
        "blue-green deployment",
        "canary release",
        "idempotent",
        "eventual consistency",
        "saga"
    )

    fun classify(text: String): TranslatableText {
        val category = when {
            presentationLabels.contains(text) -> TranslationCategory.PRESENTATION
            lexicalTerms.contains(text) -> TranslationCategory.LEXICAL_FIELD
            else -> TranslationCategory.SEMANTIC_IDENTITY
        }
        return TranslatableText(text, category)
    }
}