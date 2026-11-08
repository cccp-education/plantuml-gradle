package plantuml.boundary

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PLT-OUT-3 — deterministic source-language residue detector.
 *
 * No LLM, no I/O: the detector only reads the translated text and the language
 * pair. The bias is deliberate (see `.agents/CADRAGE_PLT_OUT_3_SHIELD_LANGUE.adoc`):
 * a false positive preserves the source, a false negative would publish a label
 * silently left in French.
 */
class SourceLanguageResidueTest {
    private fun detected(
        text: String,
        source: String = "fr",
        target: String = "en",
    ): Boolean = SourceLanguageResidue.detect(text, source, target).detected

    @Test
    fun `a French label left untouched is detected`() {
        assertTrue(detected("Utilisateur"))
    }

    @Test
    fun `a short French label with a diacritic is detected`() {
        assertTrue(detected("Rédiger"))
    }

    @Test
    fun `a French sentence with stopwords is detected`() {
        assertTrue(detected("Le fichier est prêt pour vous"))
    }

    @Test
    fun `a French activity left in the target text is detected`() {
        assertTrue(detected("Créer le fichier"))
    }

    @Test
    fun `an English translation is not detected`() {
        assertFalse(detected("The file is ready for you"))
    }

    @Test
    fun `a short English label is not detected`() {
        assertFalse(detected("User"))
    }

    @Test
    fun `a French-only noun without a diacritic is detected`() {
        assertTrue(detected("Connexion"))
    }

    @Test
    fun `an ambiguous shared word is not detected`() {
        assertFalse(detected("Service"))
    }

    @Test
    fun `the English word comment does not trigger the detector`() {
        assertFalse(detected("Add a comment to the file"))
    }

    @Test
    fun `the English verb pour does not trigger the detector`() {
        assertFalse(detected("Pour the water"))
    }

    @Test
    fun `shield placeholders are ignored by the detector`() {
        assertFalse(detected("The __PLT0__ is used"))
        assertTrue(detected("Le __PLT0__ est utilisé"))
    }

    @Test
    fun `a source language without a profile is never flagged`() {
        assertFalse(detected("Créer le fichier", source = "en", target = "fr"))
        assertFalse(detected("Créer le fichier", source = "es", target = "en"))
    }

    @Test
    fun `a French target is never flagged`() {
        assertFalse(detected("Créer le fichier", source = "fr", target = "fr"))
    }

    @Test
    fun `regional language tags are normalized`() {
        assertTrue(detected("Utilisateur", source = "fr-FR", target = "en-US"))
        assertFalse(detected("User", source = "fr-FR", target = "en-US"))
    }

    @Test
    fun `the evidence names the matched marker deterministically`() {
        val residue = SourceLanguageResidue.detect("Utilisateur", "fr", "en")
        assertEquals(listOf("word:utilisateur"), residue.evidence)
    }

    @Test
    fun `a non-detected text exposes no evidence`() {
        assertTrue(SourceLanguageResidue.detect("User", "fr", "en").evidence.isEmpty())
    }

    @Test
    fun `blank text is never a residue`() {
        assertFalse(detected("   "))
    }
}
