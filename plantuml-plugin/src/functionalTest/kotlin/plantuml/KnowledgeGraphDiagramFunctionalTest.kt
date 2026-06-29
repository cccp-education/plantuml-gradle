@file:Suppress("FunctionName")

package plantuml

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KnowledgeGraphDiagramFunctionalTest {

    companion object {
        @TempDir
        @JvmStatic
        lateinit var projectDir: File

        private lateinit var graphifyOutDir: File

        @BeforeAll
        @JvmStatic
        fun setupProject() {
            File(projectDir, "settings.gradle.kts")
                .writeText("""rootProject.name = "kg-functional-test"""")

            File(projectDir, "build.gradle.kts").writeText(
                """
                plugins { id("education.cccp.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
                """.trimIndent(),
            )

            File(projectDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  images: "test-images"
                  rag: "test-rag"
                  diagrams: "generated/diagrams"
                  validations: "generated/validations"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:11438"
                    modelName: "smollm:135m"
                """.trimIndent(),
            )

            File(projectDir, "test-prompts").mkdirs()

            graphifyOutDir = File(projectDir, "graphify-out")
            graphifyOutDir.mkdirs()
        }

        private fun runner(vararg args: String): GradleRunner =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(*args)
                .withPluginClasspath()

        private fun writeGraphifyJson(json: String) {
            File(graphifyOutDir, "graph.json").writeText(json)
        }

        private val graphifyNativeJson = """
            {
              "nodes": [
                {"id": "0", "label": "LlmService", "file_type": "class", "community": 0},
                {"id": "1", "label": "DiagramProcessor", "file_type": "class", "community": 0},
                {"id": "2", "label": "ConfigLoader", "file_type": "class", "community": 1},
                {"id": "3", "label": "ApiKeyPool", "file_type": "class", "community": 1}
              ],
              "links": [
                {"source": "0", "target": "1", "relation": "calls", "confidence_score": 0.9},
                {"source": "2", "target": "3", "relation": "creates", "confidence_score": 0.75}
              ]
            }
        """.trimIndent()

        private val graphifyWithConfidenceString = """
            {
              "nodes": [
                {"id": "0", "label": "ServiceA", "file_type": "class", "community": 0},
                {"id": "1", "label": "ServiceB", "file_type": "class", "community": 0},
                {"id": "2", "label": "ServiceC", "file_type": "class", "community": 1}
              ],
              "links": [
                {"source": "0", "target": "1", "relation": "uses", "confidence": "INFERRED", "weight": 0.6},
                {"source": "1", "target": "2", "relation": "depends", "confidence": "EXTRACTED", "weight": 1.0}
              ]
            }
        """.trimIndent()

        private val graphifyLegacyFormat = """
            {
              "communities": [
                {
                  "name": "service layer",
                  "nodes": ["LlmService", "DiagramProcessor"],
                  "edges": [
                    {"source": "LlmService", "target": "DiagramProcessor", "label": "calls", "type": "EXTRACTED", "confidence": 0.9}
                  ]
                },
                {
                  "name": "config layer",
                  "nodes": ["ConfigLoader", "ApiKeyPool"],
                  "edges": [
                    {"source": "ConfigLoader", "target": "ApiKeyPool", "label": "creates", "type": "INFERRED", "confidence": 0.7}
                  ]
                }
              ]
            }
        """.trimIndent()

        private val emptyGraphJson = """
            {
              "nodes": [],
              "links": []
            }
        """.trimIndent()

        private val corruptedGraphJson = "{{invalid json!!!"

        private fun pumlContent(suffixedName: String = "knowledge-graph-full"): String =
            File(projectDir, "diagrams/knowledge-graph/$suffixedName.puml").readText()

        private fun pumlExists(suffixedName: String = "knowledge-graph-full"): Boolean =
            File(projectDir, "diagrams/knowledge-graph/$suffixedName.puml").exists()

        private fun pngExists(suffixedName: String = "knowledge-graph-full"): Boolean =
            File(projectDir, "diagrams/knowledge-graph/$suffixedName.png").exists()
    }

    @Test
    @Order(1)
    fun `should fail when graphify-out graph json does not exist`() {
        File(graphifyOutDir, "graph.json").delete()

        val result = runner("generateKnowledgeGraphDiagram").buildAndFail()

        Assertions.assertEquals(FAILED, result.task(":generateKnowledgeGraphDiagram")?.outcome)
        assertTrue(result.output.contains("graphify-out/graph.json not found"))
    }

    @Test
    @Order(2)
    fun `should generate knowledge graph diagram from graphify native format`() {
        writeGraphifyJson(graphifyNativeJson)

        val result = runner("generateKnowledgeGraphDiagram").build()

        Assertions.assertEquals(SUCCESS, result.task(":generateKnowledgeGraphDiagram")?.outcome)

        val outputDir = File(projectDir, "diagrams/knowledge-graph")
        assertTrue(outputDir.exists(), "Output directory should exist")

        assertTrue(pumlExists(), "PUML file should be generated")
        val puml = pumlContent()
        assertTrue(puml.contains("@startuml"))
        assertTrue(puml.contains("@enduml"))
        assertTrue(puml.contains("LlmService"))
        assertTrue(puml.contains("community_0"))
    }

    @Test
    @Order(3)
    fun `should render clean node labels without toString pollution`() {
        writeGraphifyJson(graphifyNativeJson)

        runner("generateKnowledgeGraphDiagram").build()

        val puml = pumlContent()
        assertFalse(puml.contains("KnowledgeGraphNode("),
            "Node labels must be clean, not toString() like 'KnowledgeGraphNode(name=..., type=...)'")
        assertFalse(puml.contains(").name"),
            "No Kotlin interpolation artifacts like ').name' from \$node.name without braces")
        assertFalse(puml.contains("attributes=[]"),
            "No data class internals like 'attributes=[]'")
    }

    @Test
    @Order(4)
    fun `should handle confidence as string edge type in graphify native format`() {
        writeGraphifyJson(graphifyWithConfidenceString)

        val result = runner("generateKnowledgeGraphDiagram").build()

        Assertions.assertEquals(SUCCESS, result.task(":generateKnowledgeGraphDiagram")?.outcome)
        val puml = pumlContent()
        assertTrue(puml.contains("ServiceA"))
        assertTrue(puml.contains("ServiceB"))
        assertFalse(puml.contains("KnowledgeGraphNode("))
    }

    @Test
    @Order(5)
    fun `should resolve numeric IDs to labels in links`() {
        writeGraphifyJson(graphifyNativeJson)

        runner("generateKnowledgeGraphDiagram").build()

        val puml = pumlContent()
        assertFalse(puml.contains("\"0\""), "Link source/target must be resolved to labels, not numeric IDs")
        assertFalse(puml.contains("\"1\""), "Link source/target must be resolved to labels, not numeric IDs")
        assertTrue(puml.contains("LlmService"), "Resolved label LlmService must appear")
        assertTrue(puml.contains("DiagramProcessor"), "Resolved label DiagramProcessor must appear")
    }

    @Test
    @Order(6)
    fun `should apply community filter via Gradle property`() {
        writeGraphifyJson(graphifyNativeJson)

        runner("generateKnowledgeGraphDiagram", "-Pplantuml.kg.community=community_0").build()

        assertTrue(pumlExists("knowledge-graph-community_0"))
        val puml = pumlContent("knowledge-graph-community_0")
        assertTrue(puml.contains("community_0"))
        assertFalse(puml.contains("community_1"))
        assertFalse(puml.contains("KnowledgeGraphNode("))
    }

    @Test
    @Order(7)
    fun `should apply edgeTypes filter via Gradle property`() {
        writeGraphifyJson(graphifyWithConfidenceString)

        runner("generateKnowledgeGraphDiagram", "-Pplantuml.kg.edgeTypes=EXTRACTED").build()

        val puml = pumlContent()
        assertTrue(puml.contains("-->"), "EXTRACTED edges should use solid arrow")
        assertFalse(puml.contains("..>"), "INFERRED edges should be filtered out with edgeTypes=EXTRACTED")
    }

    @Test
    @Order(8)
    fun `should apply maxNodes filter via Gradle property`() {
        writeGraphifyJson(graphifyNativeJson)

        runner("generateKnowledgeGraphDiagram", "-Pplantuml.kg.maxNodes=2").build()

        assertTrue(pumlExists())
        val puml = pumlContent()
        assertFalse(puml.contains("KnowledgeGraphNode("))
    }

    @Test
    @Order(9)
    fun `should apply minConfidence filter via Gradle property`() {
        writeGraphifyJson(graphifyWithConfidenceString)

        runner("generateKnowledgeGraphDiagram", "-Pplantuml.kg.minConfidence=0.8").build()

        val puml = pumlContent()
        assertFalse(puml.contains("KnowledgeGraphNode("))
    }

    @Test
    @Order(10)
    fun `should use custom outputDir via Gradle property`() {
        writeGraphifyJson(graphifyNativeJson)

        val customDir = File(projectDir, "custom-kg-output")

        runner("generateKnowledgeGraphDiagram", "-Pplantuml.kg.outputDir=custom-kg-output").build()

        val customPuml = File(customDir, "knowledge-graph-full.puml")
        assertTrue(customPuml.exists(), "PUML should be generated in custom output dir")
        assertFalse(customPuml.readText().contains("KnowledgeGraphNode("))
    }

    @Test
    @Order(11)
    fun `should generate PNG when PUML is valid`() {
        writeGraphifyJson(graphifyNativeJson)

        runner("generateKnowledgeGraphDiagram").build()

        assertTrue(pngExists(), "PNG should be generated for valid PUML")
    }

    @Test
    @Order(12)
    fun `should handle empty graph with no nodes and no links`() {
        writeGraphifyJson(emptyGraphJson)

        val result = runner("generateKnowledgeGraphDiagram").build()

        Assertions.assertEquals(SUCCESS, result.task(":generateKnowledgeGraphDiagram")?.outcome)
        assertTrue(pumlExists())
        val puml = pumlContent()
        assertTrue(puml.contains("@startuml"))
        assertTrue(puml.contains("@enduml"))
    }

    @Test
    @Order(13)
    fun `should handle legacy format with communities array`() {
        writeGraphifyJson(graphifyLegacyFormat)

        val result = runner("generateKnowledgeGraphDiagram").build()

        Assertions.assertEquals(SUCCESS, result.task(":generateKnowledgeGraphDiagram")?.outcome)
        assertTrue(pumlExists())
        val puml = pumlContent()
        assertTrue(puml.contains("service layer"))
        assertTrue(puml.contains("LlmService"))
        assertFalse(puml.contains("KnowledgeGraphNode("))
    }

    @Test
    @Order(14)
    fun `should fail on corrupted graph json`() {
        writeGraphifyJson(corruptedGraphJson)

        val result = runner("generateKnowledgeGraphDiagram").buildAndFail()

        Assertions.assertEquals(FAILED, result.task(":generateKnowledgeGraphDiagram")?.outcome)
    }

    @Test
    @Order(15)
    fun `should produce diagram when community filter matches nothing`() {
        writeGraphifyJson(graphifyNativeJson)

        runner("generateKnowledgeGraphDiagram", "-Pplantuml.kg.community=nonexistent_community").build()

        assertTrue(pumlExists("knowledge-graph-nonexistent_community"))
    }

    @AfterEach
    fun cleanupOutput() {
        val outputDir = File(projectDir, "diagrams/knowledge-graph")
        if (outputDir.exists()) outputDir.deleteRecursively()
        val customDir = File(projectDir, "custom-kg-output")
        if (customDir.exists()) customDir.deleteRecursively()
    }
}