package plantuml.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import plantuml.PlantumlManager
import plantuml.PlantumlMessages
import plantuml.service.GraphifyPromptAdapter
import java.io.File

/**
 * Gradle task: `generateDiagramDocs`
 *
 * Generates PlantUML prompt files from a graphify knowledge graph for
 * automated diagram documentation. Reads `graphify-out/graph.json` and
 * creates `.prompt` files for each community (subgraph).
 *
 * **Usage**:
 * ```bash
 * # Generate prompts for a specific subgraph
 * ./gradlew generateDiagramDocs -Pplantuml.diagram.subgraph="service layer"
 *
 * # Generate prompts for all communities
 * ./gradlew generateDiagramDocs -Pplantuml.diagram.all=true
 * ```
 */
@DisableCachingByDefault(because = "Depends on Graphify output which may change")
abstract class GenerateDiagramDocsTask : DefaultTask() {

    private val lang: String = PlantumlManager.resolveLanguage(project)

    init {
        group = PlantumlMessages.get("task.diagramdocs.group", lang)
        description = PlantumlMessages.get("task.diagramdocs.description", lang)
    }

    /**
     * Main task action: generates prompt files from the knowledge graph.
     *
     * @throws GradleException if `graphify-out/graph.json` does not exist
     */
    @TaskAction
    fun generateDocs() {
        val graphFile = project.rootDir.resolve("graphify-out/graph.json")

        if (!graphFile.exists()) {
            throw GradleException(
                PlantumlMessages.get("diagramdocs.graph_not_found", lang)
            )
        }

        logger.lifecycle(PlantumlMessages.format("diagramdocs.reading", lang, graphFile.absolutePath))

        val subgraph = project.findProperty("plantuml.diagram.subgraph")?.toString()
        val generateAll = project.findProperty("plantuml.diagram.all")?.toString() == "true"

        val promptsDir = project.file("prompts")
        promptsDir.mkdirs()

        val outputDir = project.findProperty("plantuml.diagram.outputDir")?.toString()
            ?: "diagrams/auto"
        val autoOutputDir = project.file(outputDir)
        autoOutputDir.mkdirs()

        val adapter = GraphifyPromptAdapter(graphFile, promptsDir)

        val results = if (generateAll) {
            logger.lifecycle(PlantumlMessages.get("diagramdocs.all_communities", lang))
            adapter.generateAllPrompts()
        } else {
            val targetSubgraph = subgraph ?: "service layer"
            logger.lifecycle(PlantumlMessages.format("diagramdocs.subgraph", lang, targetSubgraph))
            listOf(adapter.generatePrompt(targetSubgraph))
        }

        results.forEach { result ->
            logger.lifecycle(
                PlantumlMessages.format("diagramdocs.generated_prompt", lang, result.promptFile.name, result.nodes.size, result.edges.size)
            )
        }

        logger.lifecycle(
            PlantumlMessages.format("diagramdocs.summary", lang, results.size)
        )
    }
}