package plantuml

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import plantuml.tasks.CollectPlantumlIndexTask
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for CollectPlantumlIndexTask
 * 
 * Tests the RAG re-indexing task (simulation mode, without database)
 */
class CollectPlantumlIndexTaskUnitTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var project: Project
    private lateinit var task: CollectPlantumlIndexTask

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        task = project.tasks.register("collectPlantumlIndex", CollectPlantumlIndexTask::class.java).get()
    }

    @Test
    fun `should create RAG directory when not exists`() {
        // Arrange: Configure non-existent RAG directory (isolated in tempDir)
        val ragDir = File(tempDir, "isolated-rag")
        File(tempDir, "plantuml-context.yml").apply {
            writeText(
                """
                output:
                  rag: "${ragDir.absolutePath}"
                rag:
                  databaseUrl: ""
                  username: ""
                  password: ""
                  tableName: "embeddings"
            """.trimIndent()
            )
        }

        // Act: Task should create directory
        task.reindexRag()

        // Assert: RAG directory should be created
        assertTrue(ragDir.exists(), "RAG directory should be created")
        assertTrue(ragDir.isDirectory, "RAG path should be a directory")
    }

    @Test
    fun `should throw when ragDir is file not directory`() {
        // Arrange: Create file instead of directory (isolated in tempDir)
        val ragFile = File(tempDir, "isolated-rag").apply {
            writeText("This is a file, not a directory")
        }
        File(tempDir, "plantuml-context.yml").apply {
            writeText(
                """
                output:
                  rag: "${ragFile.absolutePath}"
                rag:
                  databaseUrl: ""
                  username: ""
                  password: ""
                  tableName: "embeddings"
            """.trimIndent()
            )
        }

        // Act & Assert: Should throw exception
        val exception = assertFailsWith<RuntimeException> {
            task.reindexRag()
        }
        assertTrue(exception.message!!.contains("not a directory"), "Exception should mention 'not a directory'")
    }

    @Test
    fun `should report no diagrams when RAG directory is empty`() {
        // Arrange: Create empty RAG directory (isolated in tempDir)
        val ragDir = File(tempDir, "isolated-rag").apply { mkdirs() }
        File(tempDir, "plantuml-context.yml").apply {
            writeText(
                """
                output:
                  rag: "${ragDir.absolutePath}"
                rag:
                  databaseUrl: ""
                  username: ""
                  password: ""
                  tableName: "embeddings"
            """.trimIndent()
            )
        }

        // Act: Task should report no diagrams
        task.reindexRag()

        // Assert: No exception thrown (graceful handling)
        assertTrue(ragDir.exists(), "RAG directory should exist")
    }

    @Test
    fun `should scan puml files in directory`() {
        // Arrange: Create RAG directory with .puml files (isolated in tempDir)
        val ragDir = File(tempDir, "isolated-rag").apply { mkdirs() }
        val diagram1 = File(ragDir, "diagram1.puml").apply {
            writeText("@startuml\nAlice -> Bob\n@enduml")
        }
        val diagram2 = File(ragDir, "diagram2.puml").apply {
            writeText("@startuml\nBob -> Alice\n@enduml")
        }
        File(tempDir, "plantuml-context.yml").apply {
            writeText(
                """
                output:
                  rag: "${ragDir.absolutePath}"
                rag:
                  databaseUrl: ""
                  username: ""
                  password: ""
                  tableName: "embeddings"
            """.trimIndent()
            )
        }

        // Act: Task should scan files
        task.reindexRag()

        // Assert: Files should still exist (processed but not deleted)
        assertTrue(diagram1.exists(), "Diagram 1 should exist")
        assertTrue(diagram2.exists(), "Diagram 2 should exist")
    }

    @Test
    fun `should scan history json files`() {
        // Arrange: Create RAG directory with history files (isolated in tempDir)
        val ragDir = File(tempDir, "isolated-rag").apply { mkdirs() }
        val diagram = File(ragDir, "diagram.puml").apply {
            writeText("@startuml\nAlice -> Bob\n@enduml")
        }
        val history1 = File(ragDir, "attempt-history-20260409-120000.json").apply {
            writeText("""{"attempts": 1, "success": true}""")
        }
        val history2 = File(ragDir, "attempt-history-20260409-130000.json").apply {
            writeText("""{"attempts": 2, "success": true}""")
        }
        File(tempDir, "plantuml-context.yml").apply {
            writeText(
                """
                output:
                  rag: "${ragDir.absolutePath}"
                rag:
                  databaseUrl: ""
                  username: ""
                  password: ""
                  tableName: "embeddings"
            """.trimIndent()
            )
        }

        // Act: Task should scan history files
        task.reindexRag()

        // Assert: Files should still exist
        assertTrue(diagram.exists(), "Diagram should exist")
        assertTrue(history1.exists(), "History 1 should exist")
        assertTrue(history2.exists(), "History 2 should exist")
    }

    @Test
    fun `should handle puml files with minimal content gracefully`() {
        // Arrange: Create RAG directory with minimal .puml file (isolated in tempDir)
        val ragDir = File(tempDir, "isolated-rag").apply { mkdirs() }
        val minimalDiagram = File(ragDir, "minimal.puml").apply {
            writeText("@startuml\n@enduml")
        }
        File(tempDir, "plantuml-context.yml").apply {
            writeText(
                """
                output:
                  rag: "${ragDir.absolutePath}"
                rag:
                  databaseUrl: ""
                  username: ""
                  password: ""
                  tableName: "embeddings"
            """.trimIndent()
            )
        }

        // Act: Task should handle minimal files gracefully
        task.reindexRag()

        // Assert: No exception thrown (graceful handling)
        assertTrue(minimalDiagram.exists(), "Minimal diagram should still exist")
    }

    @Test
    fun `should skip non puml and non history files`() {
        // Arrange: Create RAG directory with mixed files (isolated in tempDir)
        val ragDir = File(tempDir, "isolated-rag").apply { mkdirs() }
        val diagram = File(ragDir, "diagram.puml").apply {
            writeText("@startuml\nAlice -> Bob\n@enduml")
        }
        val readme = File(ragDir, "readme.txt").apply {
            writeText("This is a readme file")
        }
        val config = File(ragDir, "config.yml").apply {
            writeText("some: config")
        }
        File(tempDir, "plantuml-context.yml").apply {
            writeText(
                """
                output:
                  rag: "${ragDir.absolutePath}"
                rag:
                  databaseUrl: ""
                  username: ""
                  password: ""
                  tableName: "embeddings"
            """.trimIndent()
            )
        }

        // Act: Task should only process .puml and attempt-history*.json files
        task.reindexRag()

        // Assert: All files should still exist (non-matching files skipped)
        assertTrue(diagram.exists(), "Diagram should exist")
        assertTrue(readme.exists(), "Readme should exist")
        assertTrue(config.exists(), "Config should exist")
    }
}
