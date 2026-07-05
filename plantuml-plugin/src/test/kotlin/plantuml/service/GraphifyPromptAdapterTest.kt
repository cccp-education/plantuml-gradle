package plantuml.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import plantuml.PlantumlMessages

class GraphifyPromptAdapterTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var promptsDir: File
    private lateinit var graphFile: File
    private lateinit var adapter: GraphifyPromptAdapter
    private val mapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        promptsDir = File(tempDir, "prompts").apply { mkdirs() }
        graphFile = File(tempDir, "graph.json")
        adapter = GraphifyPromptAdapter(graphFile, promptsDir)
    }

    private fun createGraphJson(communities: List<Map<String, Any>>): String {
        val graph = mapOf("communities" to communities)
        return mapper.writeValueAsString(graph)
    }

    @Test
    fun `should generate prompt for single subgraph`() {
        val communities = listOf(
            mapOf(
                "name" to "service layer",
                "nodes" to listOf("LlmService", "DiagramProcessor", "PlantumlService"),
                "edges" to listOf("LlmService --> DiagramProcessor", "DiagramProcessor --> PlantumlService")
            )
        )
        graphFile.writeText(createGraphJson(communities))

        val result = adapter.generatePrompt("service layer")

        assertEquals("service layer", result.communityName)
        assertEquals(listOf("LlmService", "DiagramProcessor", "PlantumlService"), result.nodes)
        assertEquals(listOf("LlmService --> DiagramProcessor", "DiagramProcessor --> PlantumlService"), result.edges)
        assertTrue(result.promptFile.exists())
        assertTrue(result.promptFile.name.startsWith("auto-service-layer"))
        val content = result.promptFile.readText()
        assertTrue(content.contains("service layer"))
        assertTrue(content.contains("LlmService"))
        assertTrue(content.contains("LlmService --> DiagramProcessor"))
    }

    @Test
    fun `should generate prompts for all communities`() {
        val communities = listOf(
            mapOf(
                "name" to "service layer",
                "nodes" to listOf("LlmService"),
                "edges" to listOf("LlmService --> DiagramProcessor")
            ),
            mapOf(
                "name" to "config layer",
                "nodes" to listOf("ConfigLoader"),
                "edges" to listOf("ConfigLoader --> ConfigMerger")
            )
        )
        graphFile.writeText(createGraphJson(communities))

        val results = adapter.generateAllPrompts()

        assertEquals(2, results.size)
        assertEquals("service layer", results[0].communityName)
        assertEquals("config layer", results[1].communityName)
        assertTrue(promptsDir.listFiles()!!.size >= 2)
    }

    @Test
    fun `should throw when graph file not found`() {
        val missingFile = File(tempDir, "nonexistent.json")
        val missingAdapter = GraphifyPromptAdapter(missingFile, promptsDir)

        assertFailsWith<Exception> {
            missingAdapter.generatePrompt("service layer")
        }
    }

    @Test
    fun `should throw when no community matches and communities list is empty`() {
        val communities = emptyList<Map<String, Any>>()
        graphFile.writeText(createGraphJson(communities))

        assertFailsWith<NoSuchElementException> {
            adapter.generatePrompt("nonexistent layer")
        }
    }

    @Test
    fun `should use first community as fallback when no exact match`() {
        val communities = listOf(
            mapOf(
                "name" to "model layer",
                "nodes" to listOf("PlantumlConfig"),
                "edges" to listOf("PlantumlConfig --> InputConfig")
            )
        )
        graphFile.writeText(createGraphJson(communities))

        val result = adapter.generatePrompt("nonexistent layer")

        assertEquals("model layer", result.communityName)
        assertEquals(listOf("PlantumlConfig"), result.nodes)
    }

    @Test
    fun `should handle empty edges list`() {
        val communities = listOf(
            mapOf(
                "name" to "task layer",
                "nodes" to listOf("GeneratePlantumlDiagramsTask", "ValidatePlantumlSyntaxTask"),
                "edges" to emptyList<String>()
            )
        )
        graphFile.writeText(createGraphJson(communities))

        val result = adapter.generatePrompt("task layer")

        val content = result.promptFile.readText()
        assertTrue(content.contains("(no explicit edges"))
    }

    @Test
    fun `should create prompts directory if it does not exist`() {
        val newPromptsDir = File(tempDir, "new-prompts")
        val newAdapter = GraphifyPromptAdapter(graphFile, newPromptsDir)

        val communities = listOf(
            mapOf(
                "name" to "service layer",
                "nodes" to listOf("LlmService"),
                "edges" to emptyList<String>()
            )
        )
        graphFile.writeText(createGraphJson(communities))

        val result = newAdapter.generatePrompt("service layer")

        assertTrue(newPromptsDir.exists())
        assertTrue(result.promptFile.exists())
    }

    @Test
    fun `should perform case-insensitive subgraph matching`() {
        val communities = listOf(
            mapOf(
                "name" to "Service Layer",
                "nodes" to listOf("LlmService"),
                "edges" to emptyList<String>()
            )
        )
        graphFile.writeText(createGraphJson(communities))

        val result = adapter.generatePrompt("service layer")

        assertEquals("Service Layer", result.communityName)
    }

    @Test
    fun `should replace spaces with dashes in prompt filename`() {
        val communities = listOf(
            mapOf(
                "name" to "config layer",
                "nodes" to listOf("ConfigLoader"),
                "edges" to emptyList<String>()
            )
        )
        graphFile.writeText(createGraphJson(communities))

        val result = adapter.generatePrompt("config layer")

        assertTrue(result.promptFile.name.startsWith("auto-config-layer"))
    }

    @Test
    fun `should throw when no communities in graph`() {
        graphFile.writeText("""{"other_field": "value"}""")

        val ex = assertFailsWith<IllegalArgumentException> {
            adapter.generateAllPrompts()
        }
        assertEquals(PlantumlMessages.get("graphify.no_communities"), ex.message)
    }
}