package plantuml.corpus

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PLT-OUT-4 — the pure corpus language probe.
 *
 * It measures the same surfaces as `PlantumlTranslationPortAdapter` (quoted
 * labels/directives, notes, unquoted prose) with the deterministic
 * `SourceLanguageResidue`, so the corpus measurement never drifts from the
 * generation guard. Non-blocking by design: a finding is a measurement.
 */
class PlantumlCorpusLanguageProbeTest {
    private fun block(
        document: String = "en/article.adoc",
        id: String = "diagram",
        line: Int = 7,
        body: String,
    ): PlantumlCorpusBlock = PlantumlCorpusBlock(document, id, line, "@startuml\n$body\n@enduml")

    @Test
    fun `a French class label in an English document is a finding`() {
        val finding =
            PlantumlCorpusLanguageProbe.examine(
                block(body = "class \"Utilisateur\""),
                documentLanguage = "en",
            )

        assertNotNull(finding)
        assertEquals("en/article.adoc", finding.document)
        assertEquals("en", finding.language)
        assertEquals("diagram", finding.blockId)
        assertEquals(7, finding.line)
        assertTrue(finding.evidence.contains("word:utilisateur"), finding.evidence.toString())
    }

    @Test
    fun `a French note in an English document is a finding`() {
        val finding =
            PlantumlCorpusLanguageProbe.examine(
                block(body = "A -> B\nnote right of B : Le fichier est prêt"),
                documentLanguage = "en",
            )

        assertNotNull(finding)
        assertTrue(finding.evidence.any { it.startsWith("diacritic:") }, finding.evidence.toString())
    }

    @Test
    fun `a French sequence message in an English document is a finding`() {
        val finding =
            PlantumlCorpusLanguageProbe.examine(
                block(body = "A -> B : Créer le fichier"),
                documentLanguage = "en",
            )

        assertNotNull(finding)
        assertTrue(finding.evidence.contains("word:fichier"), finding.evidence.toString())
    }

    @Test
    fun `a clean English block is not a finding`() {
        assertNull(
            PlantumlCorpusLanguageProbe.examine(
                block(body = "class \"User\"\nA -> B : Create the file"),
                documentLanguage = "en",
            ),
        )
    }

    @Test
    fun `a French document is never flagged`() {
        assertNull(
            PlantumlCorpusLanguageProbe.examine(
                block(document = "fr/article.adoc", body = "class \"Utilisateur\""),
                documentLanguage = "fr",
            ),
        )
    }

    @Test
    fun `a source language without a profile is never flagged`() {
        assertNull(
            PlantumlCorpusLanguageProbe.examine(
                block(body = "class \"Utilisateur\""),
                documentLanguage = "en",
                sourceLanguage = "de",
            ),
        )
    }

    @Test
    fun `a blank source language is never flagged`() {
        assertNull(
            PlantumlCorpusLanguageProbe.examine(
                block(body = "class \"Utilisateur\""),
                documentLanguage = "en",
                sourceLanguage = "",
            ),
        )
    }

    @Test
    fun `a blank document language is never flagged`() {
        assertNull(
            PlantumlCorpusLanguageProbe.examine(
                block(body = "class \"Utilisateur\""),
                documentLanguage = "",
            ),
        )
    }

    @Test
    fun `a code-only structural block is not a finding`() {
        assertNull(
            PlantumlCorpusLanguageProbe.examine(
                block(body = "U --> Foo.Bar"),
                documentLanguage = "en",
            ),
        )
    }

    @Test
    fun `the evidence is deduplicated across the surfaces of one block`() {
        val finding =
            PlantumlCorpusLanguageProbe.examine(
                block(body = "class \"Utilisateur\"\nnote \"Utilisateur\" as N1"),
                documentLanguage = "en",
            )

        assertNotNull(finding)
        assertEquals(1, finding.evidence.count { it == "word:utilisateur" }, finding.evidence.toString())
    }
}
