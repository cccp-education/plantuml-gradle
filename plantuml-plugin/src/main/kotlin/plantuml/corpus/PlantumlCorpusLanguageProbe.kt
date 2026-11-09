package plantuml.corpus

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import contracts.plantuml.PlantUmlBlock
import plantuml.boundary.PlantumlProseLabelExtractor
import plantuml.boundary.SourceLanguageResidue
import plantuml.repair.PlantumlNoteExtractor

/**
 * One `[plantuml]` block whose prose still looks like the source language
 * (EPIC PLT-I18N-OUTPUT, PLT-OUT-4).
 *
 * @property document corpus-relative document path
 * @property language the document language resolved from the corpus path
 * @property blockId the diagram block id (declared or `line-<n>` fallback)
 * @property line 1-based line of the `[plantuml]` attribute
 * @property evidence stable markers (`word:…`, `ratio:n/m`, `diacritic:…`)
 */
data class PlantumlCorpusLanguageFinding(
    val document: String,
    val language: String,
    val blockId: String,
    val line: Int,
    val evidence: List<String>,
) {
    fun render(): String = "$document [$language] $blockId:$line — source-language residue (${evidence.joinToString(", ")})"
}

/**
 * Non-blocking language report of a corpus gate run (PLT-OUT-4).
 *
 * This report is a **measurement**, never a gate: it says how many diagram
 * blocks still carry source-language prose, so a re-translation session can be
 * piloted by a number instead of an impression. Syntax blocking stays in
 * [PlantumlCorpusReport.blocking].
 *
 * @property sourceLanguage the language the corpus was authored in (default `fr`)
 * @property totalDocuments corpus documents scanned
 * @property totalBlocks diagram blocks scanned
 * @property findings one entry per block with a residue, ordered deterministically
 */
data class PlantumlCorpusLanguageReport(
    val sourceLanguage: String,
    val totalDocuments: Int,
    val totalBlocks: Int,
    val findings: List<PlantumlCorpusLanguageFinding> = emptyList(),
) {
    /** Number of blocks with a residue. */
    @get:JsonProperty("total")
    val total: Int get() = findings.size

    /** Human-readable section appended to the corpus report. */
    fun render(): String =
        buildString {
            appendLine("source-language: $sourceLanguage")
            appendLine("language residue (non-blocking): $total")
            if (findings.isNotEmpty()) {
                appendLine("---")
                findings.forEach { appendLine(it.render()) }
                appendLine("---")
            }
        }

    /** Pretty-printed, deterministic JSON for a re-translation session. */
    fun toJson(): String = mapper.writeValueAsString(this)

    companion object {
        private val mapper: ObjectMapper =
            jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

        val EMPTY = PlantumlCorpusLanguageReport(sourceLanguage = "", totalDocuments = 0, totalBlocks = 0)
    }
}

/**
 * Pure language probe over a corpus block (PLT-OUT-4).
 *
 * It inspects exactly the surfaces the translation port translates — quoted
 * labels/directives ([PlantUmlBlock.labels]), notes, and unquoted prose — and
 * asks the deterministic [SourceLanguageResidue] whether a surface still looks
 * like [sourceLanguage]. No LLM, no I/O, no render: a corpus can be measured
 * for free, in CI, on every document.
 */
object PlantumlCorpusLanguageProbe {
    /**
     * @return the finding when the block has at least one source-language
     *   residue surface, `null` otherwise
     */
    fun examine(
        block: PlantumlCorpusBlock,
        documentLanguage: String,
        sourceLanguage: String = "fr",
    ): PlantumlCorpusLanguageFinding? {
        if (sourceLanguage.isBlank() || documentLanguage.isBlank()) return null
        val evidence =
            surfaces(block.source)
                .flatMap { SourceLanguageResidue.detect(it, sourceLanguage, documentLanguage).evidence }
                .distinct()
        if (evidence.isEmpty()) return null
        return PlantumlCorpusLanguageFinding(
            document = block.document,
            language = documentLanguage,
            blockId = block.id,
            line = block.line,
            evidence = evidence,
        )
    }

    private fun surfaces(source: String): List<String> {
        val labels = PlantUmlBlock(source).labels()
        val notes = PlantumlNoteExtractor.occurrences(source).map { it.text }
        val prose = PlantumlProseLabelExtractor.occurrences(source).map { it.text }
        return (labels + notes + prose)
            .filter { it.isNotBlank() }
            .distinct()
    }
}
