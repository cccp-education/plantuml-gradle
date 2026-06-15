package plantuml.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import plantuml.EdgeType
import plantuml.PlantumlManager
import plantuml.PlantumlMessages
import plantuml.service.KnowledgeGraphParser
import plantuml.service.KnowledgeGraphRenderer
import plantuml.service.PlantumlService
import java.io.File

@DisableCachingByDefault(because = "Depends on graph.json which may change")
abstract class GenerateKnowledgeGraphDiagramTask : DefaultTask() {

    init {
        val lang = PlantumlManager.resolveLanguage(project)
        group = PlantumlMessages.get("task.kg.group", lang)
        description = PlantumlMessages.get("task.kg.description", lang)
    }

    @TaskAction
    fun generateKnowledgeGraph() {
        val graphFile = project.rootDir.resolve("graphify-out/graph.json")

        if (!graphFile.exists()) {
            throw GradleException(
                "graphify-out/graph.json not found. Run 'graphify . --no-viz' first to build the knowledge graph."
            )
        }

        logger.lifecycle("Reading knowledge graph from: ${graphFile.absolutePath}")

        val communityFilter = project.findProperty("plantuml.kg.community")?.toString()
        val edgeTypesStr = project.findProperty("plantuml.kg.edgeTypes")?.toString()
        val minConfidenceStr = project.findProperty("plantuml.kg.minConfidence")?.toString()
        val maxNodesStr = project.findProperty("plantuml.kg.maxNodes")?.toString()
        val outputDirStr = project.findProperty("plantuml.kg.outputDir")?.toString() ?: "diagrams/knowledge-graph"

        val nodeTypesStr = project.findProperty("plantuml.kg.nodeTypes")?.toString()
        val edgeTypes = if (edgeTypesStr != null) {
            edgeTypesStr.split(",").map { parseEdgeType(it.trim()) }.toSet()
        } else {
            EdgeType.entries.toSet()
        }

        val minConfidence = minConfidenceStr?.toDoubleOrNull() ?: 0.0
        val maxNodes = maxNodesStr?.toIntOrNull() ?: Int.MAX_VALUE
        val nodeTypes = if (nodeTypesStr != null) {
            nodeTypesStr.split(",").map { it.trim().lowercase() }.toSet()
        } else {
            null
        }

        val parser = KnowledgeGraphParser(graphFile)
        val graph = parser.parse()

        logger.lifecycle(
            "Knowledge graph: {} nodes, {} edges, {} communities",
            graph.nodes.size, graph.edges.size, graph.communities.size
        )

        val renderer = KnowledgeGraphRenderer()
        val plantumlCode = renderer.render(
            graph,
            communityFilter = communityFilter,
            edgeTypes = edgeTypes,
            minConfidence = minConfidence,
            maxNodes = maxNodes,
            nodeTypes = nodeTypes
        )

        val outputDir = project.file(outputDirStr)
        outputDir.mkdirs()

        val fileNameSuffix = if (communityFilter != null) {
            "-${communityFilter.replace(" ", "-")}"
        } else {
            "-full"
        }
        val pumlFile = File(outputDir, "knowledge-graph${fileNameSuffix}.puml")
        pumlFile.writeText(plantumlCode)
        logger.lifecycle("Generated PlantUML: {}", pumlFile.absolutePath)

        val plantumlService = PlantumlService()
        val validationResult = plantumlService.validateSyntax(plantumlCode)
        if (validationResult is PlantumlService.SyntaxValidationResult.Invalid) {
            logger.warn("Generated PlantUML has validation issues: {}", validationResult.errorMessage)
            logger.lifecycle("PlantUML file saved despite validation issues: {}", pumlFile.absolutePath)
        } else {
            val imageFile = File(outputDir, "knowledge-graph${fileNameSuffix}.png")
            plantumlService.generateImage(plantumlCode, imageFile)
            logger.lifecycle("Generated PNG: {}", imageFile.absolutePath)
        }

        logger.lifecycle(
            "Knowledge graph diagram generated successfully ({} nodes, {} edges, {} communities)",
            graph.nodes.size, graph.edges.size, graph.communities.size
        )
    }

    private fun parseEdgeType(typeStr: String): EdgeType {
        return when (typeStr.uppercase()) {
            "EXTRACTED" -> EdgeType.EXTRACTED
            "INFERRED" -> EdgeType.INFERRED
            "AMBIGUOUS" -> EdgeType.AMBIGUOUS
            else -> {
                logger.warn("Unknown edge type '{}', defaulting to EXTRACTED", typeStr)
                EdgeType.EXTRACTED
            }
        }
    }
}