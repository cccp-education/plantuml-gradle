package plantuml

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional tests for `validatePlantumlCorpus` (EPIC PLT-I18N-OUTPUT,
 * PLT-OUT-1).
 *
 * The gate is exercised through the real Gradle surface: task registration,
 * corpus scan, deterministic report, blocking failure, named baseline and
 * strict opt-out. PlantUML validation is the real N0 adapter — no stub — so a
 * missing `@enduml` is a genuine syntax finding.
 */
class ValidatePlantumlCorpusFunctionalTest {
    @field:TempDir
    lateinit var projectDir: File

    private fun writeBuildFile() {
        projectDir.resolve("settings.gradle").writeText("")
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id('education.cccp.plantuml')
            }
            """.trimIndent(),
        )
    }

    private fun writeDocument(
        relativePath: String,
        diagram: String,
    ) {
        val file = projectDir.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(diagram)
    }

    private fun validDiagram(id: String): String =
        """
        = Article

        [plantuml, $id, svg]
        ----
        @startuml
        A --> B
        @enduml
        ----
        """.trimIndent()

    private fun brokenDiagram(id: String): String =
        """
        = Article

        [plantuml, $id, svg]
        ----
        @startuml
        A --> B
        ----
        """.trimIndent()

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments(*args)
            .withProjectDir(projectDir)

    @Test
    fun `validatePlantumlCorpus task is registered`() {
        writeBuildFile()

        val result = runner("tasks", "--all", "--quiet").build()

        assertTrue(
            result.output.contains("validatePlantumlCorpus"),
            "Expected validatePlantumlCorpus in the task list",
        )
    }

    @Test
    fun `a valid corpus passes and writes a deterministic report`() {
        writeBuildFile()
        writeDocument("docs/fr/article.adoc", validDiagram("diagram"))

        val result =
            runner(
                "validatePlantumlCorpus",
                "-Pplantuml.corpusDir=docs",
            ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validatePlantumlCorpus")?.outcome)
        val report = projectDir.resolve("build/plantuml/plantuml-corpus-report.txt")
        assertTrue(report.exists(), "Expected the corpus report")
        val content = report.readText()
        assertTrue(content.contains("documents: 1"))
        assertTrue(content.contains("blocks: 1"))
        assertTrue(content.contains("blocking: 0"))
        val suggested = projectDir.resolve("build/plantuml/plantuml-corpus-baseline.txt")
        assertTrue(suggested.exists(), "Expected the suggested baseline")
        assertEquals("", suggested.readText())
    }

    @Test
    fun `a broken diagram fails the gate and names article id and reason`() {
        writeBuildFile()
        writeDocument("docs/en/article.adoc", brokenDiagram("diagram-2"))

        val result =
            runner(
                "validatePlantumlCorpus",
                "-Pplantuml.corpusDir=docs",
            ).buildAndFail()

        assertTrue(result.output.contains("blocking PlantUML corpus finding"))
        assertTrue(result.output.contains("en/article.adoc"))
        assertTrue(result.output.contains("diagram-2"))
        val report = projectDir.resolve("build/plantuml/plantuml-corpus-report.txt")
        assertTrue(report.exists(), "The report must be written before failing")
        assertTrue(report.readText().contains("Missing or mismatched diagram delimiters"))
        assertTrue(report.readText().contains("[en]"))
    }

    @Test
    fun `a named baseline tolerates a known violation`() {
        writeBuildFile()
        writeDocument("docs/en/article.adoc", brokenDiagram("diagram-2"))
        projectDir.resolve("plantuml-corpus-baseline.txt").writeText(
            "en/article.adoc\tdiagram-2\tMissing or mismatched diagram delimiters\n",
        )

        val result =
            runner(
                "validatePlantumlCorpus",
                "-Pplantuml.corpusDir=docs",
            ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validatePlantumlCorpus")?.outcome)
        val report = projectDir.resolve("build/plantuml/plantuml-corpus-report.txt").readText()
        assertTrue(report.contains("baselined: 1"))
        assertTrue(report.contains("blocking: 0"))
    }

    @Test
    fun `a source plantuml listing is never validated`() {
        writeBuildFile()
        writeDocument(
            "docs/fr/article.adoc",
            """
            = Article

            [source,plantuml]
            ----
            @startuml
            A --> B
            ----
            """.trimIndent(),
        )

        val result =
            runner(
                "validatePlantumlCorpus",
                "-Pplantuml.corpusDir=docs",
            ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validatePlantumlCorpus")?.outcome)
        val report = projectDir.resolve("build/plantuml/plantuml-corpus-report.txt").readText()
        assertTrue(report.contains("blocks: 0"))
        assertTrue(report.contains("blocking: 0"))
    }

    @Test
    fun `strict false reports without failing`() {
        writeBuildFile()
        writeDocument("docs/en/article.adoc", brokenDiagram("diagram-2"))

        val result =
            runner(
                "validatePlantumlCorpus",
                "-Pplantuml.corpusDir=docs",
                "-Pplantuml.corpusStrict=false",
            ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validatePlantumlCorpus")?.outcome)
        val report = projectDir.resolve("build/plantuml/plantuml-corpus-report.txt").readText()
        assertTrue(report.contains("blocking: 1"))
    }

    @Test
    fun `the gate is up-to-date when inputs are unchanged`() {
        writeBuildFile()
        writeDocument("docs/fr/article.adoc", validDiagram("diagram"))

        runner("validatePlantumlCorpus", "-Pplantuml.corpusDir=docs").build()
        val second = runner("validatePlantumlCorpus", "-Pplantuml.corpusDir=docs").build()

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":validatePlantumlCorpus")?.outcome)
    }
}
