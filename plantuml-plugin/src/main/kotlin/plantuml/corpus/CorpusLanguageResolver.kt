package plantuml.corpus

import contracts.i18n.LanguageCatalog

/**
 * Resolves the variant language of a corpus document from its path.
 *
 * The corpus layout is the same as the published sites: the language is a
 * directory segment (`en/article.adoc`, `jbake/i18n/pt/article.adoc`). The
 * first segment matching a supported N0 language wins; a document without a
 * language directory is the source (pivot) variant.
 */
object CorpusLanguageResolver {
    const val SOURCE = "source"

    fun resolve(
        document: String,
        supportedCodes: Set<String> = LanguageCatalog.supportedCodes(),
    ): String {
        val segments = document.replace('\\', '/').split('/')
        return segments.firstOrNull { it in supportedCodes } ?: SOURCE
    }
}
