package plantuml.tasks

import contracts.context.ChannelBudget
import contracts.context.ChannelType
import contracts.context.CompositeContext
import contracts.context.CompositeContextConfig
import contracts.context.ContextChannel
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import plantuml.context.ContextProvenance
import plantuml.context.PlantumlContextBuilder
import plantuml.context.ProvenanceSource
import plantuml.context.ProvenanceTracker
import java.io.File

/**
 * Gradle task: `collectPlantumlAugmentedContext`
 *
 * Collects the augmented context that feeds PlantUML generation
 * (EPIC PLT-CONTEXT, CTX-03): the EAGER governance files of the consumer
 * project (`INDEX.adoc`, `PROMPT_REPRISE.adoc`, `AGENT.adoc`) plus the optional
 * RAG / Graphify / Docs channels injected via Gradle properties. The raw
 * sections are assembled into a [contracts.context.CompositeContext], budgeted
 * and rendered through [PlantumlContextBuilder], then written to the output
 * artefact `build/plantuml/augmented-context.txt`.
 *
 * The wiring layer (CTX-04) will consume this artefact — never the raw
 * contract. Task inputs/outputs are declared Gradle-native, so the build is
 * UP-TO-DATE when nothing changed (law of economy of ink — AGENT.adoc).
 *
 * RAG / Graphify / Docs channels are injected via `-Pcontext.*` properties
 * (mockable in tests, codebase pgvector integration is out of scope here):
 *   - `-Pcontext.ragContent=...`      RAG pgvector section
 *   - `-Pcontext.graphifyContent=...` Graphify relations section
 *   - `-Pcontext.docsContent=...`     Codex/documentary section
 *   - `-Pcontext.tokenBudget=...`     total token budget (default 8000)
 *
 * The provenance artefact `context-provenance.json` is the anti-hallucination
 * audit trail (decision D-6), mirroring the capsule patron.
 */
@DisableCachingByDefault(because = "Augmented context collection — governance files, non-cacheable")
abstract class CollectPlantumlAugmentedContextTask : DefaultTask() {

    /** EAGER governance files of the consumer project (missing files skipped). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val eagerFiles: ConfigurableFileCollection

    /** RAG pgvector section content (optional). */
    @get:Input
    @get:Optional
    abstract val ragContent: Property<String>

    /** Graphify relations section content (optional). */
    @get:Input
    @get:Optional
    abstract val graphifyContent: Property<String>

    /** Codex/documentary section content (optional). */
    @get:Input
    @get:Optional
    abstract val docsContent: Property<String>

    /** Total token budget applied across the channels. */
    @get:Input
    abstract val tokenBudget: Property<Int>

    /** Rendered augmented context artefact. */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * Provenance artefact (decision D-6): `build/plantuml/context-provenance.json`.
     *
     * A serialized [ContextProvenance] snapshot describing which sources fed the
     * channels that survived the token budget — the anti-hallucination audit
     * trail of the augmented context.
     */
    @get:OutputFile
    abstract val provenanceFile: RegularFileProperty

    @TaskAction
    fun run() {
        val eager = eagerFiles.files
            .filter { it.exists() }
            .sortedBy { it.name }
            .joinToString("\n\n") { file ->
                "--- ${file.name} ---\n${file.readText().trim()}"
            }

        val budget = ChannelBudget(totalTokenBudget = tokenBudget.get())

        val composite = CompositeContext(
            eagerSection = eager,
            ragSection = ragContent.orNull.orEmpty(),
            graphifySection = graphifyContent.orNull.orEmpty(),
            docsSection = docsContent.orNull.orEmpty(),
            config = CompositeContextConfig(),
        )

        val tracker = ProvenanceTracker()
        trackSources(tracker)
        val context = PlantumlContextBuilder.build(
            composite = composite,
            budget = budget,
            tracker = tracker,
        )

        val output = outputFile.asFile.get()
        output.parentFile.mkdirs()
        output.writeText(context.rendered)

        val provenance = tracker.build()
        val provenanceOut = provenanceFile.asFile.get()
        provenanceOut.parentFile.mkdirs()
        provenanceOut.writeText(provenance.toJson())

        logger.lifecycle(
            "PLANTUML CONTEXT -> ${context.nonEmptyCount} non-empty channels, " +
                "~${context.tokenEstimate} tokens -> ${output.absolutePath}",
        )
        logger.lifecycle(
            "PLANTUML PROVENANCE -> ${provenance.channels.size} channels, " +
                "${provenance.channels.sumOf { it.sources.size }} sources -> ${provenanceOut.absolutePath}",
        )
        if (context.isEmpty) {
            logger.warn("PLANTUML CONTEXT -> no EAGER/RAG/Graphify/Docs content collected (empty augmented context)")
        }
    }

    /**
     * Tracks the per-channel sources into [tracker] (decision D-6).
     *
     * The builder prunes the tracker to the channels that survived the token
     * budget via [ProvenanceTracker.retainOnly] — a channel truncated to zero
     * content is dropped from the provenance. Source measurement: raw chars
     * and token estimate via the N0 [ContextChannel.estimateTokens] heuristic,
     * both taken from a single read.
     */
    private fun provenanceOf(file: File): ProvenanceSource {
        val text = file.readText()
        return ProvenanceSource(
            fileName = file.name,
            chars = text.length,
            tokens = ContextChannel.estimateTokens(text),
        )
    }

    private fun trackSources(tracker: ProvenanceTracker) {
        val eagerSources = eagerFiles.files.filter { it.exists() }.sortedBy { it.name }.map(::provenanceOf)
        tracker.trackChannel(ChannelType.EAGER.name, eagerSources)

        val rag = ragContent.orNull.orEmpty()
        if (rag.isNotBlank()) {
            tracker.trackChannel(ChannelType.RAG.name, listOf(injected("rag-injected", rag)))
        }

        val graphify = graphifyContent.orNull.orEmpty()
        if (graphify.isNotBlank()) {
            tracker.trackChannel(ChannelType.GRAPHIFY.name, listOf(injected("graphify-injected", graphify)))
        }

        val docs = docsContent.orNull.orEmpty()
        if (docs.isNotBlank()) {
            tracker.trackChannel(ChannelType.DOCS.name, listOf(injected("docs-injected", docs)))
        }
    }

    private fun injected(label: String, content: String): ProvenanceSource =
        ProvenanceSource(
            fileName = label,
            chars = content.length,
            tokens = ContextChannel.estimateTokens(content),
        )
}
