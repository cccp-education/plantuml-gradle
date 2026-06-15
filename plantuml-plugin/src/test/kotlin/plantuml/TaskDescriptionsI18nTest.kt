package plantuml

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import plantuml.tasks.CollectPlantumlIndexTask
import plantuml.tasks.GenerateDiagramDocsTask
import plantuml.tasks.GenerateKnowledgeGraphDiagramTask
import plantuml.tasks.GeneratePlantumlDiagramsTask
import plantuml.tasks.ValidatePlantumlSyntaxTask
import java.io.File
import kotlin.test.assertEquals

class TaskDescriptionsI18nTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var project: Project

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()
        project.pluginManager.apply("education.cccp.plantuml")
    }

    @Test
    fun `should use i18n group and description for validatePlantumlSyntax`() {
        val task = project.tasks.getByName("validatePlantumlSyntax") as ValidatePlantumlSyntaxTask
        assertEquals(PlantumlMessages.get("task.validate.group"), task.group)
        assertEquals(PlantumlMessages.get("task.validate.description"), task.description)
    }

    @Test
    fun `should use i18n group and description for generatePlantumlDiagrams`() {
        val task = project.tasks.getByName("generatePlantumlDiagrams") as GeneratePlantumlDiagramsTask
        assertEquals(PlantumlMessages.get("task.generate.group"), task.group)
        assertEquals(PlantumlMessages.get("task.generate.description"), task.description)
    }

    @Test
    fun `should use i18n group and description for generateDiagramDocs`() {
        val task = project.tasks.getByName("generateDiagramDocs") as GenerateDiagramDocsTask
        assertEquals(PlantumlMessages.get("task.diagramdocs.group"), task.group)
        assertEquals(PlantumlMessages.get("task.diagramdocs.description"), task.description)
    }

    @Test
    fun `should use i18n group and description for generateKnowledgeGraphDiagram`() {
        val task = project.tasks.getByName("generateKnowledgeGraphDiagram") as GenerateKnowledgeGraphDiagramTask
        assertEquals(PlantumlMessages.get("task.kg.group"), task.group)
        assertEquals(PlantumlMessages.get("task.kg.description"), task.description)
    }

    @Test
    fun `should use i18n group and description for collectPlantumlIndex`() {
        val task = project.tasks.getByName("collectPlantumlIndex") as CollectPlantumlIndexTask
        assertEquals(PlantumlMessages.get("task.collect.group"), task.group)
        assertEquals(PlantumlMessages.get("task.collect.description"), task.description)
    }

    @Test
    fun `should use i18n group and description for docs task`() {
        val task = project.tasks.getByName("docs")
        assertEquals(PlantumlMessages.get("task.docs.group"), task.group)
        assertEquals(PlantumlMessages.get("task.docs.description"), task.description)
    }

    @ParameterizedTest
    @ValueSource(strings = ["en", "fr", "zh", "hi", "es", "ar", "bn", "pt", "ru", "ur"])
    fun `should resolve task description in all supported languages`(code: String) {
        val expectedGroup = PlantumlMessages.get("task.validate.group", code)
        val expectedDesc = PlantumlMessages.get("task.validate.description", code)
        assert(expectedGroup.isNotEmpty()) { "Group should not be empty for $code" }
        assert(expectedDesc.isNotEmpty()) { "Description should not be empty for $code" }
    }
}
