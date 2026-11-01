package plantuml

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for RagIndexer.
 *
 * No GradleRunner, no network — each test runs in a few ms.
 * We test all the logic that was previously buried in the Gradle task
 * and only visible via GradleRunner text output.
 */
class RagIndexerTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var ragDir: File

    @BeforeEach
    fun setup() {
        ragDir = File(tempDir, "generated/rag")
    }

    @Test
    fun `should create directory and report it when rag dir does not exist`() {
        val result = RagIndexer(ragDir).index()

        assertTrue(ragDir.exists())
        assertEquals(0, result.diagramsFound)
        assertTrue(result.messages.any { it.contains("Created RAG directory") })
    }

    @Test
    fun `should report no diagrams when directory is empty`() {
        ragDir.mkdirs()

        val result = RagIndexer(ragDir).index()

        assertEquals(0, result.diagramsFound)
        assertTrue(result.messages.any { it.contains("No PlantUML diagrams or training data found") })
    }

    @Test
    fun `should count diagrams and histories correctly`() {
        ragDir.mkdirs()
        File(ragDir, "a.puml").writeText("@startuml\nclass A\n@enduml")
        File(ragDir, "b.puml").writeText("@startuml\nclass B\n@enduml")
        File(ragDir, "history.json").writeText("{}")

        val result = RagIndexer(ragDir).index()

        assertEquals(2, result.diagramsFound)
        assertEquals(1, result.historiesFound)
        assertTrue(
            result.messages.any {
                it.contains("Found 2 PlantUML diagrams and 1 training histories")
            },
        )
    }

    @Test
    fun `should scan subdirectories recursively`() {
        ragDir.mkdirs()
        File(ragDir, "root.puml").writeText("@startuml\nclass Root\n@enduml")
        val sub = File(ragDir, "sub").also { it.mkdirs() }
        File(sub, "deep.puml").writeText("@startuml\nclass Deep\n@enduml")

        val result = RagIndexer(ragDir).index()

        // The test finds 2 diagrams (root.puml + deep.puml)
        // The 'sub' directory is not counted as a diagram
        assertEquals(2, result.diagramsFound)
    }

    @Test
    fun `should handle empty puml files without crashing`() {
        ragDir.mkdirs()
        File(ragDir, "empty.puml").writeText("")
        File(ragDir, "valid.puml").writeText("@startuml\nclass Valid\n@enduml")

        val result = RagIndexer(ragDir).index()

        // Both files are found, indexing doesn't crash on empty
        assertEquals(2, result.diagramsFound)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 5, 50])
    fun `should handle various diagram counts`(count: Int) {
        ragDir.mkdirs()
        repeat(count) { i ->
            File(ragDir, "diagram$i.puml").writeText("@startuml\nclass C$i\n@enduml")
        }

        val result = RagIndexer(ragDir).index()

        assertEquals(count, result.diagramsFound)
    }

    @Test
    fun `should report error when ragDir is a file not a directory`() {
        // Create a file instead of a directory
        ragDir.parentFile.mkdirs()
        ragDir.createNewFile()

        val result = RagIndexer(ragDir).index()

        assertTrue(result.messages.any { it.contains("not a directory") })
        assertEquals(0, result.diagramsFound)
    }
}
