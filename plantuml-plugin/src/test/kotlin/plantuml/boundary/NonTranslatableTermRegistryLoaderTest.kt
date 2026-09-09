package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NonTranslatableTermRegistryLoaderTest {

    private val loader = NonTranslatableTermRegistryLoader()

    @Test
    fun `should parse single term`() {
        val yaml = "terms:\n  - QUALIOPI\n"

        val registry = loader.load(yaml)

        assertTrue(registry.contains("QUALIOPI"))
    }

    @Test
    fun `should parse multiple terms`() {
        val yaml = """
            terms:
              - QUALIOPI
              - ISO
              - QUALIOPI
        """.trimIndent() + "\n"

        val registry = loader.load(yaml)

        assertTrue(registry.contains("QUALIOPI"))
        assertTrue(registry.contains("ISO"))
        assertTrue(registry.contains("QUALIOPI"))
    }

    @Test
    fun `should return empty registry for blank yaml`() {
        val yaml = ""

        val registry = loader.load(yaml)

        assertFalse(registry.contains("QUALIOPI"))
    }
}