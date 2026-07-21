package plantuml

/**
 * Classification of knowledge graph edge provenance.
 *
 * - [EXTRACTED]: Directly observed from source code (e.g., import statements)
 * - [INFERRED]: Deduced by the LLM from naming conventions or context
 * - [AMBIGUOUS]: Uncertain relationship requiring human review
 */
enum class EdgeType {
    EXTRACTED,
    INFERRED,
    AMBIGUOUS
}

/**
 * A directed edge in the knowledge graph connecting two nodes.
 *
 * @property source Source node name
 * @property target Target node name
 * @property label Human-readable relationship label
 * @property type Provenance classification of this edge
 * @property confidence Confidence score (0.0–1.0) for inferred edges
 */
data class KnowledgeGraphEdge(
    val source: String,
    val target: String,
    val label: String = "",
    val type: EdgeType = EdgeType.EXTRACTED,
    val confidence: Double = 1.0
)

/**
 * A node in the knowledge graph representing a code entity.
 *
 * @property name Display name of the entity
 * @property type Entity kind (e.g., "class", "code", "interface")
 * @property community Community identifier assigned by graphify
 * @property attributes Additional metadata attributes
 */
data class KnowledgeGraphNode(
    val name: String,
    val type: String = "class",
    val community: String = "",
    val attributes: List<String> = emptyList()
)

/**
 * A community (cluster) of related nodes in the knowledge graph.
 *
 * @property name Community display name
 * @property color Optional hex color for rendering
 * @property nodes Node names belonging to this community
 * @property edges Edges whose source or target is within this community
 */
data class KnowledgeGraphCommunity(
    val name: String,
    val color: String = "",
    val nodes: List<String> = emptyList(),
    val edges: List<KnowledgeGraphEdge> = emptyList()
)

/**
 * Top-level knowledge graph model aggregating nodes, edges, and communities.
 *
 * @property nodes All graph nodes
 * @property edges All graph edges
 * @property communities Community clusters
 */
data class KnowledgeGraph(
    val nodes: List<KnowledgeGraphNode> = emptyList(),
    val edges: List<KnowledgeGraphEdge> = emptyList(),
    val communities: List<KnowledgeGraphCommunity> = emptyList()
)