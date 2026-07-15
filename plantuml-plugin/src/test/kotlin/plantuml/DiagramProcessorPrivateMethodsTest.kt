package plantuml

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import plantuml.service.AttemptEntry
import plantuml.service.DiagramProcessor
import plantuml.service.PlantumlService
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for DiagramProcessor private methods.
 * Tests use reflection to access private methods.
 */
class DiagramProcessorPrivateMethodsTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var processor: DiagramProcessor
    private lateinit var plantumlService: PlantumlService
    private val logger: Logger = LoggerFactory.getLogger(DiagramProcessorPrivateMethodsTest::class.java)

    @BeforeEach
    fun setUp() {
        plantumlService = PlantumlService()
        processor = DiagramProcessor(plantumlService, null, null)
    }

    @Test
    fun `buildHistoryContext should return empty string for empty history`() {
        val emptyHistory = emptyList<AttemptEntry>()

        val result = callPrivateMethod(processor, "buildHistoryContext", listOf(emptyHistory))

        assertEquals("", result)
    }

    @Test
    fun `buildHistoryContext should format history entries correctly`() {
        val history = listOf(
            AttemptEntry("Initial prompt", "@startuml\nTest\n@enduml", 0, true),
            AttemptEntry("Correction #1", "@startuml\nFixed\n@enduml", 1, false, "Syntax error")
        )

        val result = callPrivateMethod(processor, "buildHistoryContext", listOf(history)) as String

        assertTrue(result.contains("Previous attempts:"))
        assertTrue(result.contains("Attempt #0:"))
        assertTrue(result.contains("Attempt #1:"))
    }

    @Test
    fun `generateSimulatedLlmResponse should return valid PlantUML code`() {
        val prompt = "Create a class diagram for User and System"

        val result = callPrivateMethod(processor, "generateSimulatedLlmResponse", listOf(prompt, 5)) as String

        assertTrue(result.contains("@startuml"))
        assertTrue(result.contains("@enduml"))
        assertTrue(result.contains("title Create a class diagram for User and System"))
    }

    @Test
    fun `generateSimulatedLlmResponse should handle empty prompt`() {
        val prompt = ""

        val result = callPrivateMethod(processor, "generateSimulatedLlmResponse", listOf(prompt, 5)) as String

        assertTrue(result.contains("@startuml"))
        assertTrue(result.contains("@enduml"))
        assertTrue(result.contains("title Generated Diagram"))
    }

    @Test
    fun `fixCommonPlantUmlIssues should add missing startuml tag`() {
        val codeWithoutStart = """
            class Test {
              +method()
            }
            @enduml
        """.trimIndent()

        val result = callPrivateMethod(processor, "fixCommonPlantUmlIssues", listOf(codeWithoutStart)) as String

        assertTrue(result.startsWith("@startuml"))
        assertTrue(result.contains("@enduml"))
    }

    @Test
    fun `fixCommonPlantUmlIssues should add missing enduml tag`() {
        val codeWithoutEnd = """
            @startuml
            class Test {
              +method()
            }
        """.trimIndent()

        val result = callPrivateMethod(processor, "fixCommonPlantUmlIssues", listOf(codeWithoutEnd)) as String

        assertTrue(result.startsWith("@startuml"))
        assertTrue(result.endsWith("@enduml"))
    }

    @Test
    fun `fixCommonPlantUmlIssues should add both tags when missing`() {
        val codeWithoutTags = """
            class Test {
              +method()
            }
        """.trimIndent()

        val result = callPrivateMethod(processor, "fixCommonPlantUmlIssues", listOf(codeWithoutTags)) as String

        assertTrue(result.startsWith("@startuml"))
        assertTrue(result.endsWith("@enduml"))
    }

    @Test
    fun `convertHistoryToJson should produce valid JSON structure`() {
        val history = listOf(
            AttemptEntry(
                "Initial prompt",
                "@startuml\nTest\n@enduml",
                0,
                true,
                null,
                LocalDateTime.of(2024, 1, 1, 10, 0)
            )
        )

        val result = callPrivateMethod(processor, "convertHistoryToJson", listOf(history)) as String

        assertTrue(result.contains("\"entries\""))
        assertTrue(result.contains("\"iteration\""))
        assertTrue(result.contains("Initial prompt"))
        assertTrue(result.contains("\"valid\"") || result.contains("\"isValid\""))
        assertTrue(result.contains("\"totalAttempts\""))
    }

    @Test
    fun `archiveAttemptHistory should handle exception gracefully`() {
        val history = listOf(
            AttemptEntry("Test prompt", "@startuml\nTest\n@enduml", 0, false),
            AttemptEntry("Correction", "@startuml\nFixed\n@enduml", 1, true)
        )

        val configWithInvalidPath = PlantumlConfig(
            output = OutputConfig(
                rag = "/root/invalid-path-that-will-fail/rag"
            )
        )
        val processorWithConfig = DiagramProcessor(plantumlService, null, configWithInvalidPath)

        callPrivateMethod(processorWithConfig, "archiveAttemptHistory", listOf(history, logger))

        assertTrue(true, "Method should not throw exception even with invalid path")
    }
}

/**
 * Helper function to call private methods using reflection
 */
fun callPrivateMethod(instance: Any, methodName: String, args: List<Any?>): Any? {
    val method = instance.javaClass.declaredMethods.find { it.name == methodName }
        ?: throw NoSuchMethodException("Method $methodName not found")
    method.isAccessible = true
    return if (args.isEmpty()) {
        method.invoke(instance)
    } else {
        method.invoke(instance, *args.toTypedArray())
    }
}
