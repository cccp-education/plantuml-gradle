package plantuml.corpus

import contracts.i18n.LanguageCatalog
import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult

/**
 * One invalid corpus block, named by document, language, block id and reason
 * (EPIC PLT-I18N-OUTPUT, PLT-OUT-1).
 */
data class PlantumlCorpusFinding(
    val document: String,
    val language: String,
    val blockId: String,
    val line: Int,
    val reason: String,
) {
    /** Stable baseline key: document, block id and reason (tab separated). */
    val baselineKey: String
        get() = "$document\t$blockId\t$reason"

    fun render(): String = "$document [$language] $blockId:$line — $reason"
}

/**
 * Named baseline of tolerated corpus findings (a debt being paid down).
 *
 * A line is either an exact key (`document<TAB>blockId<TAB>reason`) or a
 * document+block pair (`document<TAB>blockId`) that accepts any reason.
 * Blank lines and `#` comments are ignored; the format is deterministic so it
 * can be committed and reviewed.
 */
class PlantumlCorpusBaseline private constructor(
    private val exactKeys: Set<String>,
    private val documentBlocks: Set<String>,
) {
    fun accepts(finding: PlantumlCorpusFinding): Boolean = finding.baselineKey in exactKeys || "${finding.document}\t${finding.blockId}" in documentBlocks

    companion object {
        val EMPTY = PlantumlCorpusBaseline(emptySet(), emptySet())

        fun fromLines(lines: List<String>): PlantumlCorpusBaseline {
            val exact = mutableSetOf<String>()
            val documentBlocks = mutableSetOf<String>()
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { line ->
                    when (line.split('\t', limit = 3).size) {
                        2 -> documentBlocks += line
                        3 -> exact += line
                    }
                }
            return PlantumlCorpusBaseline(exact, documentBlocks)
        }
    }
}

/**
 * Deterministic outcome of a corpus gate run.
 *
 * @property languageReport the non-blocking language measurement (PLT-OUT-4) —
 *   it never influences [isClean], which stays the syntax gate
 */
data class PlantumlCorpusReport(
    val totalDocuments: Int,
    val totalBlocks: Int,
    val findings: List<PlantumlCorpusFinding>,
    val baselined: List<PlantumlCorpusFinding>,
    val blocking: List<PlantumlCorpusFinding>,
    val languageReport: PlantumlCorpusLanguageReport = PlantumlCorpusLanguageReport.EMPTY,
) {
    val isClean: Boolean get() = blocking.isEmpty()

    fun render(): String =
        buildString {
            appendLine("PlantUML corpus validation")
            appendLine("documents: $totalDocuments")
            appendLine("blocks: $totalBlocks")
            appendLine("invalid: ${findings.size}")
            appendLine("baselined: ${baselined.size}")
            appendLine("blocking: ${blocking.size}")
            if (findings.isNotEmpty()) {
                appendLine("---")
                findings.forEach { appendLine(it.render()) }
                appendLine("--- suggested baseline")
                findings.forEach { appendLine(it.baselineKey) }
            }
            append(languageReport.render())
        }
}

/**
 * Blocking corpus gate (EPIC PLT-I18N-OUTPUT, PLT-OUT-1).
 *
 * The N0 [PlantUmlSyntaxValidator] is applied to **every** `[plantuml]` block
 * of **every** document — no longer only to a unit fixture. Findings are
 * ordered by document, line and block id so the report is reproducible; a
 * finding is blocking unless the named [PlantumlCorpusBaseline] accepts it.
 *
 * PLT-OUT-4 adds a **non-blocking** language measurement: every block is probed
 * for source-language prose with [PlantumlCorpusLanguageProbe]. A residue is
 * reported (text + JSON), never a blocking finding — the syntax gate's verdict
 * is unchanged.
 */
object PlantumlCorpusGate {
    fun evaluate(
        documents: Map<String, String>,
        validator: PlantUmlSyntaxValidator,
        baseline: PlantumlCorpusBaseline = PlantumlCorpusBaseline.EMPTY,
        supportedCodes: Set<String> = LanguageCatalog.supportedCodes(),
        sourceLanguage: String = "fr",
    ): PlantumlCorpusReport {
        val blocks =
            documents.entries
                .sortedBy { it.key }
                .flatMap { (document, content) ->
                    val language = CorpusLanguageResolver.resolve(document, supportedCodes)
                    AsciiDocPlantumlCorpusScanner.scan(document, content).map { it to language }
                }

        val findings =
            blocks
                .mapNotNull { (block, language) ->
                    val reason = invalidReason(validator, block)
                    reason?.let {
                        PlantumlCorpusFinding(
                            document = block.document,
                            language = language,
                            blockId = block.id,
                            line = block.line,
                            reason = it,
                        )
                    }
                }.sortedWith(compareBy({ it.document }, { it.line }, { it.blockId }))

        val languageFindings =
            blocks
                .mapNotNull { (block, language) ->
                    PlantumlCorpusLanguageProbe.examine(block, language, sourceLanguage)
                }.sortedWith(compareBy({ it.document }, { it.line }, { it.blockId }))

        return PlantumlCorpusReport(
            totalDocuments = documents.size,
            totalBlocks = blocks.size,
            findings = findings,
            baselined = findings.filter { baseline.accepts(it) },
            blocking = findings.filterNot { baseline.accepts(it) },
            languageReport =
                PlantumlCorpusLanguageReport(
                    sourceLanguage = sourceLanguage,
                    totalDocuments = documents.size,
                    totalBlocks = blocks.size,
                    findings = languageFindings,
                ),
        )
    }

    private fun invalidReason(
        validator: PlantUmlSyntaxValidator,
        block: PlantumlCorpusBlock,
    ): String? =
        runCatching { validator.validate(block.source) }
            .fold(
                onSuccess = { result ->
                    (result as? SyntaxValidationResult.Invalid)?.let { firstLine(it.errorMessage) }
                },
                onFailure = { error ->
                    firstLine(error.message ?: error::class.simpleName ?: "validation failed")
                },
            )

    private fun firstLine(message: String): String =
        message
            .lineSequence()
            .first()
            .replace('\t', ' ')
            .trim()
            .ifBlank { "PlantUML syntax is invalid" }
}
