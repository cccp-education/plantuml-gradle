package plantuml.context

import contracts.context.ChannelBudget
import contracts.context.CompositeContext
import contracts.context.CompositeContextConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * CTX-02 — pure assembler of the plantuml augmented context (N0 contract).
 */
class PlantumlContextBuilderTest {

    private fun composite(
        eager: String = "",
        rag: String = "",
        graphify: String = "",
        docs: String = "",
    ) = CompositeContext(
        eagerSection = eager,
        ragSection = rag,
        graphifySection = graphify,
        docsSection = docs,
        config = CompositeContextConfig(),
    )

    @Test
    fun `renders non-empty channels with their section headers`() {
        val context = PlantumlContextBuilder.build(composite(eager = "rules", rag = "chunk"))
        assertThat(context.rendered).contains("==== RÈGLES_EAGER (EAGER/LAZY)")
        assertThat(context.rendered).contains("rules")
        assertThat(context.rendered).contains("==== CONTEXTE_RAG (RAG/pgvector)")
        assertThat(context.rendered).contains("chunk")
        assertThat(context.nonEmptyCount).isEqualTo(2)
    }

    @Test
    fun `drops empty channels`() {
        val context = PlantumlContextBuilder.build(composite(rag = "only-rag"))
        assertThat(context.nonEmptyCount).isEqualTo(1)
        assertThat(context.rendered).doesNotContain("RÈGLES_EAGER")
    }

    @Test
    fun `an all-blank composite yields an empty context`() {
        val context = PlantumlContextBuilder.build(composite())
        assertThat(context.isEmpty).isTrue()
        assertThat(context.rendered).isBlank()
    }

    @Test
    fun `channels follow the contract order EAGER RAG GRAPHIFY DOCS`() {
        val context = PlantumlContextBuilder.build(
            composite(eager = "e", rag = "r", graphify = "g", docs = "d"),
        )
        assertThat(context.channels.map { it.type.name })
            .containsExactly("EAGER", "RAG", "GRAPHIFY", "DOCS")
    }

    @Test
    fun `the budget truncates an oversized channel`() {
        val long = (1..3000).joinToString("\n") { "xxxxxxxxxx" }
        val context = PlantumlContextBuilder.build(composite(rag = long))
        assertThat(context.rendered).contains("CONTEXTE_RAG")
        assertThat(context.rendered.length).isLessThan(long.length)
    }

    @Test
    fun `default budget is the N0 budget`() {
        assertThat(PlantumlContextBuilder.defaultBudget()).isEqualTo(ChannelBudget())
    }

    @Test
    fun `the builder prunes the tracker to the surviving channels`() {
        val tracker = ProvenanceTracker()
        tracker.trackChannel("EAGER", listOf(ProvenanceSource("INDEX.adoc", chars = 10, tokens = 2)))
        tracker.trackChannel("RAG", listOf(ProvenanceSource("rag-injected", chars = 20, tokens = 5)))
        tracker.trackChannel("GRAPHIFY", listOf(ProvenanceSource("graphify-injected", chars = 30, tokens = 8)))

        PlantumlContextBuilder.build(composite(rag = "only-rag"), tracker = tracker)

        assertThat(tracker.build().channels.map { it.channel }).containsExactly("RAG")
    }

    @Test
    fun `a null tracker leaves the build untracked`() {
        val context = PlantumlContextBuilder.build(composite(eager = "rules"))
        assertThat(context.nonEmptyCount).isEqualTo(1)
    }
}
