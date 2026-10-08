package plantuml.boundary

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlClassifier
import contracts.plantuml.PlantUmlStrategy
import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationPort
import contracts.plantuml.PlantUmlTranslationRequest
import contracts.plantuml.SyntaxValidationResult

/**
 * Implementation of the N0 [PlantUmlTranslationPort] (EPIC PLT-DIAGRAM-OWNERSHIP
 * US-2/D3).
 *
 * The port is **provider-agnostic**: label text is translated through an N0
 * [TranslationService]. Two providers plug into the same port:
 *
 * - generated diagrams → [PlantumlTranslationServiceAdapter] (the rich
 *   boundary `TranslationResolver` + idiomatic glossary)
 * - article blocks → the article's LLM `TranslationService`
 *   (e.g. `PooledOllamaTranslationAdapter`), because article labels are
 *   arbitrary prose, not the fixed glossary vocabulary.
 *
 * A block classified [PlantUmlStrategy.PRESERVE] is returned verbatim; otherwise
 * its labels are translated and re-escaped (D5): a real newline returned by the
 * provider becomes `\n` — PlantUML requires the escape, not a raw line break
 * (this is the CHE-DIAGRAM `Syntax Error?` root cause).
 *
 * @param translator the N0 translation provider for label text
 * @param classifier the shared N0 block classifier
 * @param validator optional N0 syntax validator — when present, a translated
 *   block that no longer parses is **rejected** (the source block is preserved,
 *   D5/US-3): a translation must never produce an unrenderable diagram
 */
class PlantumlTranslationPortAdapter(
    private val translator: TranslationService,
    private val classifier: PlantUmlClassifier = PlantUmlClassifier(),
    private val validator: PlantUmlSyntaxValidator? = null,
) : PlantUmlTranslationPort {

    override fun translate(request: PlantUmlTranslationRequest): PlantUmlTranslationOutcome {
        val block = request.block
        val strategy = classifier.classify(block)
        if (strategy == PlantUmlStrategy.PRESERVE) {
            return PlantUmlTranslationOutcome.Preserved("PRESERVE strategy — semantic identity only")
        }

        val labels = block.labels()
        if (labels.isEmpty()) {
            return PlantUmlTranslationOutcome.Preserved("no translatable label")
        }

        var raw = block.raw
        var changed = false
        for (label in labels) {
            val resolved = translateLabel(label, request.sourceLanguage, request.targetLanguage) ?: continue
            if (resolved == label) continue
            val escaped = escapeNewlines(resolved)
            // Quoted label (`class "Utilisateur"`) — replace the quoted form only.
            val quotedReplaced = raw.replace("\"$label\"", "\"$escaped\"")
            val quotedChanged = quotedReplaced != raw
            raw = quotedReplaced
            // Unquoted directive value (`title Évolution…`) — the label never
            // appears quoted, so the replacement targets it verbatim on its line.
            val directiveReplaced =
                DIRECTIVE_LINE_PATTERN.replace(raw) { match ->
                    val directive = match.groupValues[1]
                    val value = match.groupValues[2]
                    if (value == label) "$directive $escaped" else match.value
                }
            val directiveChanged = directiveReplaced != raw
            raw = directiveReplaced
            changed = changed || quotedChanged || directiveChanged
        }
        return if (changed) {
            val translated = block.copy(raw = raw)
            // US-3 — round-trip gate (D5): a translated block that no longer
            // parses must NOT be published. The source block is preserved.
            if (validator != null && validator.validate(translated.raw) is SyntaxValidationResult.Invalid) {
                PlantUmlTranslationOutcome.Preserved("translation breaks PlantUML syntax — source block preserved")
            } else {
                PlantUmlTranslationOutcome.Translated(translated)
            }
        } else {
            PlantUmlTranslationOutcome.Preserved("no label changed (PRESERVE / provider failure)")
        }
    }

    private fun translateLabel(text: String, sourceLanguage: String, targetLanguage: String): String? {
        if (text.isBlank()) return null
        return when (val result = translator.translate(TranslationRequest(text, sourceLanguage, targetLanguage))) {
            is TranslationResult.Success -> result.translatedText
            is TranslationResult.Failure -> null
        }
    }

    /**
     * Converts a raw line break produced by the translator back to the PlantUML
     * escape `\n`. Without this, `rectangle "a\nb"` becomes two lines and
     * PlantUML fails (`Syntax Error?`).
     */
    private fun escapeNewlines(text: String): String =
        text.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

    private companion object {
        /**
         * Matches an unquoted PlantUML directive line and captures its value,
         * so `title Évolution…` can be rewritten without touching quoted labels.
         */
        val DIRECTIVE_LINE_PATTERN =
            Regex("""(?m)^(\s*(?:title|header|footer|caption))\s+(.+?)\s*$""")
    }
}
