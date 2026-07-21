package plantuml.boundary

/**
 * Registry of terms that must never be translated (client-specific vocabulary).
 *
 * Terms registered here are always resolved with [TranslationStrategy.PRESERVE],
 * regardless of their classification.
 */
class NonTranslatableTermRegistry {

    private val terms: MutableSet<String> = mutableSetOf()

    /**
     * Registers a single non-translatable term.
     *
     * @param term The term to preserve as-is
     */
    fun register(term: String) {
        terms.add(term)
    }

    /**
     * Bulk-registers non-translatable terms.
     *
     * @param terms List of terms to preserve as-is
     */
    fun registerAll(terms: List<String>) {
        this.terms.addAll(terms)
    }

    /**
     * Checks whether a term is registered as non-translatable.
     *
     * @param term The term to check
     * @return true if the term must be preserved
     */
    fun contains(term: String): Boolean = terms.contains(term)
}