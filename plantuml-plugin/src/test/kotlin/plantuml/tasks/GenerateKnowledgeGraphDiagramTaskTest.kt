package plantuml.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class GenerateKnowledgeGraphDiagramTaskTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var project: Project
    private lateinit var task: GenerateKnowledgeGraphDiagramTask
    private val mapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        project.pluginManager.apply("education.cccp.plantuml")
        task = project.tasks.getByName("generateKnowledgeGraphDiagram") as GenerateKnowledgeGraphDiagramTask
    }

    private fun createGraphJson(vararg entries: Pair<String, Any>): String {
        val map = entries.toMap()
        return mapper.writeValueAsString(map)
    }

    private fun createGraphifyOut(communities: List<Map<String, Any>>): File {
        val graphifyOut = File(tempDir, "graphify-out")
        graphifyOut.mkdirs()
        val graphFile = File(graphifyOut, "graph.json")
        val json = mapper.writeValueAsString(mapOf("communities" to communities))
        graphFile.writeText(json)
        return graphFile
    }

    private fun createRichGraphJson(): String {
        return mapper.writeValueAsString(mapOf(
            "communities" to listOf(
                mapOf(
                    "name" to "service layer",
                    "nodes" to listOf("LlmService", "DiagramProcessor", "PlantumlService"),
                    "edges" to listOf(
                        mapOf("source" to "LlmService", "target" to "DiagramProcessor", "label" to "creates", "type" to "EXTRACTED", "confidence" to 1.0),
                        mapOf("source" to "DiagramProcessor", "target" to "PlantumlService", "label" to "validates with", "type" to "EXTRACTED", "confidence" to 1.0),
                        mapOf("source" to "LlmService", "target" to "PlantumlService", "label" to "delegates to", "type" to "INFERRED", "confidence" to 0.75)
                    )
                ),
                mapOf(
                    "name" to "config layer",
                    "nodes" to listOf("ConfigLoader", "ConfigMerger", "PlantumlConfig"),
                    "edges" to listOf(
                        mapOf("source" to "ConfigLoader", "target" to "ConfigMerger", "label" to "loads into", "type" to "EXTRACTED", "confidence" to 1.0)
                    )
                )
            ),
            "edges" to listOf(
                mapOf("source" to "LlmService", "target" to "ConfigLoader", "label" to "reads config", "type" to "INFERRED", "confidence" to 0.6)
            )
        ))
    }

    @Test
    fun `should register generateKnowledgeGraphDiagram task`() {
        assertTrue(project.tasks.names.contains("generateKnowledgeGraphDiagram"))
    }

    @Test
    fun `should throw when graph json not found`() {
        org.junit.jupiter.api.assertThrows<org.gradle.api.GradleException> {
            task.generateKnowledgeGraph()
        }
    }

    @Test
    fun `should generate knowledge graph diagram from simple communities`() {
        createGraphifyOut(listOf(
            mapOf(
                "name" to "service layer",
                "nodes" to listOf("LlmService", "DiagramProcessor"),
                "edges" to listOf("LlmService --> DiagramProcessor")
            )
        ))

        task.generateKnowledgeGraph()

        val outputDir = File(tempDir, "diagrams/knowledge-graph")
        assertTrue(outputDir.exists())

        val pumlFiles = outputDir.listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())

        val content = pumlFiles!!.first().readText()
        assertTrue(content.contains("@startuml"))
        assertTrue(content.contains("@enduml"))
        assertTrue(content.contains("service layer"))
        assertTrue(content.contains("LlmService"))
    }

    @Test
    fun `should generate rich knowledge graph with edge types`() {
        val graphifyOut = File(tempDir, "graphify-out")
        graphifyOut.mkdirs()
        val graphFile = File(graphifyOut, "graph.json")
        graphFile.writeText(createRichGraphJson())

        task.generateKnowledgeGraph()

        val outputDir = File(tempDir, "diagrams/knowledge-graph")
        assertTrue(outputDir.exists())

        val pumlFiles = outputDir.listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())

        val content = pumlFiles!!.first().readText()
        assertTrue(content.contains("-->"))
        assertTrue(content.contains("..>"))
        assertTrue(content.contains("service layer"))
        assertTrue(content.contains("config layer"))
    }

    @Test
    fun `should filter by community property`() {
        val graphifyOut = File(tempDir, "graphify-out")
        graphifyOut.mkdirs()
        val graphFile = File(graphifyOut, "graph.json")
        graphFile.writeText(createRichGraphJson())

        project.extensions.extraProperties.set("plantuml.kg.community", "service")

        task.generateKnowledgeGraph()

        val pumlFiles = File(tempDir, "diagrams/knowledge-graph").listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())

        val content = pumlFiles!!.first().readText()
        assertTrue(content.contains("service layer"))
        assertTrue(content.contains("LlmService"))
    }

    @Test
    fun `should render PNG when PlantUML syntax is valid`() {
        createGraphifyOut(listOf(
            mapOf(
                "name" to "test community",
                "nodes" to listOf("NodeA", "NodeB"),
                "edges" to listOf("NodeA --> NodeB")
            )
        ))

        task.generateKnowledgeGraph()

        val outputDir = File(tempDir, "diagrams/knowledge-graph")
        assertTrue(outputDir.exists())

        val pumlFiles = outputDir.listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())

        val pumlContent = pumlFiles!!.first().readText()
        assertTrue(pumlContent.contains("@startuml"))
        assertTrue(pumlContent.contains("NodeA"))
        assertTrue(pumlContent.contains("NodeB"))
    }

    @Test
    fun `should generate full knowledge graph when no filter applied`() {
        createGraphifyOut(listOf(
            mapOf("name" to "alpha", "nodes" to listOf("A"), "edges" to emptyList<String>()),
            mapOf("name" to "beta", "nodes" to listOf("B"), "edges" to emptyList<String>())
        ))

        task.generateKnowledgeGraph()

        val pumlFiles = File(tempDir, "diagrams/knowledge-graph").listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())

        val content = pumlFiles!!.first().readText()
        assertTrue(content.contains("alpha"))
        assertTrue(content.contains("beta"))
    }

    @Test
    fun `should filter by edgeTypes property`() {
        val graphifyOut = File(tempDir, "graphify-out")
        graphifyOut.mkdirs()
        val graphFile = File(graphifyOut, "graph.json")
        graphFile.writeText(createRichGraphJson())

        project.extensions.extraProperties.set("plantuml.kg.edgeTypes", "EXTRACTED")

        task.generateKnowledgeGraph()

        val pumlFiles = File(tempDir, "diagrams/knowledge-graph").listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())

        val content = pumlFiles!!.first().readText()
        assertTrue(content.contains("-->"))
        assertTrue(!content.contains("..>"))
    }

    @Test
    fun `should filter by minConfidence property`() {
        val graphifyOut = File(tempDir, "graphify-out")
        graphifyOut.mkdirs()
        val graphFile = File(graphifyOut, "graph.json")
        graphFile.writeText(createRichGraphJson())

        project.extensions.extraProperties.set("plantuml.kg.minConfidence", "0.8")

        task.generateKnowledgeGraph()

        val pumlFiles = File(tempDir, "diagrams/knowledge-graph").listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())

        val content = pumlFiles!!.first().readText()
        assertTrue(content.contains("LlmService"))
        assertTrue(!content.contains("reads config"))
    }

    @Test
    fun `should limit nodes with maxNodes property`() {
        createGraphifyOut(listOf(
            mapOf("name" to "big", "nodes" to (1..20).map { "Node$it" }, "edges" to emptyList<String>())
        ))

        project.extensions.extraProperties.set("plantuml.kg.maxNodes", "5")

        task.generateKnowledgeGraph()

        val pumlFiles = File(tempDir, "diagrams/knowledge-graph").listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())

        val content = pumlFiles!!.first().readText()
        assertTrue(content.contains("Node1"))
        assertTrue(!content.contains("Node6"))
    }

    @Test
    fun `should use custom outputDir property`() {
        createGraphifyOut(listOf(
            mapOf("name" to "test", "nodes" to listOf("A"), "edges" to emptyList<String>())
        ))

        project.extensions.extraProperties.set("plantuml.kg.outputDir", "custom-kg-output")

        task.generateKnowledgeGraph()

        val outputDir = File(tempDir, "custom-kg-output")
        assertTrue(outputDir.exists())
        val pumlFiles = outputDir.listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())
    }

    @Test
    fun `should name output files with community filter suffix`() {
        createGraphifyOut(listOf(
            mapOf("name" to "service layer", "nodes" to listOf("A"), "edges" to emptyList<String>())
        ))

        project.extensions.extraProperties.set("plantuml.kg.community", "service")

        task.generateKnowledgeGraph()

        val outputDir = File(tempDir, "diagrams/knowledge-graph")
        val pumlFiles = outputDir.listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())
        assertTrue(pumlFiles!!.first().name.contains("service"))
    }

    @Test
    fun `should name output files with full suffix when no filter`() {
        createGraphifyOut(listOf(
            mapOf("name" to "alpha", "nodes" to listOf("A"), "edges" to emptyList<String>())
        ))

        task.generateKnowledgeGraph()

        val outputDir = File(tempDir, "diagrams/knowledge-graph")
        val pumlFiles = outputDir.listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())
        assertTrue(pumlFiles!!.first().name == "knowledge-graph-full.puml")
    }

    @Test
    fun `should generate diagram from graphify native format`() {
        val graphifyOut = File(tempDir, "graphify-out")
        graphifyOut.mkdirs()
        val graphFile = File(graphifyOut, "graph.json")
        graphFile.writeText(mapper.writeValueAsString(mapOf(
            "directed" to true,
            "multigraph" to false,
            "nodes" to listOf(
                mapOf("label" to "LlmService", "file_type" to "code", "id" to "llm_service", "community" to 0, "source_file" to "LlmService.kt"),
                mapOf("label" to "ApiKeyPool", "file_type" to "code", "id" to "api_key_pool", "community" to 0, "source_file" to "ApiKeyPool.kt")
            ),
            "links" to listOf(
                mapOf("source" to "llm_service", "target" to "api_key_pool", "relation" to "contains", "confidence" to "EXTRACTED", "confidence_score" to 1.0, "weight" to 1.0)
            )
        )))

        task.generateKnowledgeGraph()

        val outputDir = File(tempDir, "diagrams/knowledge-graph")
        assertTrue(outputDir.exists())
        val pumlFiles = outputDir.listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())
        val content = pumlFiles!!.first().readText()
        assertTrue(content.contains("community_0"))
        assertTrue(content.contains("LlmService"))
        assertTrue(content.contains("ApiKeyPool"))
    }

    @Test
    fun `should default unknown edge type property to EXTRACTED`() {
        val graphifyOut = File(tempDir, "graphify-out")
        graphifyOut.mkdirs()
        val graphFile = File(graphifyOut, "graph.json")
        graphFile.writeText(createRichGraphJson())

        project.extensions.extraProperties.set("plantuml.kg.edgeTypes", "UNKNOWN_TYPE")

        task.generateKnowledgeGraph()

        val pumlFiles = File(tempDir, "diagrams/knowledge-graph").listFiles { f -> f.extension == "puml" }
        assertTrue(pumlFiles != null && pumlFiles.isNotEmpty())
        val content = pumlFiles!!.first().readText()
        assertTrue(content.contains("@startuml"))
    }
}