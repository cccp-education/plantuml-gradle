package plantuml

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import plantuml.tasks.GenerateDiagramDocsTask
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenerateDiagramDocsTaskTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var project: Project
    private lateinit var task: GenerateDiagramDocsTask
    private val mapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        project.pluginManager.apply("education.cccp.plantuml")
        task = project.tasks.getByName("generateDiagramDocs") as GenerateDiagramDocsTask
    }

    private fun createGraphJson(communities: List<Map<String, Any>>): String {
        val graph = mapOf("communities" to communities)
        return mapper.writeValueAsString(graph)
    }

    private fun createGraphifyOut(communities: List<Map<String, Any>>): File {
        val graphifyOut = File(tempDir, "graphify-out")
        graphifyOut.mkdirs()
        val graphFile = File(graphifyOut, "graph.json")
        graphFile.writeText(createGraphJson(communities))
        return graphFile
    }

    @Test
    fun `should register generateDiagramDocs task`() {
        assertTrue(project.tasks.names.contains("generateDiagramDocs"))
    }

    @Test
    fun `should throw when graph json not found`() {
        assertFailsWith<org.gradle.api.GradleException> {
            task.generateDocs()
        }
    }

    @Test
    fun `should generate prompt for default subgraph`() {
        val communities = listOf(
            mapOf(
                "name" to "service layer",
                "nodes" to listOf("LlmService", "DiagramProcessor"),
                "edges" to listOf("LlmService --> DiagramProcessor")
            )
        )
        createGraphifyOut(communities)

        task.generateDocs()

        val promptsDir = File(tempDir, "prompts")
        assertTrue(promptsDir.exists())
        val promptFiles = promptsDir.listFiles { f -> f.extension == "prompt" }
        assertTrue(promptFiles != null && promptFiles.isNotEmpty())
        val content = promptFiles!!.first().readText()
        assertTrue(content.contains("service layer"))
        assertTrue(content.contains("LlmService"))
    }

    @Test
    fun `should generate prompt for specific subgraph via property`() {
        val communities = listOf(
            mapOf(
                "name" to "service layer",
                "nodes" to listOf("LlmService"),
                "edges" to emptyList<String>()
            ),
            mapOf(
                "name" to "config layer",
                "nodes" to listOf("ConfigLoader", "ConfigMerger"),
                "edges" to listOf("ConfigLoader --> ConfigMerger")
            )
        )
        createGraphifyOut(communities)
        project.extensions.extraProperties.set("plantuml.diagram.subgraph", "config layer")

        task.generateDocs()

        val promptsDir = File(tempDir, "prompts")
        val promptFiles = promptsDir.listFiles { f -> f.extension == "prompt" }
        assertTrue(promptFiles != null && promptFiles.isNotEmpty())
        val content = promptFiles!!.first().readText()
        assertTrue(content.contains("config layer"))
    }

    @Test
    fun `should generate all prompts when all property is true`() {
        val communities = listOf(
            mapOf(
                "name" to "service layer",
                "nodes" to listOf("LlmService"),
                "edges" to emptyList<String>()
            ),
            mapOf(
                "name" to "config layer",
                "nodes" to listOf("ConfigLoader"),
                "edges" to emptyList<String>()
            )
        )
        createGraphifyOut(communities)
        project.extensions.extraProperties.set("plantuml.diagram.all", "true")

        task.generateDocs()

        val promptsDir = File(tempDir, "prompts")
        val promptFiles = promptsDir.listFiles { f -> f.extension == "prompt" }
        assertTrue(promptFiles != null && promptFiles.size == 2)
    }

    @Test
    fun `should register docs lifecycle task`() {
        assertTrue(project.tasks.names.contains("docs"))
        val docsTask = project.tasks.findByName("docs")
        assertNotNull(docsTask)
        assertEquals("plantuml", docsTask!!.group)
    }

    private fun <T : Any> assertNotNull(value: T?): T {
        assertTrue(value != null, "Expected non-null value")
        return value
    }
}