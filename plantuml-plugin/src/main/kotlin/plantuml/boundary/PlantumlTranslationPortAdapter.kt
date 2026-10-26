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
import plantuml.repair.PlantumlLabelIntegrity
import plantuml.repair.PlantumlNoteExtractor

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
    private val termShield: PlantumlTermShield = PlantumlTermShield(),
) : PlantUmlTranslationPort {

    override fun translate(request: PlantUmlTranslationRequest): PlantUmlTranslationOutcome {
        val block = request.block
        val labels = block.labels()
        val notes = PlantumlNoteExtractor.occurrences(block.raw)
        val proseLabels = PlantumlProseLabelExtractor.occurrences(block.raw)
        val strategy = classifier.classify(block)
        // GBL-010 G-3 — notes are translatable prose that the N0 label classifier
        // does not see: a notes-bearing block must not be short-circuited as
        // PRESERVE (semantic identity only). PLT-TERM-PROTECTION adds the unquoted
        // prose labels (sequence messages, activities, state descriptions).
        if (strategy == PlantUmlStrategy.PRESERVE && notes.isEmpty() && proseLabels.isEmpty()) {
            return PlantUmlTranslationOutcome.Preserved("PRESERVE strategy — semantic identity only")
        }
        if (labels.isEmpty() && notes.isEmpty() && proseLabels.isEmpty()) {
            return PlantUmlTranslationOutcome.Preserved("no translatable label")
        }

        // S-073 — one batched provider call for the whole diagram (falls back to
        // per-label when the provider cannot batch). The run bottleneck was the
        // per-label round-trip: a 20-label diagram cost 20 LLM calls.
        val translations = if (labels.isEmpty()) emptyMap() else translateLabels(labels, request)

        var raw = block.raw
        var changed = false
        for (label in labels) {
            val resolved = translations[label] ?: continue
            if (resolved == label) continue
            val escaped = PlantumlLabelIntegrity.escapeNewlines(resolved)
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

        // GBL-010 G-3 — translate the note prose in place. The colon form
        // (`note right: text`) is ONE physical line in PlantUML: a real newline
        // returned by the provider breaks the diagram (`Syntax Error?`). Only the
        // block form (`note right … end note`) allows real line breaks. This is
        // the CHE-DIAGRAM variant corruption (S-076).
        for (note in notes) {
            val resolved = translateProtected(note.text, request) ?: continue
            if (resolved == note.text) continue
            val replacement = if (note.singleLine) PlantumlLabelIntegrity.escapeNewlines(resolved) else resolved
            val replaced = raw.replace(note.raw, replacement)
            if (replaced != raw) {
                raw = replaced
                changed = true
            }
        }

        // PLT-TERM-PROTECTION — translate the unquoted prose labels the N0 label
        // surface misses (sequence messages, activities, state descriptions). Code
        // and business tokens are shielded; a lost placeholder preserves the source.
        for (label in proseLabels) {
            val resolved = translateProtected(label.text, request) ?: continue
            if (resolved == label.text) continue
            val escaped = PlantumlLabelIntegrity.escapeNewlines(resolved)
            val newLine = label.line.replace(label.text, escaped)
            if (newLine != label.line) {
                val replaced = raw.replace(label.line, newLine)
                if (replaced != raw) {
                    raw = replaced
                    changed = true
                }
            }
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

    /**
     * S-073 — translates all [labels] in **one** provider call ([PlantumlLabelBatch]),
     * falling back to the per-label path whenever the provider cannot batch (a
     * single label, a missing sentinel, or a mismatched count). A batching failure
     * is thus never a translation loss.
     */
    private fun translateLabels(
        labels: List<String>,
        request: PlantUmlTranslationRequest,
    ): Map<String, String> {
        val shields = labels.associateWith { termShield.shield(it) }
        val translatable = labels.filter { label -> !shields.getValue(label).isFullyShielded }
        if (translatable.isEmpty()) return emptyMap()

        if (translatable.size == 1) {
            val label = translatable.single()
            return translateProtected(label, request)?.let { mapOf(label to it) } ?: emptyMap()
        }

        val joined = PlantumlLabelBatch.join(translatable.map { shields.getValue(it).text })
        val result = translator.translate(TranslationRequest(joined, request.sourceLanguage, request.targetLanguage))
        if (result is TranslationResult.Success) {
            val parts = PlantumlLabelBatch.split(result.translatedText, translatable.size)
            if (parts != null) {
                val restored =
                    translatable.zip(parts).mapNotNull { (label, part) ->
                        termShield.restore(part, shields.getValue(label))?.let { label to it }
                    }.toMap()
                if (restored.size == translatable.size) return restored
            }
        }
        return translatable
            .mapNotNull { label ->
                translateProtected(label, request)?.let { label to it }
            }.toMap()
    }

    /**
     * Shields the protected tokens of [text], translates the remaining prose, and
     * restores the tokens. A fully shielded text (code only) is returned as-is —
     * no provider call, no metered cost. A lost placeholder returns `null` so the
     * caller preserves the source label (never a corrupted diagram).
     */
    private fun translateProtected(
        text: String,
        request: PlantUmlTranslationRequest,
    ): String? {
        if (text.isBlank()) return null
        val shield = termShield.shield(text)
        if (shield.isFullyShielded) return text
        val translated = translateLabel(shield.text, request.sourceLanguage, request.targetLanguage) ?: return null
        return termShield.restore(translated, shield)
    }

    private fun translateLabel(text: String, sourceLanguage: String, targetLanguage: String): String? {
        if (text.isBlank()) return null
        return when (val result = translator.translate(TranslationRequest(text, sourceLanguage, targetLanguage))) {
            is TranslationResult.Success -> result.translatedText
            is TranslationResult.Failure -> null
        }
    }

    private companion object {
        /**
         * Matches an unquoted PlantUML directive line and captures its value,
         * so `title Évolution…` can be rewritten without touching quoted labels.
         */
        val DIRECTIVE_LINE_PATTERN =
            Regex("""(?m)^(\s*(?:title|header|footer|caption))\s+(.+?)\s*$""")
    }
}
