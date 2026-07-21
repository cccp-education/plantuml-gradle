package plantuml.boundary

/**
 * Strategy for translating a classified text.
 *
 * - [TRANSLATE]: Word-for-word translation via Messages_*.properties
 * - [BORROW]: Keep the source term as-is (loanword)
 * - [PRESERVE]: Never translate (identifiers, formulas, client-specific terms)
 */
enum class TranslationStrategy {
    TRANSLATE,
    BORROW,
    PRESERVE
}