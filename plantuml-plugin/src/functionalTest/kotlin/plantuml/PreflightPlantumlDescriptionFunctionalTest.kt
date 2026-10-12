package plantuml

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional tests for `preflightPlantumlDescription` (EPIC PLT-AGENT, DIA-02).
 *
 * The task is the call site of [plantuml.agent.DescriptionPreflight]; the LLM is
 * mocked with WireMock, so the full task path (enable flag → config → LlmService →
 * ChatModel → parse → verdict) is exercised without a real model. Covers disabled,
 * VALID and INVALID verdicts.
 */
class PreflightPlantumlDescriptionFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    companion object {
        private lateinit var wireMock: WireMockServer

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
            wireMock.start()
            wireMock.stubFor(
                WireMock.get(WireMock.urlEqualTo("/api/tags"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"models":[]}"""),
                    ),
            )
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            if (::wireMock.isInitialized) wireMock.stop()
        }
    }

    private fun stubChat(content: String) {
        wireMock.stubFor(
            WireMock.post(WireMock.urlEqualTo("/api/chat"))
                .willReturn(
                    WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"model":"smollm:135m","message":{"role":"assistant","content":"$content"},"done":true}""",
                        ),
                ),
        )
    }

    @BeforeEach
    fun resetStubs() {
        wireMock.resetAll()
        wireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/api/tags"))
                .willReturn(
                    WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"models":[]}"""),
                ),
        )
    }

    private fun writeProject() {
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
            langchain4j:
              model: "ollama"
              ollama:
                baseUrl: "http://localhost:${wireMock.port()}"
                modelName: "smollm:135m"
            """.trimIndent(),
        )
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments(*args)
            .withProjectDir(projectDir)

    @Test
    fun `is a no-op when preflight is disabled`() {
        writeProject()
        val result = runner("preflightPlantumlDescription", "--console=plain").build()
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":preflightPlantumlDescription")?.outcome,
        )
        assertTrue(result.output.contains("disabled"), "expected the disabled no-op log")
    }

    @Test
    fun `passes when the description is valid`() {
        writeProject()
        stubChat("VALID")
        val result = runner(
            "preflightPlantumlDescription",
            "-Pplantuml.langchain4j.preflight=true",
            "-Pplantuml.description=C4 context of the API gateway",
            "--console=plain",
        ).build()
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":preflightPlantumlDescription")?.outcome,
        )
        assertTrue(result.output.contains("description VALID"), "expected the VALID verdict log")
    }

    @Test
    fun `fails with clarifying questions when the description is incomplete`() {
        writeProject()
        stubChat("INVALID\\n1. Which components?\\n2. Which protocols?")
        val result = runner(
            "preflightPlantumlDescription",
            "-Pplantuml.langchain4j.preflight=true",
            "-Pplantuml.description=some vague idea",
            "--console=plain",
        ).buildAndFail()
        assertEquals(
            TaskOutcome.FAILED,
            result.task(":preflightPlantumlDescription")?.outcome,
        )
        assertTrue(result.output.contains("needs clarification"), "expected the clarification failure")
        assertTrue(result.output.contains("Which components"), "expected the clarifying questions")
    }
}
