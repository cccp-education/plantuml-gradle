package plantuml.boundary

import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlClassifier
import contracts.plantuml.PlantUmlStrategy
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationPort
import contracts.plantuml.PlantUmlTranslationRequest

/**
 * Implementation of the N0 [PlantUmlTranslationPort] (EPIC PLT-DIAGRAM-OWNERSHIP
 * US-2/D3).
 *
 * The rich plantuml.boundary domain ([TranslationResolver] + [TextClassifier] +
 * idiomatic glossary + non-translatable registry) finally becomes the single
 * engine that document-gradle delegates to. A block classified as
 * [PlantUmlStrategy.PRESERVE] is returned verbatim; otherwise its labels are
 * resolved through the boundary pipeline.
 *
 * D5 (never break syntax): a translated label is re-escaped so a real newline
 * returned by the LLM becomes `\n` — PlantUML requires the escape, not a raw
 * line break. (The round-trip validation is US-3.)
 *
 * @param resolver the boundary resolver (classification + strategy + glossary)
 * @param classifier the shared N0 block classifier
 */
class PlantumlTranslationPortAdapter(
    private val resolver: TranslationResolver,
    private val classifier: PlantUmlClassifier = PlantUmlClassifier(),
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
            val resolved = resolver.resolve(label, request.targetLanguage).translated
            if (resolved == label) continue
            val escaped = escapeNewlines(resolved)
            raw = raw.replace("\"$label\"", "\"$escaped\"")
            changed = true
        }
        return if (changed) {
            PlantUmlTranslationOutcome.Translated(block.copy(raw = raw))
        } else {
            PlantUmlTranslationOutcome.Preserved("no label changed (PRESERVE / unknown language)")
        }
    }

    /**
     * Converts a raw line break produced by the translator back to the PlantUML
     * escape `\n`. Without this, `rectangle "a\nb"` becomes two lines and
     * PlantUML fails (`Syntax Error?`).
     */
    private fun escapeNewlines(text: String): String =
        text.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
}
