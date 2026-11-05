package plantuml.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import plantuml.PlantumlManager
import plantuml.PlantumlMessages
import plantuml.boundary.PlantumlSyntaxValidatorAdapter
import plantuml.corpus.PlantumlCorpusBaseline
import plantuml.corpus.PlantumlCorpusGate
import java.nio.file.Path

/**
 * Gradle task: `validatePlantumlCorpus` (EPIC PLT-I18N-OUTPUT, PLT-OUT-1).
 *
 * Turns the N0 syntax validator into a **blocking gate on the real corpus**:
 * every `[plantuml]` block of every `.adoc` document under the corpus root is
 * validated, and the deterministic report names the language, the article, the
 * block id and the reason. A named baseline tolerates the known violations of
 * a debt being paid down; any new violation fails the build.
 *
 * Usage:
 * ```
 * ./gradlew validatePlantumlCorpus -Pplantuml.corpusDir=docs
 * ./gradlew validatePlantumlCorpus -Pplantuml.corpusBaseline=plantuml-corpus-baseline.txt
 * ./gradlew validatePlantumlCorpus -Pplantuml.corpusStrict=false
 * ```
 *
 * Artefacts:
 * - `build/plantuml/plantuml-corpus-report.txt` — human-readable report;
 * - `build/plantuml/plantuml-corpus-baseline.txt` — suggested baseline of the
 *   current findings (copy the accepted lines into the named baseline).
 */
@DisableCachingByDefault(because = "Corpus gate reflects the consumer working tree and must never be replayed from cache")
abstract class ValidatePlantumlCorpusTask : DefaultTask() {
    private val lang: String = PlantumlManager.resolveLanguage(project)

    init {
        group = PlantumlMessages.get("task.corpus.group", lang)
        description = PlantumlMessages.get("task.corpus.description", lang)
    }

    /** Corpus root used to relativize document paths in the report. */
    @get:Input
    abstract val corpusRoot: Property<String>

    /** AsciiDoc documents to scan (`*.adoc`, `*.asciidoc`, `*.ad`). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val corpusFiles: ConfigurableFileCollection

    /** Optional named baseline (tolerant input: absent file means empty baseline). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFiles: ConfigurableFileCollection

    /** When true (default), a non-baselined finding fails the build. */
    @get:Input
    abstract val strict: Property<Boolean>

    /** Human-readable, deterministic corpus report. */
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    /** Suggested baseline built from the current findings. */
    @get:OutputFile
    abstract val suggestedBaselineFile: RegularFileProperty

    @TaskAction
    fun validateCorpus() {
        val root = project.file(corpusRoot.get()).toPath()
        val baseline = PlantumlCorpusBaseline.fromLines(readBaseline())
        val documents =
            corpusFiles.files
                .filter { it.isFile }
                .sortedBy { it.absolutePath }
                .associate { relativeDocument(root, it.toPath()) to it.readText() }

        val report = PlantumlCorpusGate.evaluate(documents, PlantumlSyntaxValidatorAdapter(), baseline)
        write(reportFile.get().asFile, report.render())
        write(
            suggestedBaselineFile.get().asFile,
            report.findings.joinToString(separator = "\n", postfix = if (report.findings.isEmpty()) "" else "\n") {
                it.baselineKey
            },
        )

        if (documents.isEmpty()) {
            logger.lifecycle(PlantumlMessages.get("corpus.no_blocks", lang))
        }
        logger.lifecycle(
            PlantumlMessages.format(
                "corpus.summary",
                lang,
                report.totalDocuments,
                report.totalBlocks,
                report.findings.size,
                report.baselined.size,
                report.blocking.size,
            ),
        )

        if (strict.get() && !report.isClean) {
            val first = report.blocking.first()
            throw GradleException(
                PlantumlMessages.format(
                    "corpus.failed",
                    lang,
                    report.blocking.size,
                    first.render(),
                    reportFile.get().asFile.absolutePath,
                ),
            )
        }
    }

    private fun readBaseline(): List<String> =
        baselineFiles
            .files
            .firstOrNull()
            ?.readLines()
            .orEmpty()

    private fun relativeDocument(
        root: Path,
        file: Path,
    ): String =
        runCatching {
            root
                .toAbsolutePath()
                .normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/')
        }.getOrDefault(file.fileName.toString())

    private fun write(
        file: java.io.File,
        content: String,
    ) {
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}
