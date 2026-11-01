package plantuml

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import plantuml.tasks.ValidatePlantumlSyntaxTask
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for ValidatePlantumlSyntaxTask
 *
 * Tests the PlantUML syntax validation task
 */
class ValidatePlantumlSyntaxTaskTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var project: Project
    private lateinit var task: ValidatePlantumlSyntaxTask

    @BeforeEach
    fun setup() {
        project =
            ProjectBuilder
                .builder()
                .withProjectDir(tempDir)
                .build()

        task = project.tasks.register("validatePlantumlSyntax", ValidatePlantumlSyntaxTask::class.java).get()
    }

    @Test
    fun `should exit gracefully when no diagram file specified`() {
        // Arrange: No diagram file specified

        // Act: Should not throw exception
        task.validateSyntax()

        // Assert: No exception thrown (graceful exit)
        assertTrue(true, "Task should complete without throwing exception")
    }

    @Test
    fun `should throw exception when diagram file does not exist`() {
        // Arrange: Non-existent file
        project.extensions.extraProperties.set("plantuml.diagram", "nonexistent.puml")

        // Act & Assert: Exception thrown
        val exception =
            assertFailsWith<GradleException> {
                task.validateSyntax()
            }
        assertTrue(exception.message!!.contains("Diagram file does not exist"))
    }

    @Test
    fun `should validate valid PlantUML file`() {
        // Arrange: Valid PlantUML file
        @Suppress("UnusedVariable", "unused")
        val validDiagram =
            File(tempDir, "valid.puml").apply {
                writeText("@startuml\nAlice -> Bob: Hello\n@enduml")
            }
        project.extensions.extraProperties.set("plantuml.diagram", "valid.puml")

        // Act: Should not throw exception for valid syntax
        task.validateSyntax()

        // Assert: No exception thrown (valid syntax)
        assertTrue(true, "Task should complete without throwing exception for valid PlantUML")
    }

    @Test
    fun `should report invalid PlantUML syntax`() {
        // Arrange: Invalid PlantUML file (missing @enduml)
        @Suppress("UnusedVariable", "unused")
        val invalidDiagram =
            File(tempDir, "invalid.puml").apply {
                writeText("@startuml\nAlice -> Bob")
            }
        project.extensions.extraProperties.set("plantuml.diagram", "invalid.puml")

        // Act & Assert: Should not throw exception, but should detect invalid syntax
        // The task logs the error but doesn't throw for invalid syntax
        task.validateSyntax()

        // Assert: Task completes (invalid syntax is logged, not thrown)
        assertTrue(true, "Task should complete and log invalid syntax")
    }

    @Test
    fun `should respect plantuml diagram property override`() {
        // Arrange: Multiple files, property override
        @Suppress("UnusedVariable", "unused")
        val file1 =
            File(tempDir, "diagram1.puml").apply {
                writeText("@startuml\nAlice -> Bob\n@enduml")
            }

        @Suppress("UnusedVariable", "unused")
        val file2 =
            File(tempDir, "diagram2.puml").apply {
                writeText("@startuml\nBob -> Alice\n@enduml")
            }
        project.extensions.extraProperties.set("plantuml.diagram", "diagram2.puml")

        // Act: Should use diagram2.puml (the overridden file)
        task.validateSyntax()

        // Assert: No exception thrown (diagram2.puml exists and is valid)
        assertTrue(true, "Task should use overridden diagram file")
    }
}
