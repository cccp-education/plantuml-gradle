package plantuml

import org.gradle.testfixtures.ProjectBuilder.builder
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Tests are quick
class PlantumlPluginTest {

    @Test
    fun `should create plantuml extension`() {
        // Given
        val project = builder().build()

        // When
        project.pluginManager.apply("education.cccp.plantuml")

        // Then
        val extension = project.extensions.findByName("plantuml")
        assertNotNull(extension)
        assertTrue(extension is PlantumlPlugin.PlantumlExtension)
    }

    @Test
    fun `should apply plugin successfully`() {
        // Given
        val project = builder().build()

        // When
        project.pluginManager.apply("education.cccp.plantuml")

        // Then
        // If we get here without exception, the plugin was applied correctly
        assertTrue(project.plugins.hasPlugin("education.cccp.plantuml"))
    }
}