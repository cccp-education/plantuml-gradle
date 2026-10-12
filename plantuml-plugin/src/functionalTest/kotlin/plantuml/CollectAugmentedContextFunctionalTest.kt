package plantuml

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional tests for the `collectPlantumlAugmentedContext` Gradle task
 * (EPIC PLT-CONTEXT, CTX-03).
 *
 * Verifies the Gradle surface: task registration, artefact production from the
 * consumer EAGER governance files + injected RAG content, the provenance
 * artefact (decision D-6) and the UP-TO-DATE skip on an unchanged build (law of
 * economy of ink — AGENT.adoc).
 */
class CollectAugmentedContextFunctionalTest {

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

    private fun writeEagerFiles() {
        val agents = projectDir.resolve(".agents").apply { mkdirs() }
        agents.resolve("INDEX.adoc").writeText(
            """
            = INDEX — Demo consumer
            EPIC DEMO-1 : in progress
            """.trimIndent(),
        )
        projectDir.resolve("PROMPT_REPRISE.adoc").writeText(
            "Mission : generate a demo diagram.",
        )
        projectDir.resolve("AGENT.adoc").writeText(
            "Rules : English only, no commits without permission.",
        )
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments(*args)
            .withProjectDir(projectDir)

    @Test
    fun `collectPlantumlAugmentedContext task is registered`() {
        writeBuildFile()
        val result = runner("tasks", "--all", "--quiet").build()
        assertTrue(
            result.output.contains("collectPlantumlAugmentedContext"),
            "Expected task in tasks list",
        )
    }

    @Test
    fun `collectPlantumlAugmentedContext produces the augmented context artefact`() {
        writeBuildFile()
        writeEagerFiles()
        val result = runner(
            "collectPlantumlAugmentedContext",
            "-Pcontext.ragContent=similarity: 0.98 — RAG nugget about diagrams",
            "-Pcontext.graphifyContent=node: plantuml -> edge: feeds -> node: diagram",
        ).build()
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":collectPlantumlAugmentedContext")?.outcome,
        )
        assertTrue(result.output.contains("PLANTUML CONTEXT"), "Expected summary log line")

        val artefact = projectDir.resolve("build/plantuml/augmented-context.txt")
        assertTrue(artefact.exists(), "Expected augmented context artefact")
        val content = artefact.readText()
        assertTrue(content.contains("RÈGLES_EAGER"), "Expected EAGER section header")
        assertTrue(content.contains("INDEX.adoc"), "Expected EAGER governance file content")
        assertTrue(content.contains("CONTEXTE_RAG"), "Expected RAG section header")
        assertTrue(content.contains("RAG nugget about diagrams"), "Expected injected RAG content")
        assertTrue(content.contains("RELATIONS_GRAPHIFY"), "Expected Graphify section header")
    }

    @Test
    fun `collectPlantumlAugmentedContext writes the provenance artefact`() {
        writeBuildFile()
        writeEagerFiles()
        runner(
            "collectPlantumlAugmentedContext",
            "-Pcontext.ragContent=provenance rag nugget",
        ).build()

        val provenance = projectDir.resolve("build/plantuml/context-provenance.json")
        assertTrue(provenance.exists(), "Expected provenance artefact")
        val json = provenance.readText()
        assertTrue(json.contains("\"EAGER\""), "Expected EAGER channel in provenance")
        assertTrue(json.contains("rag-injected"), "Expected injected RAG source in provenance")
        assertTrue(json.contains("index.adoc", ignoreCase = true), "Expected EAGER source in provenance")
    }

    @Test
    fun `collectPlantumlAugmentedContext is up-to-date when inputs are unchanged`() {
        writeBuildFile()
        writeEagerFiles()
        runner(
            "collectPlantumlAugmentedContext",
            "-Pcontext.ragContent=unchanged",
        ).build()
        val second = runner(
            "collectPlantumlAugmentedContext",
            "-Pcontext.ragContent=unchanged",
        ).build()
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            second.task(":collectPlantumlAugmentedContext")?.outcome,
            "Second run must skip the work (economy of ink)",
        )
    }
}
