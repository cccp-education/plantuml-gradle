package plantuml.boundary

/**
 * Classification of text by translatability nature.
 *
 * - [PRESENTATION]: UI labels, headings, structural text — always translatable
 * - [SEMANTIC_IDENTITY]: Identifiers, class names, technical symbols — never translated
 * - [LEXICAL_FIELD]: Domain jargon (e.g., "pipeline", "rollback") — idiomatic choice
 */
enum class TranslationCategory {
    PRESENTATION,
    SEMANTIC_IDENTITY,
    LEXICAL_FIELD
}