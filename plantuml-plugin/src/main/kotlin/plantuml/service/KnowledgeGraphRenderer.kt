package plantuml.service

import plantuml.EdgeType
import plantuml.KnowledgeGraph
import plantuml.KnowledgeGraphCommunity
import plantuml.KnowledgeGraphEdge
import plantuml.KnowledgeGraphNode
import plantuml.boundary.TranslationResolver

class KnowledgeGraphRenderer {

    private val communityColors = listOf(
        "#E8F4FD", "#FFF3CD", "#D4EDDA", "#F8D7DA", "#D6D8DB",
        "#CCE5FF", "#FFF0F0", "#E8F5E9", "#F3E5F5", "#FFFACD",
        "#E0F7FA", "#FBE9E7", "#F1F8E9", "#EDE7F6", "#E1F5FE"
    )

    private val communityBorderColors = listOf(
        "#3B82F6", "#F59E0B", "#10B981", "#EF4444", "#6B7280",
        "#2563EB", "#DC2626", "#059669", "#8B5CF6", "#D97706",
        "#0891B2", "#EA580C", "#65A30D", "#7C3AED", "#0284C7"
    )

    private val methodPatterns = listOf(
        Regex(".*\\(\\)$"),
        Regex("^\\..+"),
        Regex(".*`.*`.*")
    )

    fun render(
        graph: KnowledgeGraph,
        communityFilter: String? = null,
        edgeTypes: Set<EdgeType> = EdgeType.entries.toSet(),
        minConfidence: Double = 0.0,
        maxNodes: Int = Int.MAX_VALUE,
        nodeTypes: Set<String>? = null,
        resolver: TranslationResolver? = null,
        language: String = "en"
    ): String {
        val label = { text: String ->
            resolver?.resolve(text, language)?.translated ?: text
        }
        val filteredGraph = applyFilters(graph, communityFilter, edgeTypes, minConfidence, maxNodes, nodeTypes)

        if (filteredGraph.communities.isEmpty() && filteredGraph.nodes.isEmpty()) {
            return "@startuml\ntitle ${label("Empty Knowledge Graph")}\n@enduml"
        }

        val sb = StringBuilder()
        sb.appendLine("@startuml")
        sb.appendLine()
        sb.appendLine("left to right direction")
        sb.appendLine()
        sb.appendLine("skinparam backgroundColor #FEFEFE")
        sb.appendLine("skinparam packageBorderColor #333333")
        sb.appendLine("skinparam packageFontSize 14")
        sb.appendLine("skinparam nodeFontSize 12")
        sb.appendLine("skinparam arrowThickness 1.5")
        sb.appendLine("skinparam nodesep 40")
        sb.appendLine("skinparam ranksep 60")
        sb.appendLine()

        addLegend(sb, edgeTypes, communityFilter, nodeTypes, label)

        val filteredNodeNames = filteredGraph.nodes.map { it.name }.toSet()
        val nodesByCommunity = filteredGraph.nodes.groupBy { it.community }
        val communityIdx = mutableMapOf<String, Int>()

        val communitiesToRender = if (filteredGraph.communities.isNotEmpty()) {
            filteredGraph.communities
        } else {
            nodesByCommunity.keys.map { communityName ->
                val commNodes = nodesByCommunity[communityName] ?: emptyList()
                KnowledgeGraphCommunity(
                    name = communityName,
                    nodes = commNodes.map { it.name },
                    edges = filteredGraph.edges.filter { it.source in commNodes.map { n -> n.name } || it.target in commNodes.map { n -> n.name } }
                )
            }
        }

        for ((idx, community) in communitiesToRender.withIndex()) {
            val colorPair = if (community.color.isNotBlank()) {
                community.color to community.color
            } else {
                communityColors[idx % communityColors.size] to communityBorderColors[idx % communityBorderColors.size]
            }

            communityIdx[community.name] = idx
            val bgColor = colorPair.first
            val borderColor = colorPair.second

            sb.appendLine("package \"${community.name}\" {")
            sb.appendLine("    skinparam packageBackgroundColor $bgColor")
            sb.appendLine("    skinparam packageBorderColor $borderColor")

            val communityNodeNames = (community.nodes.ifEmpty {
                nodesByCommunity[community.name]?.map { it.name } ?: emptyList()
            }).filter { it in filteredNodeNames }

            val nodesByType = communityNodeNames
                .mapNotNull { name -> filteredGraph.nodes.find { it.name == name } }
                .groupBy { it.type }

            val classNodeNames = (nodesByType["class"] ?: emptyList()).map { it.name }
            val fileNodeNames = (nodesByType["code"] ?: emptyList()).map { it.name }
            val otherNodes = communityNodeNames.filter { it !in classNodeNames && it !in fileNodeNames }

            if (classNodeNames.isNotEmpty()) {
                sb.appendLine("    folder \"${label("Classes")}\" {")
                for (nodeName in classNodeNames) {
                    val nodeInfo = filteredGraph.nodes.find { it.name == nodeName }
                    if (nodeInfo != null) renderNode(sb, nodeInfo, indent = 8)
                    else sb.appendLine("        class \"${cleanLabel(nodeName)}\" as ${sanitizeId(nodeName)}")
                }
                sb.appendLine("    }")
            }

            if (fileNodeNames.isNotEmpty()) {
                sb.appendLine("    folder \"${label("Files")}\" {")
                for (nodeName in fileNodeNames) {
                    val nodeInfo = filteredGraph.nodes.find { it.name == nodeName }
                    if (nodeInfo != null) renderNode(sb, nodeInfo, indent = 8)
                    else sb.appendLine("        node \"${cleanLabel(nodeName)}\" as ${sanitizeId(nodeName)}")
                }
                sb.appendLine("    }")
            }

            if (otherNodes.isNotEmpty()) {
                for (nodeName in otherNodes) {
                    val nodeInfo = filteredGraph.nodes.find { it.name == nodeName }
                    if (nodeInfo != null) renderNode(sb, nodeInfo, indent = 4)
                    else sb.appendLine("    node \"${cleanLabel(nodeName)}\" as ${sanitizeId(nodeName)}")
                }
            }

            sb.appendLine("}")
            sb.appendLine()
        }

        val allRenderedNodes = communitiesToRender
            .flatMap { c ->
                c.nodes.ifEmpty {
                    nodesByCommunity[c.name]?.map { it.name } ?: emptyList()
                }
            }
            .take(maxNodes)
            .toSet()

        val renderedEdges = filteredGraph.edges.filter {
            it.source in allRenderedNodes && it.target in allRenderedNodes
        }

        val crossCommunityEdges = renderedEdges.filter { edge ->
            val sourceCommunity = communitiesToRender.find { c ->
                val cnodes = c.nodes.ifEmpty { nodesByCommunity[c.name]?.map { it.name } ?: emptyList() }
                edge.source in cnodes
            }
            val targetCommunity = communitiesToRender.find { c ->
                val cnodes = c.nodes.ifEmpty { nodesByCommunity[c.name]?.map { it.name } ?: emptyList() }
                edge.target in cnodes
            }
            sourceCommunity != null && targetCommunity != null && sourceCommunity != targetCommunity
        }

        if (crossCommunityEdges.isNotEmpty()) {
            sb.appendLine("' ${label("Cross-community edges")}")
            for (edge in crossCommunityEdges) {
                renderEdge(sb, edge)
            }
            sb.appendLine()
        }

        val intraCommunityEdges = renderedEdges.filter { edge ->
            edge !in crossCommunityEdges
        }

        if (intraCommunityEdges.isNotEmpty()) {
            sb.appendLine("' ${label("Intra-community edges")}")
            for (edge in intraCommunityEdges) {
                renderEdge(sb, edge)
            }
            sb.appendLine()
        }

        val unassignedNodes = filteredGraph.nodes.filter { it.name !in allRenderedNodes }
        if (unassignedNodes.isNotEmpty()) {
            sb.appendLine("' ${label("Unassigned nodes")}")
            for (node in unassignedNodes) {
                renderNode(sb, node, indent = 0)
            }
            sb.appendLine()
        }

        sb.appendLine("@enduml")
        return sb.toString()
    }

    private fun applyFilters(
        graph: KnowledgeGraph,
        communityFilter: String?,
        edgeTypes: Set<EdgeType>,
        minConfidence: Double,
        maxNodes: Int,
        nodeTypes: Set<String>?
    ): KnowledgeGraph {
        val filteredCommunities = if (communityFilter != null) {
            graph.communities.filter {
                it.name.contains(communityFilter, ignoreCase = true)
            }
        } else {
            graph.communities
        }

        val communityNames = filteredCommunities.map { it.name }.toSet()

        var filteredNodes = if (communityFilter != null) {
            val communityNodes = filteredCommunities.flatMap { it.nodes }.toSet()
            graph.nodes.filter { it.name in communityNodes || it.community in communityNames }
        } else {
            graph.nodes
        }

        if (nodeTypes != null) {
            filteredNodes = filteredNodes.filter { it.type in nodeTypes }
        }

        val limitedNodes = filteredNodes.take(maxNodes)
        val nodeNames = limitedNodes.map { it.name }.toSet()

        val filteredEdges = graph.edges
            .filter { it.type in edgeTypes }
            .filter { it.confidence >= minConfidence }
            .filter { it.source in nodeNames || it.target in nodeNames }

        return KnowledgeGraph(
            nodes = limitedNodes,
            edges = filteredEdges,
            communities = filteredCommunities
        )
    }

    private fun renderNode(sb: StringBuilder, node: KnowledgeGraphNode, indent: Int = 4) {
        val pad = " ".repeat(indent)
        val id = sanitizeId(node.name)
        val label = cleanLabel(node.name)
        if (node.attributes.isNotEmpty()) {
            sb.appendLine("${pad}node \"$label\" as $id {")
            for (attr in node.attributes.take(5)) {
                sb.appendLine("${pad}    $attr")
            }
            sb.appendLine("$pad}")
        } else {
            sb.appendLine("${pad}node \"$label\" as $id")
        }
    }

    private fun renderEdge(sb: StringBuilder, edge: KnowledgeGraphEdge) {
        val sourceId = sanitizeId(edge.source)
        val targetId = sanitizeId(edge.target)
        val label = if (edge.label.isNotBlank()) " : ${edge.label}" else ""
        val confidenceLabel = if (edge.type == EdgeType.INFERRED && edge.confidence < 1.0) {
            " (${String.format("%.0f%%", edge.confidence * 100)})"
        } else ""

        when (edge.type) {
            EdgeType.EXTRACTED -> sb.appendLine("$sourceId --> $targetId$label$confidenceLabel")
            EdgeType.INFERRED -> sb.appendLine("$sourceId ..> $targetId$label$confidenceLabel")
            EdgeType.AMBIGUOUS -> sb.appendLine("$sourceId --x $targetId$label")
        }
    }

    private fun addLegend(
        sb: StringBuilder,
        edgeTypes: Set<EdgeType>,
        communityFilter: String?,
        nodeTypes: Set<String>?,
        label: (String) -> String = { it }
    ) {
        sb.appendLine("legend right")
        sb.appendLine("  | Type | Arrow |")
        sb.appendLine("  |------|-------|")
        if (EdgeType.EXTRACTED in edgeTypes) {
            sb.appendLine("  | ${label("Extracted")} | --> |")
        }
        if (EdgeType.INFERRED in edgeTypes) {
            sb.appendLine("  | ${label("Inferred")}  | ..> |")
        }
        if (EdgeType.AMBIGUOUS in edgeTypes) {
            sb.appendLine("  | ${label("Ambiguous")} | --x |")
        }
        if (communityFilter != null) {
            sb.appendLine("  | Filter | $communityFilter |")
        }
        if (nodeTypes != null) {
            sb.appendLine("  | Node types | ${nodeTypes.joinToString(", ")} |")
        }
        sb.appendLine("endlegend")
        sb.appendLine()
    }

    fun sanitizeId(name: String): String {
        val sanitized = cleanLabel(name).replace(" ", "_")
            .replace(".", "_")
            .replace("(", "")
            .replace(")", "")
            .replace("[", "_")
            .replace("]", "")
            .replace("/", "_")
            .replace("\\", "_")
            .replace("-", "_")
            .replace("\"", "")
            .replace("'", "")
            .replace("`", "")
            .replace(",", "_")
        return if (sanitized.first().isDigit()) "_$sanitized" else sanitized
    }

    private fun cleanLabel(name: String): String {
        return name.replace(Regex("^\\.+"), "")
            .replace(Regex("`"), "")
            .trim()
    }

    fun isMethodNode(node: KnowledgeGraphNode): Boolean {
        return methodPatterns.any { it.matches(node.name) }
    }
}