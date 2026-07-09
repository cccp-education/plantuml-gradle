package plantuml.boundary

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NonTranslatableTermRegistryTest {

    @Test
    fun `should register and detect a term`() {
        val registry = NonTranslatableTermRegistry()
        registry.register("REAC")

        assertTrue(registry.contains("REAC"))
    }

    @Test
    fun `should not detect unregistered term`() {
        val registry = NonTranslatableTermRegistry()

        assertFalse(registry.contains("REAC"))
    }

    @Test
    fun `should support bulk registration`() {
        val registry = NonTranslatableTermRegistry()
        registry.registerAll(listOf("REAC", "AFNOR", "FPA"))

        assertTrue(registry.contains("REAC"))
        assertTrue(registry.contains("AFNOR"))
        assertTrue(registry.contains("FPA"))
    }
}