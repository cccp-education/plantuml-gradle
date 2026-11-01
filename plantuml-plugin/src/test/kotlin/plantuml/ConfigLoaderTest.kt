package plantuml

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ConfigLoader environment variable resolution.
 */
class ConfigLoaderTest {
    @Test
    fun `test resolveEnvironmentVariables with existing env var`() {
        val yamlContent =
            """
            langchain4j:
              openai:
                apiKey: ${'$'}{TEST_API_KEY}
            """.trimIndent()

        val result = ConfigLoader.resolveEnvironmentVariables(yamlContent)

        val expectedValue = System.getenv("TEST_API_KEY") ?: "${'$'}{TEST_API_KEY}"
        assertTrue(result.contains(expectedValue))
    }

    @Test
    fun `test resolveEnvironmentVariables with missing env var preserves syntax`() {
        val yamlContent =
            """
            langchain4j:
              openai:
                apiKey: ${'$'}{NONEXISTENT_VAR_12345}
            """.trimIndent()

        val result = ConfigLoader.resolveEnvironmentVariables(yamlContent)

        assertTrue(result.contains("${'$'}{NONEXISTENT_VAR_12345}"))
    }

    @Test
    fun `test resolveEnvironmentVariables with multiple env vars`() {
        val yamlContent =
            """
            langchain4j:
              openai:
                apiKey: ${'$'}{VAR_ONE}
              gemini:
                apiKey: ${'$'}{VAR_TWO}
            """.trimIndent()

        val result = ConfigLoader.resolveEnvironmentVariables(yamlContent)

        val expectedOne = System.getenv("VAR_ONE") ?: "${'$'}{VAR_ONE}"
        val expectedTwo = System.getenv("VAR_TWO") ?: "${'$'}{VAR_TWO}"
        assertTrue(result.contains(expectedOne))
        assertTrue(result.contains(expectedTwo))
    }

    @Test
    fun `test resolveEnvironmentVariables with no env vars returns unchanged`() {
        val yamlContent =
            """
            langchain4j:
              maxIterations: 5
              model: ollama
            """.trimIndent()

        val result = ConfigLoader.resolveEnvironmentVariables(yamlContent)

        assertEquals(yamlContent, result)
    }

    @Test
    fun `test load with environment variable in apiKey`() {
        val tempFile = File.createTempFile("test-config", ".yml")
        tempFile.writeText(
            """
            langchain4j:
              openai:
                apiKey: ${'$'}{TEST_OPENAI_KEY}
            """.trimIndent(),
        )
        tempFile.deleteOnExit()

        val config = ConfigLoader.load(tempFile)

        val expectedKey = System.getenv("TEST_OPENAI_KEY") ?: "${'$'}{TEST_OPENAI_KEY}"
        assertEquals(expectedKey, config.langchain4j.openai.apiKey)
    }
}
