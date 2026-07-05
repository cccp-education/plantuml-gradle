package plantuml.service

import com.fasterxml.jackson.databind.ObjectMapper
import plantuml.PlantumlMessages
import java.io.File

class GraphifyPromptAdapter(
    private val graphFile: File,
    private val promptsDir: File
) {

    private val mapper = ObjectMapper()

    data class SubgraphResult(
        val promptFile: File,
        val communityName: String,
        val nodes: List<String>,
        val edges: List<String>
    )

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
        return """
Generate a PlantUML class diagram for the "$communityName" module of the plantuml-gradle plugin.

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