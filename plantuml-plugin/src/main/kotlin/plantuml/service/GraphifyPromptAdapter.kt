package plantuml.service

import com.fasterxml.jackson.databind.ObjectMapper
import plantuml.PlantumlMessages
import plantuml.rag.DocKnowledgeBase
import java.io.File

class GraphifyPromptAdapter(
    private val graphFile: File,
    private val promptsDir: File,
    private val docKnowledgeBase: DocKnowledgeBase = DocKnowledgeBase.forPlantUML(),
) {

    private val mapper = ObjectMapper()

    /**
     * Result of generating a prompt for a single subgraph.
     *
     * @property promptFile The generated `.prompt` file
     * @property communityName Name of the community
     * @property nodes Node names in the community
     * @property edges Edge descriptions in the community
     */
    data class SubgraphResult(
        val promptFile: File,
        val communityName: String,
        val nodes: List<String>,
        val edges: List<String>
    )

    /**
     * Generates a prompt file for a specific subgraph by name.
     *
     * @param subgraphName Community name to match (case-insensitive substring)
     * @return The [SubgraphResult] with the generated prompt file
     * @throws IllegalArgumentException if no matching community is found
     */
    fun generatePrompt(subgraphName: String): SubgraphResult {
        val graph = mapper.readTree(graphFile)

        val communities = graph.get("communities")
        val community = communities?.find {
            it.get("name")?.asText()?.contains(subgraphName, ignoreCase = true) == true
        } ?: communities?.first()
            ?: throw IllegalArgumentException(PlantumlMessages.format("graphify.no_community", "en", subgraphName))

        val communityName = community.get("name").asText()
        val nodes = community.get("nodes")?.map { it.asText() } ?: emptyList()
        val edges = community.get("edges")?.map { it.asText() } ?: emptyList()

        val promptContent = buildPrompt(communityName, nodes, edges)

        if (!promptsDir.exists()) {
            promptsDir.mkdirs()
        }

        val promptFile = promptsDir.resolve("auto-${communityName.replace(" ", "-")}.prompt")
        promptFile.writeText(promptContent)

        return SubgraphResult(promptFile, communityName, nodes, edges)
    }

    /**
     * Generates prompt files for all communities in the graph.
     *
     * @return List of [SubgraphResult] for every community
     * @throws IllegalArgumentException if the graph has no communities
     */
    fun generateAllPrompts(): List<SubgraphResult> {
        val graph = mapper.readTree(graphFile)
        val communities = graph.get("communities")
            ?: throw IllegalArgumentException(PlantumlMessages.get("graphify.no_communities"))

        return communities.map { community ->
            val name = community.get("name").asText()
            generatePrompt(name)
        }
    }

    private fun buildPrompt(
        communityName: String,
        nodes: List<String>,
        edges: List<String>
    ): String {
        val queryText = "class diagram component $communityName ${nodes.joinToString(" ")}"
        val docContext = docKnowledgeBase.queryContext(queryText)
        val docSection = if (docContext.isNotBlank()) {
            """PlantUML syntax reference (use ONLY valid PlantUML syntax):
$docContext

"""
        } else ""

        return """${docSection}Generate a PlantUML class diagram for the "$communityName" module of the plantuml-gradle plugin.

Classes and their relationships:

${nodes.joinToString("\n") { "- $it" }}

Relationships:

${edges.ifEmpty { listOf("(no explicit edges — infer from class names)") }.joinToString("\n") { "- $it" }}

Requirements:
- Use EXTRACTED relations only (ignore INFERRED)
- Include class names with their key attributes
- Use proper UML relations: -->, ..>, *--, o--
- No style directives, no skinparam, no colors
- Structure only
        """.trimIndent()
    }
}