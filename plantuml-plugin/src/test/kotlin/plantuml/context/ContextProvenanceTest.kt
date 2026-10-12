package plantuml.context

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the `plantuml.context` provenance model
 * (EPIC PLT-CONTEXT, CTX-03, decision D-6).
 *
 * Baby-step TDD (RED → GREEN): [ProvenanceSource], [ProvenanceChannel],
 * [ContextProvenance] and [ProvenanceTracker] are the immutable data model +
 * mutable accumulator behind the `context-provenance.json` artefact written by
 * [plantuml.tasks.CollectPlantumlAugmentedContextTask]. They are pure — no I/O,
 * no Gradle, no network.
 */
class ContextProvenanceTest {

    private val mapper: ObjectMapper = ObjectMapper()

    // ─── ProvenanceSource ───────────────────────────────────────────────

    @Test
    fun `ProvenanceSource defaults chars and tokens to zero`() {
        val source = ProvenanceSource(fileName = "AGENT.adoc")
        assertEquals("AGENT.adoc", source.fileName)
        assertEquals(0, source.chars)
        assertEquals(0, source.tokens)
    }

    @Test
    fun `ProvenanceSource preserves the supplied fields`() {
        val source = ProvenanceSource(fileName = "AGENT.adoc", chars = 120, tokens = 30)
        assertEquals("AGENT.adoc", source.fileName)
        assertEquals(120, source.chars)
        assertEquals(30, source.tokens)
    }

    // ─── ProvenanceChannel ──────────────────────────────────────────────

    @Test
    fun `ProvenanceChannel aggregates an empty source list to zero`() {
        val channel = ProvenanceChannel(channel = "RAG", sources = emptyList())
        assertEquals(0, channel.totalChars())
        assertEquals(0, channel.totalTokens())
    }

    @Test
    fun `ProvenanceChannel sums chars and tokens across sources`() {
        val channel = ProvenanceChannel(
            channel = "EAGER",
            sources = listOf(
                ProvenanceSource("INDEX.adoc", chars = 120, tokens = 30),
                ProvenanceSource("AGENT.adoc", chars = 80, tokens = 20),
            ),
        )
        assertEquals(200, channel.totalChars())
        assertEquals(50, channel.totalTokens())
    }

    // ─── ContextProvenance ──────────────────────────────────────────────

    @Test
    fun `ContextProvenance toJson of an empty provenance yields empty channels and zero totals`() {
        val json: JsonNode = mapper.readTree(ContextProvenance().toJson())
        assertTrue(json["channels"].isArray, "channels must be an array")
        assertEquals(0, json["channels"].size())
        assertEquals(0, json["totalChars"].asInt())
        assertEquals(0, json["totalTokens"].asInt())
    }

    @Test
    fun `ContextProvenance toJson serialises channels with sources and totals`() {
        val provenance = ContextProvenance(
            channels = listOf(
                ProvenanceChannel(
                    channel = "EAGER",
                    sources = listOf(ProvenanceSource("INDEX.adoc", chars = 120, tokens = 30)),
                ),
            ),
        )
        val json: JsonNode = mapper.readTree(provenance.toJson())
        val channels = json["channels"]
        assertEquals(1, channels.size())
        assertEquals("EAGER", channels[0]["channel"].asText())
        assertEquals(1, channels[0]["sources"].size())
        assertEquals("INDEX.adoc", channels[0]["sources"][0]["fileName"].asText())
        assertEquals(120, channels[0]["sources"][0]["chars"].asInt())
        assertEquals(30, channels[0]["sources"][0]["tokens"].asInt())
        assertEquals(120, json["totalChars"].asInt())
        assertEquals(30, json["totalTokens"].asInt())
    }

    @Test
    fun `ContextProvenance aggregates totals across channels`() {
        val provenance = ContextProvenance(
            channels = listOf(
                ProvenanceChannel("EAGER", listOf(ProvenanceSource("a.adoc", chars = 100, tokens = 25))),
                ProvenanceChannel("RAG", listOf(ProvenanceSource("rag-injected", chars = 60, tokens = 15))),
                ProvenanceChannel("DOCS", listOf(ProvenanceSource("b.adoc", chars = 40, tokens = 10))),
            ),
        )
        assertEquals(200, provenance.totalChars)
        assertEquals(50, provenance.totalTokens)
    }

    // ─── ProvenanceTracker ──────────────────────────────────────────────

    @Test
    fun `ProvenanceTracker build returns an empty provenance when nothing tracked`() {
        val provenance = ProvenanceTracker().build()
        assertTrue(provenance.channels.isEmpty())
        assertEquals(0, provenance.totalChars)
        assertEquals(0, provenance.totalTokens)
    }

    @Test
    fun `ProvenanceTracker build freezes an immutable snapshot`() {
        val tracker = ProvenanceTracker()
        tracker.trackChannel(
            "EAGER",
            listOf(ProvenanceSource("INDEX.adoc", chars = 120, tokens = 30)),
        )
        val snapshot = tracker.build()
        assertEquals(1, snapshot.channels.size)

        tracker.trackChannel("RAG", listOf(ProvenanceSource("rag-injected", chars = 60, tokens = 15)))
        assertEquals(1, snapshot.channels.size, "snapshot must not mutate after later trackChannel calls")
        assertEquals("EAGER", snapshot.channels.single().channel)

        val rebuilt = tracker.build()
        assertEquals(listOf("EAGER", "RAG"), rebuilt.channels.map { it.channel })
    }

    @Test
    fun `ProvenanceTracker trackChannel ignores an empty source list`() {
        val tracker = ProvenanceTracker()
        tracker.trackChannel("RAG", emptyList())
        assertTrue(tracker.build().channels.isEmpty())
    }

    @Test
    fun `ProvenanceTracker trackChannel replaces sources for a repeated channel`() {
        val tracker = ProvenanceTracker()
        tracker.trackChannel("EAGER", listOf(ProvenanceSource("INDEX.adoc", chars = 120, tokens = 30)))
        tracker.trackChannel("EAGER", listOf(ProvenanceSource("AGENT.adoc", chars = 80, tokens = 20)))
        val snapshot = tracker.build()
        assertEquals(1, snapshot.channels.size)
        assertEquals(listOf("AGENT.adoc"), snapshot.channels.single().sources.map { it.fileName })
    }

    @Test
    fun `ProvenanceTracker retainOnly prunes channels not in the surviving set`() {
        val tracker = ProvenanceTracker()
        tracker.trackChannel("EAGER", listOf(ProvenanceSource("a.adoc", chars = 10, tokens = 2)))
        tracker.trackChannel("RAG", listOf(ProvenanceSource("rag-injected", chars = 20, tokens = 4)))
        tracker.trackChannel("DOCS", listOf(ProvenanceSource("b.adoc", chars = 30, tokens = 6)))
        tracker.retainOnly(setOf("EAGER", "RAG"))
        assertEquals(listOf("EAGER", "RAG"), tracker.build().channels.map { it.channel })
    }
}
