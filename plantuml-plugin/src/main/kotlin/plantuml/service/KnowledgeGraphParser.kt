package plantuml.service

import com.fasterxml.jackson.databind.ObjectMapper
import plantuml.EdgeType
import plantuml.KnowledgeGraph
import plantuml.KnowledgeGraphCommunity
import plantuml.KnowledgeGraphEdge
import plantuml.KnowledgeGraphNode
import plantuml.PlantumlMessages
import java.io.File

class KnowledgeGraphParser(
    private val graphFile: File
) {
    private val mapper = ObjectMapper()

    fun parse(): KnowledgeGraph {
        if (!graphFile.exists()) {
            throw IllegalArgumentException(
                PlantumlMessages.format("kgparser.file_not_found", "en", graphFile.absolutePath)
            )
        }

        val root = mapper.readTree(graphFile)

        val nodes = mutableListOf<KnowledgeGraphNode>()
        val edges = mutableListOf<KnowledgeGraphEdge>()
        val communities = mutableListOf<KnowledgeGraphCommunity>()

        val communitiesNode = root.get("communities")
        val hasGraphifyLinks = root.has("links")
        val firstNode = root.get("nodes")?.firstOrNull()
        val hasGraphifyNodes = firstNode?.isObject == true &&
            (firstNode?.has("label") == true || firstNode?.has("community") == true)
        val hasCommunitiesArray = communitiesNode?.isArray == true
        val isGraphifyFormat = !hasCommunitiesArray && (hasGraphifyLinks || hasGraphifyNodes)

        if (hasCommunitiesArray && !isGraphifyFormat) {
            parseLegacyFormat(root, nodes, edges, communities)
        } else if (isGraphifyFormat) {
            parseGraphifyFormat(root, nodes, edges, communities)
        } else {
            parseFlatFormat(root, nodes, edges)
        }

        return KnowledgeGraph(
            nodes = nodes,
            edges = edges,
            communities = communities
        )
    }

    private fun parseLegacyFormat(
        root: com.fasterxml.jackson.databind.JsonNode,
        nodes: MutableList<KnowledgeGraphNode>,
        edges: MutableList<KnowledgeGraphEdge>,
        communities: MutableList<KnowledgeGraphCommunity>
    ) {
        val communitiesNode = root.get("communities")!!
        for (communityNode in communitiesNode) {
            val communityName = communityNode.get("name")?.asText() ?: "unknown"
            val communityNodeNames = communityNode.get("nodes")?.map { it.asText() } ?: emptyList()

            val communityEdges = mutableListOf<KnowledgeGraphEdge>()

            val edgesNode = communityNode.get("edges")
            if (edgesNode != null && edgesNode.isArray) {
                for (edgeNode in edgesNode) {
                    val edge = parseEdgeNode(edgeNode) ?: continue
                    communityEdges.add(edge)
                    if (edges.none { it.source == edge.source && it.target == edge.target && it.type == edge.type }) {
                        edges.add(edge)
                    }
                }
            }

            for (nodeName in communityNodeNames) {
                if (nodes.none { it.name == nodeName }) {
                    nodes.add(KnowledgeGraphNode(name = nodeName, community = communityName))
                }
            }

            communities.add(
                KnowledgeGraphCommunity(
                    name = communityName,
                    nodes = communityNodeNames,
                    edges = communityEdges
                )
            )
        }

        val rootEdgesNode = root.get("edges")
        if (rootEdgesNode != null && rootEdgesNode.isArray) {
            for (edgeNode in rootEdgesNode) {
                val edge = parseEdgeNode(edgeNode) ?: continue
                if (edges.none { it.source == edge.source && it.target == edge.target && it.type == edge.type }) {
                    edges.add(edge)
                }
            }
        }

        val rootNodesNode = root.get("nodes")
        if (rootNodesNode != null && rootNodesNode.isArray) {
            for (nodeEntry in rootNodesNode) {
                val knNode = parseNodeEntry(nodeEntry) ?: continue
                val existingIndex = nodes.indexOfFirst { it.name == knNode.name }
                if (existingIndex >= 0) {
                    nodes[existingIndex] = knNode
                } else {
                    nodes.add(knNode)
                }
            }
        }
    }

    private fun parseGraphifyFormat(
        root: com.fasterxml.jackson.databind.JsonNode,
        nodes: MutableList<KnowledgeGraphNode>,
        edges: MutableList<KnowledgeGraphEdge>,
        communities: MutableList<KnowledgeGraphCommunity>
    ) {
        val communityMap = mutableMapOf<Int, MutableList<KnowledgeGraphNode>>()
        val idToLabel = mutableMapOf<String, String>()

        val rootNodesNode = root.get("nodes")
        if (rootNodesNode != null && rootNodesNode.isArray) {
            for (nodeEntry in rootNodesNode) {
                if (!nodeEntry.isObject) continue
                val label = nodeEntry.get("label")?.asText() ?: nodeEntry.get("name")?.asText() ?: continue
                val nodeId = nodeEntry.get("id")?.asText()
                val type = nodeEntry.get("file_type")?.asText() ?: nodeEntry.get("type")?.asText() ?: "class"
                val communityInt = nodeEntry.get("community")?.asInt()
                val communityStr = if (communityInt != null) "community_$communityInt" else
                    nodeEntry.get("community")?.asText() ?: ""
                val attributes = nodeEntry.get("attributes")
                    ?.map { it.asText() }
                    ?.toList() ?: emptyList()

                if (nodeId != null) {
                    idToLabel[nodeId] = label
                }

                val knNode = KnowledgeGraphNode(
                    name = label,
                    type = type,
                    community = communityStr,
                    attributes = attributes
                )
                nodes.add(knNode)

                if (communityInt != null) {
                    communityMap.getOrPut(communityInt) { mutableListOf() }.add(knNode)
                }
            }
        }

        val edgesNode = root.get("links") ?: root.get("edges")
        if (edgesNode != null && edgesNode.isArray) {
            for (edgeNode in edgesNode) {
                if (!edgeNode.isObject) continue
                val source = edgeNode.get("source")?.asText() ?: continue
                val target = edgeNode.get("target")?.asText() ?: continue
                val resolvedSource = idToLabel[source] ?: source
                val resolvedTarget = idToLabel[target] ?: target
                val label = edgeNode.get("relation")?.asText()
                    ?: edgeNode.get("label")?.asText() ?: ""
                val typeStr = edgeNode.get("confidence")?.asText()
                    ?: edgeNode.get("type")?.asText() ?: "EXTRACTED"
                val confidence = edgeNode.get("confidence_score")?.asDouble()
                    ?: (if (edgeNode.has("confidence") && edgeNode.get("confidence").isNumber) edgeNode.get("confidence").asDouble() else null)
                    ?: edgeNode.get("weight")?.asDouble()
                    ?: 1.0

                val edge = KnowledgeGraphEdge(
                    source = resolvedSource,
                    target = resolvedTarget,
                    label = label,
                    type = parseEdgeType(typeStr),
                    confidence = confidence
                )
                if (edges.none { it.source == edge.source && it.target == edge.target && it.type == edge.type }) {
                    edges.add(edge)
                }
            }
        }

        for ((communityInt, communityNodes) in communityMap.toSortedMap()) {
            val communityName = "community_$communityInt"
            val nodeNames = communityNodes.map { it.name }.toSet()
            val communityEdges = edges.filter { it.source in nodeNames || it.target in nodeNames }

            communities.add(
                KnowledgeGraphCommunity(
                    name = communityName,
                    nodes = nodeNames.toList(),
                    edges = communityEdges
                )
            )
        }
    }

    private fun parseEdgeNode(edgeNode: com.fasterxml.jackson.databind.JsonNode): KnowledgeGraphEdge? {
        if (edgeNode.isObject) {
            val source = edgeNode.get("source")?.asText() ?: return null
            val target = edgeNode.get("target")?.asText() ?: return null
            val label = edgeNode.get("label")?.asText() ?: ""
            val typeStr = edgeNode.get("type")?.asText() ?: "EXTRACTED"
            val confidence = edgeNode.get("confidence")?.asDouble() ?: 1.0
            return KnowledgeGraphEdge(
                source = source,
                target = target,
                label = label,
                type = parseEdgeType(typeStr),
                confidence = confidence
            )
        } else if (edgeNode.isTextual) {
            return parseEdgeString(edgeNode.asText())
        }
        return null
    }

    private fun parseNodeEntry(nodeEntry: com.fasterxml.jackson.databind.JsonNode): KnowledgeGraphNode? {
        if (nodeEntry.isObject) {
            val name = nodeEntry.get("name")?.asText() ?: return null
            val type = nodeEntry.get("type")?.asText() ?: "class"
            val community = nodeEntry.get("community")?.asText() ?: ""
            val attributes = nodeEntry.get("attributes")
                ?.map { it.asText() }
                ?.toList() ?: emptyList()
            return KnowledgeGraphNode(
                name = name,
                type = type,
                community = community,
                attributes = attributes
            )
        } else if (nodeEntry.isTextual) {
            return KnowledgeGraphNode(name = nodeEntry.asText())
        }
        return null
    }

    private fun parseEdgeType(typeStr: String): EdgeType {
        return when (typeStr.uppercase()) {
            "EXTRACTED" -> EdgeType.EXTRACTED
            "INFERRED" -> EdgeType.INFERRED
            "AMBIGUOUS" -> EdgeType.AMBIGUOUS
            else -> EdgeType.EXTRACTED
        }
    }

    private fun parseEdgeString(edgeStr: String): KnowledgeGraphEdge? {
        val arrowPatterns = listOf("--x", "..>", "-->", "->")
        for (arrow in arrowPatterns) {
            if (edgeStr.contains(arrow)) {
                val parts = edgeStr.split(arrow, limit = 2)
                if (parts.size == 2) {
                    val source = parts[0].trim()
                    val targetAndLabel = parts[1].trim()
                    val labelParts = targetAndLabel.split(":", limit = 2)
                    val target = labelParts[0].trim()
                    val label = if (labelParts.size > 1) labelParts[1].trim() else ""
                    val type = when (arrow) {
                        "..>" -> EdgeType.INFERRED
                        "--x" -> EdgeType.AMBIGUOUS
                        else -> EdgeType.EXTRACTED
                    }
                    return KnowledgeGraphEdge(source = source, target = target, label = label, type = type)
                }
            }
        }
        return null
    }

    private fun parseFlatFormat(
        root: com.fasterxml.jackson.databind.JsonNode,
        nodes: MutableList<KnowledgeGraphNode>,
        edges: MutableList<KnowledgeGraphEdge>
    ) {
        val rootNodesNode = root.get("nodes")
        if (rootNodesNode != null && rootNodesNode.isArray) {
            for (nodeEntry in rootNodesNode) {
                val knNode = parseNodeEntry(nodeEntry) ?: continue
                if (nodes.none { it.name == knNode.name }) {
                    nodes.add(knNode)
                }
            }
        }

        val edgesNode = root.get("links") ?: root.get("edges")
        if (edgesNode != null && edgesNode.isArray) {
            for (edgeNode in edgesNode) {
                val edge = parseEdgeNode(edgeNode) ?: continue
                if (edges.none { it.source == edge.source && it.target == edge.target && it.type == edge.type }) {
                    edges.add(edge)
                }
            }
        }
    }
}