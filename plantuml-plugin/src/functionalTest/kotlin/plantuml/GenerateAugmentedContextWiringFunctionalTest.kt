package plantuml

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional tests for the augmented-context wiring (EPIC PLT-CONTEXT, CTX-04).
 *
 * Verifies the Gradle surface: `generatePlantumlDiagrams` depends on
 * `collectPlantumlAugmentedContext`; running generation produces the collected
 * artefact `build/plantuml/augmented-context.txt` from the consumer EAGER files.
 */
class GenerateAugmentedContextWiringFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private fun writeBuildFile() {
        projectDir.resolve("settings.gradle").writeText("")
        projectDir.resolve("build.gradle").writeText(
            """
            plugins {
                id('education.cccp.plantuml')
            }
            plantuml { configPath = "plantuml-context.yml" }
            """.trimIndent(),
        )
        projectDir.resolve("plantuml-context.yml").writeText(
            """
            input:
              prompts: "test-prompts"
            output:
              diagrams: "generated/diagrams"
              images: "generated/images"
              rag: "generated/rag"
              validations: "generated/validations"
            """.trimIndent(),
        )
    }

    private fun writeEagerFiles() {
        val agents = projectDir.resolve(".agents").apply { mkdirs() }
        agents.resolve("INDEX.adoc").writeText(
            """
            = INDEX — Demo consumer
            EPIC DEMO-1 : in progress
            """.trimIndent(),
        )
        projectDir.resolve("AGENT.adoc").writeText("Rules : only valid PlantUML syntax.")
    }

    private fun writePrompt() {
        val prompts = projectDir.resolve("test-prompts").apply { mkdirs() }
        prompts.resolve("demo.prompt").writeText("Create a simple class diagram with one class named Car")
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments(*args)
            .withProjectDir(projectDir)

    @Test
    fun `generatePlantumlDiagrams depends on the augmented context collector`() {
        writeBuildFile()
        writeEagerFiles()
        writePrompt()

        val result = runner(
            "generatePlantumlDiagrams",
            "-Dplantuml.test.mode=true",
        ).build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":collectPlantumlAugmentedContext")?.outcome,
            "the collector must run before generation",
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":generatePlantumlDiagrams")?.outcome,
        )
    }

    @Test
    fun `generation surfaces the collected augmented context artefact`() {
        writeBuildFile()
        writeEagerFiles()
        writePrompt()

        runner(
            "generatePlantumlDiagrams",
            "-Dplantuml.test.mode=true",
        ).build()

        val artefact = projectDir.resolve("build/plantuml/augmented-context.txt")
        assertTrue(artefact.exists(), "expected the augmented context artefact")
        val content = artefact.readText()
        assertTrue(content.contains("RÈGLES_EAGER"), "expected the EAGER section header")
        assertTrue(content.contains("INDEX.adoc"), "expected the EAGER governance content")
    }

    @Test
    fun `generation still succeeds without any governance files`() {
        writeBuildFile()
        writePrompt()

        val result = runner(
            "generatePlantumlDiagrams",
            "-Dplantuml.test.mode=true",
        ).build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":collectPlantumlAugmentedContext")?.outcome,
            "the tolerant collector must succeed with no EAGER files",
        )
        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePlantumlDiagrams")?.outcome)
    }
}
