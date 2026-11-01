package plantuml.service

/**
 * Typed knowledge-graph parse failures — errors as values (XB-FP-5, arrow-kt).
 *
 * The parser used to throw (`IllegalArgumentException` for a missing file, and
 * Jackson's `JsonParseException` straight through for malformed input). Both are
 * now `Left` values of [KnowledgeGraphParser.parseEither]; `parse()` keeps the
 * historical exception contract by folding, exactly once, at the boundary.
 */
sealed interface KnowledgeGraphParseError {
    /** Human-readable, already-localized message. */
    val message: String

    /** The graph file does not exist at [path]. */
    data class FileNotFound(
        val path: String,
        override val message: String,
    ) : KnowledgeGraphParseError

    /** The file exists but is not valid JSON for a knowledge graph. */
    data class Malformed(
        val cause: Throwable?,
        override val message: String,
    ) : KnowledgeGraphParseError
}
