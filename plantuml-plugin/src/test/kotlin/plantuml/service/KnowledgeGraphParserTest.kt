package plantuml.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import plantuml.EdgeType
import plantuml.PlantumlMessages
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class KnowledgeGraphParserTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var graphFile: File
    private lateinit var parser: KnowledgeGraphParser
    private val mapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        graphFile = File(tempDir, "graph.json")
        parser = KnowledgeGraphParser(graphFile)
    }

    @Test
    fun `should parse simple community-based graph`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf(
                    "name" to "service layer",
                    "nodes" to listOf("LlmService", "DiagramProcessor"),
                    "edges" to listOf("LlmService --> DiagramProcessor")
                )
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edges.size)
        assertEquals(1, graph.communities.size)
        assertEquals("service layer", graph.communities[0].name)
        assertEquals("LlmService", graph.edges[0].source)
        assertEquals("DiagramProcessor", graph.edges[0].target)
    }

    @Test
    fun `should parse rich edge format with type and confidence`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf(
                    "name" to "core",
                    "nodes" to listOf("A", "B", "C"),
                    "edges" to listOf(
                        mapOf(
                            "source" to "A",
                            "target" to "B",
                            "label" to "depends on",
                            "type" to "EXTRACTED",
                            "confidence" to 1.0
                        ),
                        mapOf(
                            "source" to "A",
                            "target" to "C",
                            "label" to "might use",
                            "type" to "INFERRED",
                            "confidence" to 0.75
                        )
                    )
                )
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(2, graph.edges.size)
        val extractedEdge = graph.edges.find { it.source == "A" && it.target == "B" }!!
        assertEquals("depends on", extractedEdge.label)
        assertEquals(1.0, extractedEdge.confidence)

        val inferredEdge = graph.edges.find { it.source == "A" && it.target == "C" }!!
        assertEquals("might use", inferredEdge.label)
        assertEquals(0.75, inferredEdge.confidence)
    }

    @Test
    fun `should parse root-level edges`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf("name" to "alpha", "nodes" to listOf("A"), "edges" to emptyList<String>())
            ),
            "edges" to listOf(
                mapOf(
                    "source" to "A",
                    "target" to "B",
                    "type" to "AMBIGUOUS",
                    "confidence" to 0.5
                )
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(1, graph.edges.size)
        val edge = graph.edges[0]
        assertEquals("A", edge.source)
        assertEquals("B", edge.target)
    }

    @Test
    fun `should parse root-level nodes as objects`() {
        val json = mapper.writeValueAsString(mapOf(
            "nodes" to listOf(
                mapOf(
                    "name" to "LlmService",
                    "type" to "class",
                    "community" to "service layer",
                    "attributes" to listOf("+createModel(): ChatModel", "+getNextKey(): ApiKeyEntry")
                )
            ),
            "communities" to listOf(
                mapOf("name" to "service layer", "nodes" to listOf("LlmService"), "edges" to emptyList<String>())
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        val node = graph.nodes.find { it.name == "LlmService" }!!
        assertEquals("service layer", node.community)
        assertEquals("class", node.type)
        assertEquals(2, node.attributes.size)
        assertTrue(node.attributes[0].contains("createModel"))
    }

    @Test
    fun `should handle graph without communities`() {
        val json = mapper.writeValueAsString(mapOf(
            "nodes" to listOf("A", "B"),
            "edges" to listOf(
                mapOf("source" to "A", "target" to "B", "type" to "EXTRACTED", "confidence" to 1.0)
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edges.size)
        assertEquals(0, graph.communities.size)
    }

    @Test
    fun `should parse AMBIGUOUS edge type from string`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf(
                    "name" to "test",
                    "nodes" to listOf("X"),
                    "edges" to listOf(
                        mapOf("source" to "X", "target" to "Y", "type" to "AMBIGUOUS", "confidence" to 0.3)
                    )
                )
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        val edge = graph.edges[0]
        assertEquals(plantuml.EdgeType.AMBIGUOUS, edge.type)
        assertEquals(0.3, edge.confidence)
    }

    @Test
    fun `should throw when graph file not found`() {
        val missingFile = File(tempDir, "nonexistent.json")
        val missingParser = KnowledgeGraphParser(missingFile)

        val ex = assertFailsWith<IllegalArgumentException> {
            missingParser.parse()
        }
        assertEquals(
            PlantumlMessages.format("kgparser.file_not_found", "en", missingFile.absolutePath),
            ex.message
        )
    }

    @Test
    fun `should deduplicate edges`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf(
                    "name" to "core",
                    "nodes" to listOf("A", "B"),
                    "edges" to listOf(
                        mapOf("source" to "A", "target" to "B", "type" to "EXTRACTED", "confidence" to 1.0)
                    )
                )
            ),
            "edges" to listOf(
                mapOf("source" to "A", "target" to "B", "type" to "EXTRACTED", "confidence" to 1.0)
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(1, graph.edges.size)
    }

    @Test
    fun `should deduplicate nodes`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf("name" to "core", "nodes" to listOf("A"), "edges" to emptyList<String>())
            ),
            "nodes" to listOf(
                mapOf("name" to "A", "type" to "class", "community" to "core")
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(1, graph.nodes.size)
    }

    @Test
    fun `should parse multiple communities`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf("name" to "service", "nodes" to listOf("S1", "S2"), "edges" to emptyList<String>()),
                mapOf("name" to "config", "nodes" to listOf("C1", "C2"), "edges" to emptyList<String>()),
                mapOf("name" to "task", "nodes" to listOf("T1"), "edges" to emptyList<String>())
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(3, graph.communities.size)
        assertEquals(5, graph.nodes.size)
        assertEquals("service", graph.communities[0].name)
        assertEquals("config", graph.communities[1].name)
        assertEquals("task", graph.communities[2].name)
    }

    @Test
    fun `should parse graphify native format with links and community integers`() {
        val json = mapper.writeValueAsString(mapOf(
            "directed" to true,
            "multigraph" to false,
            "nodes" to listOf(
                mapOf("label" to "LlmService", "file_type" to "code", "id" to "llm_service", "community" to 0, "source_file" to "LlmService.kt"),
                mapOf("label" to "ApiKeyPool", "file_type" to "code", "id" to "api_key_pool", "community" to 0, "source_file" to "ApiKeyPool.kt"),
                mapOf("label" to "ConfigLoader", "file_type" to "code", "id" to "config_loader", "community" to 1, "source_file" to "ConfigLoader.kt")
            ),
            "links" to listOf(
                mapOf("source" to "llm_service", "target" to "api_key_pool", "relation" to "contains", "confidence" to "EXTRACTED", "confidence_score" to 1.0, "weight" to 1.0),
                mapOf("source" to "llm_service", "target" to "config_loader", "relation" to "method", "confidence" to "INFERRED", "confidence_score" to 0.7, "weight" to 0.7)
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(3, graph.nodes.size)
        assertEquals(2, graph.edges.size)
        assertEquals(2, graph.communities.size)

        val llmNode = graph.nodes.find { it.name == "LlmService" }!!
        assertEquals("community_0", llmNode.community)

        val configNode = graph.nodes.find { it.name == "ConfigLoader" }!!
        assertEquals("community_1", configNode.community)

        val containsEdge = graph.edges.find { it.label == "contains" }!!
        assertEquals("LlmService", containsEdge.source)
        assertEquals("ApiKeyPool", containsEdge.target)
        assertEquals(EdgeType.EXTRACTED, containsEdge.type)

        val methodEdge = graph.edges.find { it.label == "method" }!!
        assertEquals(EdgeType.INFERRED, methodEdge.type)
        assertEquals(0.7, methodEdge.confidence)

        assertEquals("community_0", graph.communities[0].name)
        assertEquals("community_1", graph.communities[1].name)
    }

    @Test
    fun `should throw on malformed JSON`() {
        graphFile.writeText("{invalid json!!!")

        assertFailsWith<com.fasterxml.jackson.core.JsonParseException> {
            parser.parse()
        }
    }

    @Test
    fun `should default unknown edge type to EXTRACTED`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf(
                    "name" to "test",
                    "nodes" to listOf("X"),
                    "edges" to listOf(
                        mapOf("source" to "X", "target" to "Y", "type" to "UNKNOWN_TYPE", "confidence" to 0.9)
                    )
                )
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(EdgeType.EXTRACTED, graph.edges[0].type)
    }

    @Test
    fun `should parse graphify format with edges key instead of links`() {
        val json = mapper.writeValueAsString(mapOf(
            "directed" to true,
            "nodes" to listOf(
                mapOf("label" to "A", "file_type" to "code", "id" to "a", "community" to 0),
                mapOf("label" to "B", "file_type" to "code", "id" to "b", "community" to 0)
            ),
            "edges" to listOf(
                mapOf("source" to "a", "target" to "b", "relation" to "calls", "confidence" to "EXTRACTED", "confidence_score" to 1.0)
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edges.size)
        assertEquals("calls", graph.edges[0].label)
    }

    @Test
    fun `should parse graphify nodes without community field`() {
        val json = mapper.writeValueAsString(mapOf(
            "nodes" to listOf(
                mapOf("label" to "Standalone", "file_type" to "code", "id" to "standalone"),
                mapOf("label" to "Grouped", "file_type" to "code", "id" to "grouped", "community" to 5)
            ),
            "links" to listOf(
                mapOf("source" to "standalone", "target" to "grouped", "relation" to "uses", "confidence" to "EXTRACTED", "confidence_score" to 1.0)
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(2, graph.nodes.size)
        val standalone = graph.nodes.find { it.name == "Standalone" }!!
        assertEquals("", standalone.community)
        val grouped = graph.nodes.find { it.name == "Grouped" }!!
        assertEquals("community_5", grouped.community)
        assertEquals(1, graph.communities.size)
    }

    @Test
    fun `should parse graphify nodes without file_type`() {
        val json = mapper.writeValueAsString(mapOf(
            "nodes" to listOf(
                mapOf("label" to "NoFileType", "id" to "no_ft", "community" to 0)
            ),
            "links" to emptyList<Any>()
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        val node = graph.nodes[0]
        assertEquals("NoFileType", node.name)
        assertEquals("class", node.type)
    }

    @Test
    fun `should parse graphify links with weight as confidence fallback`() {
        val json = mapper.writeValueAsString(mapOf(
            "nodes" to listOf(
                mapOf("label" to "A", "file_type" to "code", "id" to "a", "community" to 0),
                mapOf("label" to "B", "file_type" to "code", "id" to "b", "community" to 0)
            ),
            "links" to listOf(
                mapOf("source" to "a", "target" to "b", "relation" to "calls", "confidence" to "EXTRACTED", "weight" to 0.85)
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(0.85, graph.edges[0].confidence)
    }

    @Test
    fun `should parse graphify link with label field as fallback`() {
        val json = mapper.writeValueAsString(mapOf(
            "nodes" to listOf(
                mapOf("label" to "A", "file_type" to "code", "id" to "a", "community" to 0),
                mapOf("label" to "B", "file_type" to "code", "id" to "b", "community" to 0)
            ),
            "links" to listOf(
                mapOf("source" to "a", "target" to "b", "label" to "depends on", "type" to "EXTRACTED", "confidence" to 1.0)
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals("depends on", graph.edges[0].label)
    }

    @Test
    fun `should parse edge string with dashed arrow INFERRED`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf("name" to "core", "nodes" to listOf("X", "Y"), "edges" to listOf("X ..> Y : might use"))
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(EdgeType.INFERRED, graph.edges[0].type)
        assertEquals("X", graph.edges[0].source)
        assertEquals("Y", graph.edges[0].target)
        assertEquals("might use", graph.edges[0].label)
    }

    @Test
    fun `should parse edge string with crossed arrow AMBIGUOUS`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf("name" to "core", "nodes" to listOf("X", "Y"), "edges" to listOf("X --x Y"))
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(EdgeType.AMBIGUOUS, graph.edges[0].type)
    }

    @Test
    fun `should parse edge string with simple arrow`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf("name" to "core", "nodes" to listOf("A", "B"), "edges" to listOf("A -> B : depends"))
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(EdgeType.EXTRACTED, graph.edges[0].type)
        assertEquals("depends", graph.edges[0].label)
    }

    @Test
    fun `should return empty graph for JSON with no nodes edges or communities`() {
        val json = mapper.writeValueAsString(mapOf("directed" to true))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(0, graph.nodes.size)
        assertEquals(0, graph.edges.size)
        assertEquals(0, graph.communities.size)
    }

    @Test
    fun `should parse empty communities array as legacy format`() {
        val json = mapper.writeValueAsString(mapOf(
            "communities" to emptyList<Any>(),
            "nodes" to listOf("A"),
            "edges" to emptyList<Any>()
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(1, graph.nodes.size)
        assertEquals(0, graph.communities.size)
    }

    @Test
    fun `should parse graphify format with node name fallback`() {
        val json = mapper.writeValueAsString(mapOf(
            "nodes" to listOf(
                mapOf("name" to "FallbackNode", "community" to 0, "id" to "fb")
            ),
            "links" to emptyList<Any>()
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals("FallbackNode", graph.nodes[0].name)
    }

    @Test
    fun `should deduplicate edges in graphify format`() {
        val json = mapper.writeValueAsString(mapOf(
            "nodes" to listOf(
                mapOf("label" to "A", "file_type" to "code", "id" to "a", "community" to 0),
                mapOf("label" to "B", "file_type" to "code", "id" to "b", "community" to 0)
            ),
            "links" to listOf(
                mapOf("source" to "a", "target" to "b", "relation" to "calls", "confidence" to "EXTRACTED", "confidence_score" to 1.0),
                mapOf("source" to "a", "target" to "b", "relation" to "calls", "confidence" to "EXTRACTED", "confidence_score" to 1.0)
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(1, graph.edges.size)
    }

    @Test
    fun `should skip null edge nodes gracefully`() {
        val json = mapper.writeValueAsString(mapOf(
            "nodes" to listOf("A", "B"),
            "edges" to listOf(
                mapOf("source" to "A", "target" to "B", "type" to "EXTRACTED", "confidence" to 1.0),
                42,
                "garbage"
            )
        ))
        graphFile.writeText(json)

        val graph = parser.parse()

        assertEquals(1, graph.edges.size)
    }
}