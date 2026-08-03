package plantuml.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import plantuml.EdgeType
import plantuml.KnowledgeGraph
import plantuml.validation.SyntaxValidationResult
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphifyIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var graphFile: File
    private val mapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        val graphifyOut = File(tempDir, "graphify-out")
        graphifyOut.mkdirs()
        graphFile = File(graphifyOut, "graph.json")
    }

    private fun writeGraphifyJson(
        nodes: List<Map<String, Any>>,
        links: List<Map<String, Any>>
    ) {
        graphFile.writeText(mapper.writeValueAsString(mapOf(
            "directed" to true,
            "multigraph" to false,
            "nodes" to nodes,
            "links" to links
        )))
    }

    private fun parseRenderValidate(
        communityFilter: String? = null,
        edgeTypes: Set<EdgeType> = EdgeType.entries.toSet(),
        minConfidence: Double = 0.0,
        maxNodes: Int = Int.MAX_VALUE
    ): String {
        val parser = KnowledgeGraphParser(graphFile)
        val graph = parser.parse()

        val renderer = KnowledgeGraphRenderer()
        val plantumlCode = renderer.render(
            graph,
            communityFilter = communityFilter,
            edgeTypes = edgeTypes,
            minConfidence = minConfidence,
            maxNodes = maxNodes
        )

        val plantumlService = PlantumlService()
        val validationResult = plantumlService.validateSyntax(plantumlCode)

        if (validationResult is SyntaxValidationResult.Valid) {
            val pngFile = File(tempDir, "diagrams/kg-test.png")
            pngFile.parentFile.mkdirs()
            plantumlService.generateImage(plantumlCode, pngFile)
            assertTrue(pngFile.exists(), "PNG should be generated for valid PlantUML")
        }

        return plantumlCode
    }

    @Test
    fun `full pipeline graphify JSON to parser to renderer to PNG`() {
        writeGraphifyJson(
            nodes = listOf(
                mapOf("label" to "PlantumlPlugin", "file_type" to "code", "id" to "plantuml_plugin", "community" to 0, "source_file" to "PlantumlPlugin.kt"),
                mapOf("label" to "PlantumlManager", "file_type" to "code", "id" to "plantuml_manager", "community" to 0, "source_file" to "PlantumlManager.kt"),
                mapOf("label" to "LlmService", "file_type" to "code", "id" to "llm_service", "community" to 1, "source_file" to "LlmService.kt"),
                mapOf("label" to "ApiKeyPool", "file_type" to "code", "id" to "api_key_pool", "community" to 1, "source_file" to "ApiKeyPool.kt"),
                mapOf("label" to "ConfigLoader", "file_type" to "code", "id" to "config_loader", "community" to 2, "source_file" to "ConfigLoader.kt"),
                mapOf("label" to "ConfigMerger", "file_type" to "code", "id" to "config_merger", "community" to 2, "source_file" to "ConfigMerger.kt")
            ),
            links = listOf(
                mapOf("source" to "plantuml_plugin", "target" to "plantuml_manager", "relation" to "contains", "confidence" to "EXTRACTED", "confidence_score" to 1.0, "weight" to 1.0),
                mapOf("source" to "plantuml_manager", "target" to "llm_service", "relation" to "method", "confidence" to "EXTRACTED", "confidence_score" to 1.0, "weight" to 1.0),
                mapOf("source" to "llm_service", "target" to "api_key_pool", "relation" to "method", "confidence" to "EXTRACTED", "confidence_score" to 1.0, "weight" to 1.0),
                mapOf("source" to "plantuml_manager", "target" to "config_loader", "relation" to "method", "confidence" to "INFERRED", "confidence_score" to 0.7, "weight" to 0.7),
                mapOf("source" to "config_loader", "target" to "config_merger", "relation" to "contains", "confidence" to "EXTRACTED", "confidence_score" to 1.0, "weight" to 1.0),
                mapOf("source" to "llm_service", "target" to "config_merger", "relation" to "method", "confidence" to "AMBIGUOUS", "confidence_score" to 0.3, "weight" to 0.3)
            )
        )

        val result = parseRenderValidate()

        assertTrue(result.contains("@startuml"))
        assertTrue(result.contains("@enduml"))
        assertTrue(result.contains("community_0"))
        assertTrue(result.contains("community_1"))
        assertTrue(result.contains("community_2"))
        assertTrue(result.contains("PlantumlPlugin"))
        assertTrue(result.contains("LlmService"))
        assertTrue(result.contains("ConfigLoader"))
        assertTrue(result.contains("-->"))
        assertTrue(result.contains("..>"))
        assertTrue(result.contains("--x"))
        assertTrue(result.contains("Cross-community edges"))
    }

    @Test
    fun `full pipeline with community filter`() {
        writeGraphifyJson(
            nodes = listOf(
                mapOf("label" to "A", "file_type" to "code", "id" to "a", "community" to 0),
                mapOf("label" to "B", "file_type" to "code", "id" to "b", "community" to 0),
                mapOf("label" to "C", "file_type" to "code", "id" to "c", "community" to 1)
            ),
            links = listOf(
                mapOf("source" to "a", "target" to "b", "relation" to "calls", "confidence" to "EXTRACTED", "confidence_score" to 1.0, "weight" to 1.0),
                mapOf("source" to "a", "target" to "c", "relation" to "uses", "confidence" to "INFERRED", "confidence_score" to 0.5, "weight" to 0.5)
            )
        )

        val result = parseRenderValidate(communityFilter = "community_0")

        assertTrue(result.contains("community_0"))
        assertTrue(!result.contains("community_1"))
        assertTrue(result.contains("A"))
        assertTrue(result.contains("B"))
    }

    @Test
    fun `full pipeline with maxNodes limit`() {
        val nodes = (0..9).map { i ->
            mapOf("label" to "Node$i", "file_type" to "code", "id" to "node_$i", "community" to 0)
        }
        val links = emptyList<Map<String, Any>>()
        writeGraphifyJson(nodes, links)

        val result = parseRenderValidate(maxNodes = 3)

        assertTrue(result.contains("Node0"))
        assertTrue(result.contains("Node2"))
        assertTrue(!result.contains("Node3"))
    }

    @Test
    fun `full pipeline with edge type filter`() {
        writeGraphifyJson(
            nodes = listOf(
                mapOf("label" to "X", "file_type" to "code", "id" to "x", "community" to 0),
                mapOf("label" to "Y", "file_type" to "code", "id" to "y", "community" to 0)
            ),
            links = listOf(
                mapOf("source" to "x", "target" to "y", "relation" to "calls", "confidence" to "EXTRACTED", "confidence_score" to 1.0, "weight" to 1.0)
            )
        )

        val result = parseRenderValidate(edgeTypes = setOf(EdgeType.INFERRED))

        assertTrue(result.contains("@startuml"))
        assertTrue(!result.contains("-->"))
    }

    @Test
    fun `full pipeline with minConfidence filter`() {
        writeGraphifyJson(
            nodes = listOf(
                mapOf("label" to "A", "file_type" to "code", "id" to "a", "community" to 0),
                mapOf("label" to "B", "file_type" to "code", "id" to "b", "community" to 0)
            ),
            links = listOf(
                mapOf("source" to "a", "target" to "b", "relation" to "maybe", "confidence" to "INFERRED", "confidence_score" to 0.4, "weight" to 0.4)
            )
        )

        val result = parseRenderValidate(minConfidence = 0.7)

        assertTrue(result.contains("@startuml"))
        assertTrue(!result.contains("maybe"))
    }

    @Test
    fun `parser produces consistent graph for renderer`() {
        writeGraphifyJson(
            nodes = listOf(
                mapOf("label" to "Service", "file_type" to "code", "id" to "service", "community" to 0),
                mapOf("label" to "Repo", "file_type" to "code", "id" to "repo", "community" to 0)
            ),
            links = listOf(
                mapOf("source" to "service", "target" to "repo", "relation" to "calls", "confidence" to "EXTRACTED", "confidence_score" to 1.0, "weight" to 1.0)
            )
        )

        val parser = KnowledgeGraphParser(graphFile)
        val graph = parser.parse()

        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edges.size)
        assertEquals(1, graph.communities.size)
        assertEquals("community_0", graph.communities[0].name)
        assertEquals("Service", graph.nodes.find { it.name == "Service" }?.name)
        assertEquals("Repo", graph.nodes.find { it.name == "Repo" }?.name)
        assertEquals("calls", graph.edges[0].label)
        assertEquals(EdgeType.EXTRACTED, graph.edges[0].type)

        val renderer = KnowledgeGraphRenderer()
        val plantumlCode = renderer.render(graph)

        assertTrue(plantumlCode.contains("package \"community_0\""))
        assertTrue(plantumlCode.contains("Service"))
        assertTrue(plantumlCode.contains("Repo"))
        assertTrue(plantumlCode.contains("Service --> Repo"))
    }

    @Test
    fun `pipeline handles single node without edges`() {
        writeGraphifyJson(
            nodes = listOf(
                mapOf("label" to "Lonely", "file_type" to "code", "id" to "lonely", "community" to 0)
            ),
            links = emptyList<Map<String, Any>>()
        )

        val result = parseRenderValidate()

        assertTrue(result.contains("Lonely"))
        assertTrue(result.contains("@enduml"))
    }

    @Test
    fun `pipeline handles nodes without community`() {
        graphFile.writeText(mapper.writeValueAsString(mapOf(
            "directed" to true,
            "nodes" to listOf(
                mapOf("label" to "NoCommunity", "file_type" to "code", "id" to "no_comm")
            ),
            "links" to emptyList<Any>()
        )))

        val parser = KnowledgeGraphParser(graphFile)
        val graph = parser.parse()

        assertEquals(1, graph.nodes.size)
        assertEquals("", graph.nodes[0].community)

        val renderer = KnowledgeGraphRenderer()
        val plantumlCode = renderer.render(graph)

        assertTrue(plantumlCode.contains("NoCommunity"))
        assertTrue(plantumlCode.contains("@enduml"))
    }

    @Test
    fun `pipeline handles large graphify output`() {
        val nodes = (0..49).map { i ->
            mapOf("label" to "Class$i", "file_type" to "code", "id" to "class_$i", "community" to (i / 5))
        }
        val links = nodes.zipWithNext().mapIndexed { idx, pair ->
            mapOf(
                "source" to pair.first["id"]!!,
                "target" to pair.second["id"]!!,
                "relation" to "calls",
                "confidence" to "EXTRACTED",
                "confidence_score" to 1.0,
                "weight" to 1.0
            )
        }
        writeGraphifyJson(nodes, links)

        val result = parseRenderValidate()

        assertTrue(result.contains("@startuml"))
        assertTrue(result.contains("community_0"))
        assertTrue(result.contains("community_9"))
    }
}