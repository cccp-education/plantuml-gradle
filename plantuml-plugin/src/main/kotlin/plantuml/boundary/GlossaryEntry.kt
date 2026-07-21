package plantuml.boundary

/**
 * A single glossary entry for a term in a specific language.
 *
 * @property translation The translated or borrowed form of the term
 * @property strategy The [TranslationStrategy] to apply
 */
data class GlossaryEntry(val translation: String, val strategy: TranslationStrategy)