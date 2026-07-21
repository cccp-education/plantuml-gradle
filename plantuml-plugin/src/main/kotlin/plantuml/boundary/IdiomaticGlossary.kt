package plantuml.boundary

/**
 * Registry of idiomatic translations for domain-specific terms.
 *
 * Maps a term to per-language [GlossaryEntry] instances, supporting
 * [TranslationStrategy.TRANSLATE] and [TranslationStrategy.BORROW] strategies.
 */
class IdiomaticGlossary {

    private val entries: MutableMap<String, MutableMap<String, GlossaryEntry>> = mutableMapOf()

    /**
     * Registers a single glossary entry for a term in a specific language.
     *
     * @param term The source term
     * @param language Language code (e.g., "fr", "zh")
     * @param entry The [GlossaryEntry] with translation and strategy
     */
    fun register(term: String, language: String, entry: GlossaryEntry) {
        entries.getOrPut(term) { mutableMapOf() }[language] = entry
    }

    /**
     * Bulk-registers entries from a map of term → language → entry.
     *
     * @param entries Nested map of term to language to [GlossaryEntry]
     */
    fun registerAll(entries: Map<String, Map<String, GlossaryEntry>>) {
        entries.forEach { (term, byLanguage) ->
            byLanguage.forEach { (language, entry) ->
                register(term, language, entry)
            }
        }
    }

    /**
     * Looks up the glossary entry for a term in a specific language.
     *
     * @param term The source term
     * @param language Language code
     * @return The matching [GlossaryEntry], or null if not found
     */
    fun lookup(term: String, language: String): GlossaryEntry? =
        entries[term]?.get(language)

    /**
     * Checks whether a glossary entry exists for the given term and language.
     *
     * @param term The source term
     * @param language Language code
     * @return true if an entry exists
     */
    fun contains(term: String, language: String): Boolean =
        entries[term]?.containsKey(language) == true
}