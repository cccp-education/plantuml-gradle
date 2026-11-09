package plantuml

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PLT-OUT-4 — functional proof of the **non-blocking** language measurement.
 *
 * The corpus gate must still fail only on syntax; a French residue in an English
 * variant is measured in `plantuml-corpus-language.json` (and the text report)
 * without ever failing the build. The source language is configurable and a
 * French document is never measured.
 */
class ValidatePlantumlCorpusLanguageFunctionalTest {
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
        body: String,
    ) {
        val file = projectDir.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(
            """
            = Article

            [plantuml, diagram, svg]
            ----
            @startuml
            $body
            @enduml
            ----
            """.trimIndent(),
        )
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments(*args)
            .withProjectDir(projectDir)

    private fun languageReport(): String = projectDir.resolve("build/plantuml/plantuml-corpus-language.json").readText()

    @Test
    fun `a French residue in an English variant is measured but never blocking`() {
        writeBuildFile()
        writeDocument("docs/en/article.adoc", "class \"Utilisateur\"")

        val result = runner("validatePlantumlCorpus", "-Pplantuml.corpusDir=docs").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validatePlantumlCorpus")?.outcome)
        val json = languageReport()
        assertTrue(json.contains("\"sourceLanguage\" : \"fr\""), json)
        assertTrue(json.contains("\"total\" : 1"), json)
        assertTrue(json.contains("\"document\" : \"en/article.adoc\""), json)
        val report = projectDir.resolve("build/plantuml/plantuml-corpus-report.txt").readText()
        assertTrue(report.contains("language residue (non-blocking): 1"), report)
        assertTrue(report.contains("blocking: 0"), report)
    }

    @Test
    fun `a clean English variant writes an empty language report`() {
        writeBuildFile()
        writeDocument("docs/en/article.adoc", "class \"User\"\nA -> B : Create the file")

        val result = runner("validatePlantumlCorpus", "-Pplantuml.corpusDir=docs").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validatePlantumlCorpus")?.outcome)
        assertTrue(languageReport().contains("\"total\" : 0"), languageReport())
    }

    @Test
    fun `the source language option disables the measurement for another source`() {
        writeBuildFile()
        writeDocument("docs/en/article.adoc", "class \"Utilisateur\"")

        val result =
            runner(
                "validatePlantumlCorpus",
                "-Pplantuml.corpusDir=docs",
                "-Pplantuml.corpusSourceLanguage=de",
            ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validatePlantumlCorpus")?.outcome)
        val json = languageReport()
        assertTrue(json.contains("\"sourceLanguage\" : \"de\""), json)
        assertTrue(json.contains("\"total\" : 0"), json)
    }

    @Test
    fun `a French variant is never measured`() {
        writeBuildFile()
        writeDocument("docs/fr/article.adoc", "class \"Utilisateur\"")

        val result = runner("validatePlantumlCorpus", "-Pplantuml.corpusDir=docs").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validatePlantumlCorpus")?.outcome)
        assertTrue(languageReport().contains("\"total\" : 0"), languageReport())
    }

    @Test
    fun `the language report is an output of the task`() {
        writeBuildFile()
        writeDocument("docs/en/article.adoc", "class \"Utilisateur\"")

        runner("validatePlantumlCorpus", "-Pplantuml.corpusDir=docs").build()

        assertTrue(
            projectDir.resolve("build/plantuml/plantuml-corpus-language.json").exists(),
            "Expected the non-blocking language report artifact",
        )
    }
}
