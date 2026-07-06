package plantuml

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskContainer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import plantuml.PlantumlPlugin.PlantumlExtension
import plantuml.tasks.GeneratePlantumlDiagramsTask
import plantuml.tasks.CollectPlantumlIndexTask
import plantuml.tasks.ValidatePlantumlSyntaxTask
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class PlantumlPluginUnitTest {

    private lateinit var project: Project
    private lateinit var plugin: PlantumlPlugin
    private lateinit var extensions: ExtensionContainer
    private lateinit var plugins: PluginContainer
    private lateinit var tasks: TaskContainer

    @BeforeEach
    fun setUp() {
        // Create mocks for the Project dependencies
        project = mock(Project::class.java)
        extensions = mock(ExtensionContainer::class.java)
        plugins = mock(PluginContainer::class.java)
        tasks = mock(TaskContainer::class.java)

        // Configure mock behaviours
        `when`(project.extensions).thenReturn(extensions)
        `when`(project.plugins).thenReturn(plugins)
        `when`(project.tasks).thenReturn(tasks)
    }

    @Test
    fun `should register plantuml extension when plugin is applied`() {
        // Given
        plugin = PlantumlPlugin()

        // When
        plugin.apply(project)

        // Then
        verify(extensions).create("plantuml", PlantumlExtension::class.java)
    }

    @Test
    fun `should register all required tasks when plugin is applied`() {
        // Given
        plugin = PlantumlPlugin()

        // When
        plugin.apply(project)

        // Then
        verify(tasks).register("generatePlantumlDiagrams", GeneratePlantumlDiagramsTask::class.java)
        verify(tasks).register("validatePlantumlSyntax", ValidatePlantumlSyntaxTask::class.java)
        verify(tasks).register("collectPlantumlIndex", CollectPlantumlIndexTask::class.java)
    }

    @Test
    fun `should create plantuml extension with configurable properties`() {
        // Given
        val objects = mock(ObjectFactory::class.java)
        @Suppress("UNCHECKED_CAST")
        val property = mock(Property::class.java) as Property<String>
        @Suppress("UNCHECKED_CAST")
        val listProperty = mock(ListProperty::class.java) as ListProperty<String>

        `when`(objects.property(String::class.java)).thenReturn(property)
        `when`(objects.listProperty(String::class.java)).thenReturn(listProperty)

        // When - Create the extension
        val extension = PlantumlExtension(objects)

        // Then - Verify the extension and its properties
        assertNotNull(extension)
        assertNotNull(extension.configPath)
        assertEquals(property, extension.configPath)
        assertNotNull(extension.language)
        assertEquals(property, extension.language)
        assertNotNull(extension.supportedLanguages)
        assertEquals(listProperty, extension.supportedLanguages)
    }
}