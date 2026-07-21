package plantuml.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import plantuml.EdgeType
import plantuml.PlantumlManager
import plantuml.PlantumlMessages
import plantuml.boundary.IdiomaticGlossary
import plantuml.boundary.NonTranslatableTermRegistry
import plantuml.boundary.TextClassifier
import plantuml.boundary.TranslationResolver
import plantuml.service.KnowledgeGraphParser
import plantuml.service.KnowledgeGraphRenderer
import plantuml.service.PlantumlService
import java.io.File

/**
 * Gradle task: `generateKnowledgeGraphDiagram`
 *
 * Generates a PlantUML diagram from a graphify knowledge graph JSON file.
 * Reads `graphify-out/graph.json`, parses it into a [KnowledgeGraph],
 * renders it as PlantUML source, validates syntax, and generates a PNG image.
 *
 * **Configuration** (via CLI properties):
 * - `-Pplantuml.kg.community=<name>` — Filter by community name
 * - `-Pplantuml.kg.edgeTypes=EXTRACTED,INFERRED` — Comma-separated edge types
 * - `-Pplantuml.kg.minConfidence=0.5` — Minimum edge confidence
 * - `-Pplantuml.kg.maxNodes=50` — Maximum nodes to render
 * - `-Pplantuml.kg.nodeTypes=class,code` — Filter by node type
 * - `-Pplantuml.kg.outputDir=diagrams/kg` — Output directory
 *
 * **Usage**:
 * ```bash
 * ./gradlew generateKnowledgeGraphDiagram
 * ```
 */
@DisableCachingByDefault(because = "Depends on graph.json which may change")
abstract class GenerateKnowledgeGraphDiagramTask : DefaultTask() {

    private val lang: String = PlantumlManager.resolveLanguage(project)

    init {
        group = PlantumlMessages.get("task.kg.group", lang)
        description = PlantumlMessages.get("task.kg.description", lang)
    }

    /**
     * Main task action: generates a knowledge graph PlantUML diagram.
     *
     * @throws GradleException if `graphify-out/graph.json` does not exist
     */
    @TaskAction
    fun generateKnowledgeGraph() {
        val graphFile = project.rootDir.resolve("graphify-out/graph.json")

        if (!graphFile.exists()) {
            throw GradleException(
                PlantumlMessages.format("kgparser.file_not_found", lang, graphFile.absolutePath)
            )
        }

        logger.lifecycle(PlantumlMessages.format("kg.reading", lang, graphFile.absolutePath))

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
            PlantumlMessages.format("kg.stats", lang, graph.nodes.size, graph.edges.size, graph.communities.size)
        )

        val renderer = KnowledgeGraphRenderer()
        val resolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = IdiomaticGlossary(),
            messageResolver = { key, l -> runCatching { PlantumlMessages.get(key, l) }.getOrNull() },
            nonTranslatableRegistry = NonTranslatableTermRegistry()
        )
        val plantumlCode = renderer.render(
            graph,
            communityFilter = communityFilter,
            edgeTypes = edgeTypes,
            minConfidence = minConfidence,
            maxNodes = maxNodes,
            nodeTypes = nodeTypes,
            resolver = resolver,
            language = lang
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
        logger.lifecycle(PlantumlMessages.format("kg.generated_puml", lang, pumlFile.absolutePath))

        val plantumlService = PlantumlService()
        val validationResult = plantumlService.validateSyntax(plantumlCode)
        if (validationResult is PlantumlService.SyntaxValidationResult.Invalid) {
            logger.warn(PlantumlMessages.format("kg.validation_issues", lang, validationResult.errorMessage))
            logger.lifecycle(PlantumlMessages.format("kg.saved_despite_issues", lang, pumlFile.absolutePath))
        } else {
            val imageFile = File(outputDir, "knowledge-graph${fileNameSuffix}.png")
            plantumlService.generateImage(plantumlCode, imageFile)
            logger.lifecycle(PlantumlMessages.format("kg.generated_png", lang, imageFile.absolutePath))
        }

        logger.lifecycle(
            PlantumlMessages.format("kg.success", lang, graph.nodes.size, graph.edges.size, graph.communities.size)
        )
    }

    private fun parseEdgeType(typeStr: String): EdgeType {
        return when (typeStr.uppercase()) {
            "EXTRACTED" -> EdgeType.EXTRACTED
            "INFERRED" -> EdgeType.INFERRED
            "AMBIGUOUS" -> EdgeType.AMBIGUOUS
            else -> {
                logger.warn(PlantumlMessages.format("kg.unknown_edge", lang, typeStr))
                EdgeType.EXTRACTED
            }
        }
    }
}