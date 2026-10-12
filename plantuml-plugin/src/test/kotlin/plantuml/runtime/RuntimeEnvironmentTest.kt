package plantuml.runtime

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeEnvironmentTest {

    private fun runtime(
        properties: Map<String, String> = emptyMap(),
        environment: Map<String, String> = emptyMap(),
    ): RuntimeEnvironment = RuntimeEnvironment.system(
        property = { properties[it] },
        environment = { environment[it] },
    )

    @Test
    fun `test mode is enabled by the plantuml_test_mode property`() {
        assertTrue(runtime(properties = mapOf("plantuml.test.mode" to "true")).isTestMode)
    }

    @Test
    fun `test mode is enabled by the TEST_ENV environment variable`() {
        assertTrue(runtime(environment = mapOf("TEST_ENV" to "true")).isTestMode)
    }

    @Test
    fun `test mode is disabled by default`() {
        assertFalse(runtime().isTestMode)
    }

    @Test
    fun `real llm test flag reflects the plantuml_real_test property`() {
        assertTrue(runtime(properties = mapOf("plantuml.real.test" to "true")).isRealLlmTest)
        assertFalse(runtime().isRealLlmTest)
    }

    @Test
    fun `disk full simulation reflects the plantuml_test_disk_full property`() {
        assertTrue(runtime(properties = mapOf("plantuml.test.disk.full" to "true")).isDiskFullSimulated)
        assertFalse(runtime().isDiskFullSimulated)
    }

    @Test
    fun `project dir prefers plugin_project_dir then user_dir then dot`() {
        assertEquals(
            "/work",
            runtime(properties = mapOf("plugin.project.dir" to "/work", "user.dir" to "/home")).projectDir,
        )
        assertEquals(
            "/home",
            runtime(properties = mapOf("user.dir" to "/home")).projectDir,
        )
        assertEquals(".", runtime().projectDir)
    }

    @Test
    fun `provider timeout is five seconds under TEST_ENV and sixty otherwise`() {
        assertEquals(5L, runtime(environment = mapOf("TEST_ENV" to "true")).providerTimeoutSeconds)
        assertEquals(60L, runtime().providerTimeoutSeconds)
    }
}
