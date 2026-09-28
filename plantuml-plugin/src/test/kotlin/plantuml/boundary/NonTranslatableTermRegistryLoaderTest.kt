package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NonTranslatableTermRegistryLoaderTest {

    private val loader = NonTranslatableTermRegistryLoader()

    @Test
    fun `should parse single term`() {
        val yaml = "terms:\n  - RFC\n"

        val registry = loader.load(yaml)

        assertTrue(registry.contains("RFC"))
    }

    @Test
    fun `should parse multiple terms`() {
        val yaml = """
            terms:
              - RFC
              - ISO
              - RFC
        """.trimIndent() + "\n"

        val registry = loader.load(yaml)

        assertTrue(registry.contains("RFC"))
        assertTrue(registry.contains("ISO"))
        assertTrue(registry.contains("RFC"))
    }

    @Test
    fun `should return empty registry for blank yaml`() {
        val yaml = ""

        val registry = loader.load(yaml)

        assertFalse(registry.contains("RFC"))
    }
}