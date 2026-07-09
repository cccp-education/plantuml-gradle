package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NonTranslatableTermRegistryLoaderTest {

    private val loader = NonTranslatableTermRegistryLoader()

    @Test
    fun `should parse single term`() {
        val yaml = "terms:\n  - REAC\n"

        val registry = loader.load(yaml)

        assertTrue(registry.contains("REAC"))
    }

    @Test
    fun `should parse multiple terms`() {
        val yaml = """
            terms:
              - REAC
              - AFNOR
              - FPA
        """.trimIndent() + "\n"

        val registry = loader.load(yaml)

        assertTrue(registry.contains("REAC"))
        assertTrue(registry.contains("AFNOR"))
        assertTrue(registry.contains("FPA"))
    }

    @Test
    fun `should return empty registry for blank yaml`() {
        val yaml = ""

        val registry = loader.load(yaml)

        assertFalse(registry.contains("REAC"))
    }
}