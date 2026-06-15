package plantuml

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.MissingResourceException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlantumlMessagesTest {

    @Test
    fun `should return English bundle for en code`() {
        val bundle = PlantumlMessages.forLanguage("en")
        assertNotNull(bundle)
        assertEquals("en", bundle.locale.language)
    }

    @Test
    fun `should return French bundle for fr code`() {
        val bundle = PlantumlMessages.forLanguage("fr")
        assertNotNull(bundle)
        assertEquals("fr", bundle.locale.language)
    }

    @Test
    fun `should return Chinese bundle for zh code`() {
        val bundle = PlantumlMessages.forLanguage("zh")
        assertNotNull(bundle)
        assertEquals("zh", bundle.locale.language)
    }

    @Test
    fun `should fallback to English for unsupported code`() {
        val bundle = PlantumlMessages.forLanguage("xx")
        assertNotNull(bundle)
        assertEquals("en", bundle.locale.language)
    }

    @Test
    fun `should get simple message by key`() {
        val message = PlantumlMessages.get("task.docs.group", "en")
        assertEquals("info", message)
    }

    @Test
    fun `should get message with default language en`() {
        val message = PlantumlMessages.get("task.validate.group")
        assertEquals("verify", message)
    }

    @Test
    fun `should format message with single argument`() {
        val message = PlantumlMessages.format("validate.file_not_found", "en", "/path/to/file.puml")
        assertEquals("Diagram file does not exist: /path/to/file.puml", message)
    }

    @Test
    fun `should format message with multiple arguments`() {
        val message = PlantumlMessages.format("kg.stats", "en", 42, 15, 3)
        assertEquals("Knowledge graph: 42 nodes, 15 edges, 3 communities", message)
    }

    @Test
    fun `should format message with default language`() {
        val message = PlantumlMessages.format("generate.processing", args = arrayOf(5))
        assertEquals("Processing 5 prompt files...", message)
    }

    @Test
    fun `should throw MissingResourceException for unknown key`() {
        assertFailsWith<MissingResourceException> {
            PlantumlMessages.get("nonexistent.key")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["en", "zh", "hi", "es", "fr", "ar", "bn", "pt", "ru", "ur"])
    fun `should load bundle for all supported languages`(code: String) {
        val bundle = PlantumlMessages.forLanguage(code)
        assertNotNull(bundle)
        assertTrue(bundle.containsKey("task.docs.group"))
    }

    @Test
    fun `should format with three arguments`() {
        val message = PlantumlMessages.format("diagramdocs.generated_prompt", "en", "service-layer.prompt", 8, 3)
        assertEquals("Generated prompt: service-layer.prompt (8 nodes, 3 edges)", message)
    }

    @Test
    fun `should get task description message`() {
        val message = PlantumlMessages.get("task.generate.description")
        assertEquals("Process PlantUML prompts and generate diagrams with LLM assistance", message)
    }

    @Test
    fun `should format cleanup failed message`() {
        val message = PlantumlMessages.format("generate.cleanup_failed", "en", "Permission denied")
        assertEquals("  ⚠ Cleanup failed: Permission denied", message)
    }
}
