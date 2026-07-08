package plantuml.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import plantuml.EdgeType
import plantuml.KnowledgeGraph
import plantuml.KnowledgeGraphCommunity
import plantuml.KnowledgeGraphEdge
import plantuml.KnowledgeGraphNode
import plantuml.boundary.IdiomaticGlossary
import plantuml.boundary.TextClassifier
import plantuml.boundary.TranslationResolver
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class KnowledgeGraphRendererTest {

    private lateinit var renderer: KnowledgeGraphRenderer

    @BeforeEach
    fun setUp() {
        renderer = KnowledgeGraphRenderer()
    }

    @Test
    fun `should render empty graph`() {
        val graph = KnowledgeGraph()
        val result = renderer.render(graph)
        assertTrue(result.contains("@startuml"))
        assertTrue(result.contains("@enduml"))
        assertTrue(result.contains("Empty Knowledge Graph"))
    }

    @Test
    fun `should render graph with single community`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "LlmService", community = "service layer"),
                KnowledgeGraphNode(name = "DiagramProcessor", community = "service layer")
            ),
            edges = listOf(
                KnowledgeGraphEdge(
                    source = "LlmService",
                    target = "DiagramProcessor",
                    label = "creates",
                    type = EdgeType.EXTRACTED
                )
            ),
            communities = listOf(
                KnowledgeGraphCommunity(
                    name = "service layer",
                    nodes = listOf("LlmService", "DiagramProcessor"),
                    edges = listOf(
                        KnowledgeGraphEdge(
                            source = "LlmService",
                            target = "DiagramProcessor",
                            label = "creates",
                            type = EdgeType.EXTRACTED
                        )
                    )
                )
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("@startuml"))
        assertTrue(result.contains("service layer"))
        assertTrue(result.contains("LlmService"))
        assertTrue(result.contains("DiagramProcessor"))
        assertTrue(result.contains("-->"))
        assertTrue(result.contains("creates"))
        assertTrue(result.contains("@enduml"))
    }

    @Test
    fun `should render EXTRACTED edges with solid arrow`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "A", community = "test"),
                KnowledgeGraphNode(name = "B", community = "test")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "A", target = "B", type = EdgeType.EXTRACTED)
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("A", "B"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("A --> B"))
    }

    @Test
    fun `should render INFERRED edges with dashed arrow`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "A", community = "test"),
                KnowledgeGraphNode(name = "B", community = "test")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "A", target = "B", type = EdgeType.INFERRED, confidence = 0.8)
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("A", "B"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("A ..> B"))
        assertTrue(result.contains("80%"))
    }

    @Test
    fun `should render AMBIGUOUS edges with crossed arrow`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "A", community = "test"),
                KnowledgeGraphNode(name = "B", community = "test")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "A", target = "B", type = EdgeType.AMBIGUOUS)
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("A", "B"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("A --x B"))
    }

    @Test
    fun `should filter by community`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "AlphaNode", community = "service layer"),
                KnowledgeGraphNode(name = "BetaNode", community = "config layer")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "service layer", nodes = listOf("AlphaNode")),
                KnowledgeGraphCommunity(name = "config layer", nodes = listOf("BetaNode"))
            )
        )
        val result = renderer.render(graph, communityFilter = "service")
        assertTrue(result.contains("service layer"))
        assertTrue(result.contains("AlphaNode"))
        assertFalse(result.contains("config layer"))
        assertFalse(result.contains("BetaNode"))
    }

    @Test
    fun `should filter by edge type`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "A", community = "test"),
                KnowledgeGraphNode(name = "B", community = "test"),
                KnowledgeGraphNode(name = "C", community = "test")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "A", target = "B", type = EdgeType.EXTRACTED),
                KnowledgeGraphEdge(source = "A", target = "C", type = EdgeType.INFERRED)
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("A", "B", "C"))
            )
        )
        val result = renderer.render(graph, edgeTypes = setOf(EdgeType.EXTRACTED))
        assertTrue(result.contains("A --> B"))
        assertFalse(result.contains("A ..> C"))
    }

    @Test
    fun `should filter by minimum confidence`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "A", community = "test"),
                KnowledgeGraphNode(name = "B", community = "test"),
                KnowledgeGraphNode(name = "C", community = "test")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "A", target = "B", type = EdgeType.INFERRED, confidence = 0.9),
                KnowledgeGraphEdge(source = "A", target = "C", type = EdgeType.INFERRED, confidence = 0.3)
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("A", "B", "C"))
            )
        )
        val result = renderer.render(graph, minConfidence = 0.7)
        assertTrue(result.contains("A ..> B"))
        assertFalse(result.contains("0.3"))
    }

    @Test
    fun `should limit number of nodes with maxNodes`() {
        val graph = KnowledgeGraph(
            nodes = (1..10).map { KnowledgeGraphNode(name = "Node$it", community = "test") },
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = (1..10).map { "Node$it" })
            )
        )
        val result = renderer.render(graph, maxNodes = 3)
        assertTrue(result.contains("Node1"))
        assertTrue(result.contains("Node2"))
        assertTrue(result.contains("Node3"))
        assertFalse(result.contains("Node4"))
    }

    @Test
    fun `should render nodes with attributes`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(
                    name = "ApiKeyPool",
                    community = "config layer",
                    type = "class",
                    attributes = listOf("+getNextKey(): ApiKeyEntry", "+resetCounters(): Unit")
                )
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "config layer", nodes = listOf("ApiKeyPool"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("ApiKeyPool"))
        assertTrue(result.contains("getNextKey"))
        assertTrue(result.contains("resetCounters"))
    }

    @Test
    fun `should render legend with edge types`() {
        val graph = KnowledgeGraph(
            nodes = listOf(KnowledgeGraphNode(name = "A", community = "test")),
            communities = listOf(KnowledgeGraphCommunity(name = "test", nodes = listOf("A")))
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("legend"))
        assertTrue(result.contains("Extracted"))
        assertTrue(result.contains("Inferred"))
        assertTrue(result.contains("Ambiguous"))
    }

    @Test
    fun `should sanitize node IDs`() {
        val result = renderer.sanitizeId("My Class.Name (v2)")
        assertEquals("My_Class_Name_v2", result)
    }

    @Test
    fun `should sanitize node IDs starting with digit`() {
        val result = renderer.sanitizeId("3DRenderer")
        assertTrue(result.startsWith("_"))
    }

    @Test
    fun `should render cross-community edges separately`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "A", community = "alpha"),
                KnowledgeGraphNode(name = "B", community = "alpha"),
                KnowledgeGraphNode(name = "C", community = "beta")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "A", target = "B", type = EdgeType.EXTRACTED, label = "internal"),
                KnowledgeGraphEdge(source = "A", target = "C", type = EdgeType.INFERRED, label = "cross")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "alpha", nodes = listOf("A", "B")),
                KnowledgeGraphCommunity(name = "beta", nodes = listOf("C"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("Cross-community edges"))
        assertTrue(result.contains("Intra-community edges"))
    }

    private fun assertFalse(condition: Boolean) {
        org.junit.jupiter.api.Assertions.assertFalse(condition)
    }

    @Test
    fun `should apply combined filters community edgeType and minConfidence`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "S1", community = "service"),
                KnowledgeGraphNode(name = "S2", community = "service"),
                KnowledgeGraphNode(name = "C1", community = "config"),
                KnowledgeGraphNode(name = "C2", community = "config")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "S1", target = "S2", type = EdgeType.EXTRACTED, confidence = 1.0, label = "calls"),
                KnowledgeGraphEdge(source = "S1", target = "C1", type = EdgeType.INFERRED, confidence = 0.5, label = "maybe"),
                KnowledgeGraphEdge(source = "C1", target = "C2", type = EdgeType.INFERRED, confidence = 0.9, label = "loads")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "service", nodes = listOf("S1", "S2")),
                KnowledgeGraphCommunity(name = "config", nodes = listOf("C1", "C2"))
            )
        )
        val result = renderer.render(
            graph,
            communityFilter = "config",
            edgeTypes = setOf(EdgeType.INFERRED),
            minConfidence = 0.7
        )
        assertTrue(result.contains("config"))
        assertFalse(result.contains("service"))
        assertTrue(result.contains("C1"))
        assertTrue(result.contains("C2"))
        assertTrue(result.contains("loads"))
        assertFalse(result.contains("maybe"))
        assertFalse(result.contains("calls"))
    }

    @Test
    fun `should render unassigned nodes section`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "Assigned", community = "core"),
                KnowledgeGraphNode(name = "Loner", community = "")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "core", nodes = listOf("Assigned"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("Unassigned nodes"))
        assertTrue(result.contains("Loner"))
    }

    @Test
    fun `should render custom community color`() {
        val graph = KnowledgeGraph(
            nodes = listOf(KnowledgeGraphNode(name = "A", community = "colored")),
            communities = listOf(
                KnowledgeGraphCommunity(name = "colored", color = "#FF0000", nodes = listOf("A"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("#FF0000"))
    }

    @Test
    fun `should limit attributes to 5 per node`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(
                    name = "BigClass",
                    community = "test",
                    attributes = (1..8).map { "+attr$it(): String" }
                )
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("BigClass"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("attr1"))
        assertTrue(result.contains("attr5"))
        assertFalse(result.contains("attr6"))
        assertFalse(result.contains("attr7"))
        assertFalse(result.contains("attr8"))
    }

    @Test
    fun `should render legend with filter name`() {
        val graph = KnowledgeGraph(
            nodes = listOf(KnowledgeGraphNode(name = "A", community = "service")),
            communities = listOf(KnowledgeGraphCommunity(name = "service", nodes = listOf("A")))
        )
        val result = renderer.render(graph, communityFilter = "service")
        assertTrue(result.contains("Filter"))
        assertTrue(result.contains("service"))
    }

    @Test
    fun `should render edge label with colon prefix`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "A", community = "test"),
                KnowledgeGraphNode(name = "B", community = "test")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "A", target = "B", label = "depends on", type = EdgeType.EXTRACTED)
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("A", "B"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("A --> B : depends on"))
    }

    @Test
    fun `should omit confidence label for INFERRED edge with confidence 1_0`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "A", community = "test"),
                KnowledgeGraphNode(name = "B", community = "test")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "A", target = "B", type = EdgeType.INFERRED, confidence = 1.0)
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("A", "B"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("A ..> B"))
        assertFalse(result.contains("100%"))
    }

    @Test
    fun `should render graphify community names from parsed graph`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "NodeA", community = "community_0"),
                KnowledgeGraphNode(name = "NodeB", community = "community_1")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "NodeA", target = "NodeB", type = EdgeType.EXTRACTED, label = "calls")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "community_0", nodes = listOf("NodeA")),
                KnowledgeGraphCommunity(name = "community_1", nodes = listOf("NodeB"))
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("community_0"))
        assertTrue(result.contains("community_1"))
        assertTrue(result.contains("Cross-community edges"))
        assertTrue(result.contains("calls"))
    }

    @Test
    fun `should render community with no nodes via nodesByCommunity`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "A", community = "alpha")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "A", target = "A", type = EdgeType.EXTRACTED)
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "alpha", nodes = emptyList()),
                KnowledgeGraphCommunity(name = "empty_community", nodes = emptyList())
            )
        )
        val result = renderer.render(graph)
        assertTrue(result.contains("alpha"))
        assertTrue(result.contains("@enduml"))
    }

    @Test
    fun `should sanitize IDs with slashes and backslashes`() {
        assertEquals("src_main_Service", renderer.sanitizeId("src/main/Service"))
        assertEquals("src_main_Service", renderer.sanitizeId("src\\main\\Service"))
    }

    @Test
    fun `should sanitize IDs with quotes`() {
        assertEquals("MyNode", renderer.sanitizeId("My'\"Node"))
    }

    @Test
    fun `cleanLabel should strip leading dots`() {
        val graph = KnowledgeGraph(
            nodes = listOf(KnowledgeGraphNode(name = ".hiddenMethod", community = "test")),
            communities = listOf(KnowledgeGraphCommunity(name = "test", nodes = listOf(".hiddenMethod")))
        )
        val result = renderer.render(graph)
        assertFalse(result.contains("\".hiddenMethod\""))
        assertTrue(result.contains("\"hiddenMethod\""))
    }

    @Test
    fun `cleanLabel should strip multiple leading dots`() {
        val graph = KnowledgeGraph(
            nodes = listOf(KnowledgeGraphNode(name = "..deepHidden", community = "test")),
            communities = listOf(KnowledgeGraphCommunity(name = "test", nodes = listOf("..deepHidden")))
        )
        val result = renderer.render(graph)
        assertFalse(result.contains("\"..deepHidden\""))
        assertTrue(result.contains("\"deepHidden\""))
    }

    @Test
    fun `cleanLabel should strip backticks`() {
        val graph = KnowledgeGraph(
            nodes = listOf(KnowledgeGraphNode(name = "`forEach`", community = "test")),
            communities = listOf(KnowledgeGraphCommunity(name = "test", nodes = listOf("`forEach`")))
        )
        val result = renderer.render(graph)
        assertFalse(result.contains("\"`forEach`\""))
        assertTrue(result.contains("\"forEach\""))
    }

    @Test
    fun `cleanLabel should strip backticks with Kotlin method signature`() {
        val graph = KnowledgeGraph(
            nodes = listOf(KnowledgeGraphNode(name = "`equals`(other: Any)", community = "test")),
            communities = listOf(KnowledgeGraphCommunity(name = "test", nodes = listOf("`equals`(other: Any)")))
        )
        val result = renderer.render(graph)
        assertFalse(result.contains('`'))
        assertTrue(result.contains("equals(other: Any)"))
    }

    @Test
    fun `cleanLabel should strip leading dots and backticks combined`() {
        val graph = KnowledgeGraph(
            nodes = listOf(KnowledgeGraphNode(name = ".`apply`", community = "test")),
            communities = listOf(KnowledgeGraphCommunity(name = "test", nodes = listOf(".`apply`")))
        )
        val result = renderer.render(graph)
        assertFalse(result.contains("\".`apply`\""))
        assertTrue(result.contains("\"apply\""))
    }

    @Test
    fun `isMethodNode should match trailing parentheses`() {
        val node = KnowledgeGraphNode(name = "toString()", community = "test")
        assertTrue(renderer.isMethodNode(node))
    }

    @Test
    fun `isMethodNode should match leading dot`() {
        val node = KnowledgeGraphNode(name = ".apply", community = "test")
        assertTrue(renderer.isMethodNode(node))
    }

    @Test
    fun `isMethodNode should match backtick pattern`() {
        val node = KnowledgeGraphNode(name = "`forEach`", community = "test")
        assertTrue(renderer.isMethodNode(node))
    }

    @Test
    fun `isMethodNode should not match regular class name`() {
        val node = KnowledgeGraphNode(name = "LlmService", community = "test")
        assertFalse(renderer.isMethodNode(node))
    }

    @Test
    fun `isMethodNode should not match plain node name`() {
        val node = KnowledgeGraphNode(name = "ConfigLoader", community = "test")
        assertFalse(renderer.isMethodNode(node))
    }

    @Test
    fun `nodeTypes filter should include only matching types`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "MyClass", type = "class", community = "test"),
                KnowledgeGraphNode(name = "myFunc.ts", type = "code", community = "test")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("MyClass", "myFunc.ts"))
            )
        )
        val result = renderer.render(graph, nodeTypes = setOf("class"))
        assertTrue(result.contains("MyClass"))
        assertFalse(result.contains("myFunc.ts"))
    }

    @Test
    fun `nodeTypes filter should exclude nodes not in set`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "MyClass", type = "class", community = "test"),
                KnowledgeGraphNode(name = "myFunc.ts", type = "code", community = "test")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("MyClass", "myFunc.ts"))
            )
        )
        val result = renderer.render(graph, nodeTypes = setOf("code"))
        assertFalse(result.contains("MyClass"))
        assertTrue(result.contains("myFunc.ts"))
    }

    @Test
    fun `nodeTypes null should include all types`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "MyClass", type = "class", community = "test"),
                KnowledgeGraphNode(name = "myFunc.ts", type = "code", community = "test")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "test", nodes = listOf("MyClass", "myFunc.ts"))
            )
        )
        val result = renderer.render(graph, nodeTypes = null)
        assertTrue(result.contains("MyClass"))
        assertTrue(result.contains("myFunc.ts"))
    }

    @Test
    fun `rendered output should not contain toString artifact`() {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "LlmService", community = "core"),
                KnowledgeGraphNode(name = "ConfigLoader", community = "core")
            ),
            edges = listOf(
                KnowledgeGraphEdge(source = "LlmService", target = "ConfigLoader", type = EdgeType.EXTRACTED)
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "core", nodes = listOf("LlmService", "ConfigLoader"))
            )
        )
        val result = renderer.render(graph)
        assertFalse(result.contains("KnowledgeGraphNode"))
        assertFalse(result.contains("KnowledgeGraphEdge"))
        assertFalse(result.contains("KnowledgeGraphCommunity"))
    }

    @Test
    fun `should translate Classes folder label when resolver provided`() {
        val resolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = IdiomaticGlossary(),
            messageResolver = { key, _ -> if (key == "label.classes") "Classes" else null }
        )
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "LlmService", type = "class", community = "core")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "core", nodes = listOf("LlmService"))
            )
        )
        val result = renderer.render(graph, resolver = resolver, language = "fr")
        assertTrue(result.contains("folder \"Classes\" {"))
    }

    @Test
    fun `should preserve node identifiers when resolver provided`() {
        val resolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = IdiomaticGlossary(),
            messageResolver = { _, _ -> null }
        )
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = "LlmService", type = "class", community = "core")
            ),
            communities = listOf(
                KnowledgeGraphCommunity(name = "core", nodes = listOf("LlmService"))
            )
        )
        val result = renderer.render(graph, resolver = resolver, language = "fr")
        assertTrue(result.contains("LlmService"))
    }
}