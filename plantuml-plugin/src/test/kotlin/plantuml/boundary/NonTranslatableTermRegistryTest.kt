package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NonTranslatableTermRegistryTest {

    @Test
    fun `should register and detect a term`() {
        val registry = NonTranslatableTermRegistry()
        registry.register("QUALIOPI")

        assertTrue(registry.contains("QUALIOPI"))
    }

    @Test
    fun `should not detect unregistered term`() {
        val registry = NonTranslatableTermRegistry()

        assertFalse(registry.contains("QUALIOPI"))
    }

    @Test
    fun `should support bulk registration`() {
        val registry = NonTranslatableTermRegistry()
        registry.registerAll(listOf("QUALIOPI", "ISO", "NFP"))

        assertTrue(registry.contains("QUALIOPI"))
        assertTrue(registry.contains("ISO"))
        assertTrue(registry.contains("QUALIOPI"))
    }
}