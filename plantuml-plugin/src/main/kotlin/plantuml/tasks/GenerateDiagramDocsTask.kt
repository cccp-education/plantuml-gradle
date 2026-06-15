package plantuml.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import plantuml.PlantumlManager
import plantuml.PlantumlMessages
import plantuml.service.GraphifyPromptAdapter
import java.io.File

@DisableCachingByDefault(because = "Depends on Graphify output which may change")
abstract class GenerateDiagramDocsTask : DefaultTask() {

    init {
        val lang = PlantumlManager.resolveLanguage(project)
        group = PlantumlMessages.get("task.diagramdocs.group", lang)
        description = PlantumlMessages.get("task.diagramdocs.description", lang)
    }

    @TaskAction
    fun generateDocs() {
        val graphFile = project.rootDir.resolve("graphify-out/graph.json")

        if (!graphFile.exists()) {
            throw GradleException(
                "graphify-out/graph.json not found. Run 'graphify . --no-viz' first to build the knowledge graph."
            )
        }

        logger.lifecycle("Reading knowledge graph from: ${graphFile.absolutePath}")

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
            logger.lifecycle("Generating prompts for all communities...")
            adapter.generateAllPrompts()
        } else {
            val targetSubgraph = subgraph ?: "service layer"
            logger.lifecycle("Generating prompt for subgraph: $targetSubgraph")
            listOf(adapter.generatePrompt(targetSubgraph))
        }

        results.forEach { result ->
            logger.lifecycle(
                "Generated prompt: {} ({} nodes, {} edges)",
                result.promptFile.name, result.nodes.size, result.edges.size
            )
        }

        logger.lifecycle(
            "Generated {} prompt(s). Run 'generatePlantumlDiagrams' to render diagrams.",
            results.size
        )
    }
}