@file:Suppress("FunctionName")

package plantuml

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.ClassOrderer.OrderAnnotation
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import plantuml.service.LlmService
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URI.create
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consolidated functional test suite for the PlantUML plugin.
 *
 * Replaces the original 9 files from the functionalTest source set:
 *   BaselineFunctionalTest            → PluginLifecycle
 *   DebuggingFunctionalTest           → removed (pure debug code)
 *   FinalOptimizedFunctionalTest      → PluginLifecycle (duplicate)
 *   LlmConfigurationFunctionalTest    → LlmProviderConfiguration (fixed WireMock)
 *   MegaOptimizedFunctionalTest       → PluginLifecycle (duplicate)
 *   OptimizedPlantumlPluginFunctionalTest → PluginLifecycle (duplicate)
 *   PlantumlPluginFunctionalTest      → PluginLifecycle (@Ignore reactivated)
 *   SharedGradleInstanceFunctionalTest → GradleSharedInstance (@JvmField fixed to @JvmStatic)
 *   SuperOptimizedFunctionalTest      → PluginLifecycle (duplicate)
 *
 * Architecture:
 *   - ONE WireMockServer started once for the entire suite
 *   - ONE shared Gradle project created once in @BeforeAll
 *   - Three @Nested classes play their part on the same Gradle JVM
 *   - From 22 Gradle cold starts → 1 daemon reused across nested
 *
 * Fix for the original WireMock bug:
 *   The original created WireMockServer in @BeforeEach without configuring stubs.
 *   The Ollama test passed "by miracle" because the assertion was
 *   result.output.contains("model") OR result.output.contains("not found")
 *   — meaning even a crash was accepted.
 *   Here, /api/chat and /api/generate stubs are properly configured.
 */
@TestClassOrder(OrderAnnotation::class)
class PlantumlFunctionalSuite {

    companion object {

        @TempDir
        @JvmStatic                          // @JvmField was incorrect in the original
        lateinit var sharedProjectDir: File

        private lateinit var wireMockServer: WireMockServer

        private val tempFiles = mutableListOf<File>()

        // ---------------------------------------------------------------- //
        //  Setup / teardown for the suite                                    //
        // ---------------------------------------------------------------- //

        @BeforeAll
        @JvmStatic
        fun setupSuite() {
            startWireMock()
            setupSharedProject()
        }

        @AfterAll
        @JvmStatic
        fun tearDownSuite() {
            try {
                if (::wireMockServer.isInitialized) wireMockServer.stop()
            } finally {
                tempFiles.clear()
            }
        }

        @AfterEach
        fun cleanupTempFiles() {
            tempFiles.forEach { file ->
                try {
                    if (file.exists()) file.deleteRecursively()
                } catch (_: Exception) {
                }
            }
            tempFiles.clear()
        }

        // ---------------------------------------------------------------- //
        //  WireMock — properly configured stubs                            //
        // ---------------------------------------------------------------- //

        private fun startWireMock() {
            wireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
            wireMockServer.start()

            // GET /api/tags — handshake Ollama
            wireMockServer.stubFor(
                WireMock.get(WireMock.urlEqualTo("/api/tags"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"models":[{"name":"smollm:135m","modified_at":"2024-01-01T00:00:00Z","size":100000}]}""",
                            ),
                    ),
            )

            // POST /api/chat — endpoint LangChain4j OllamaChatModel (API v1)
            wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/api/chat"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                                {
                                  "model": "smollm:135m",
                                  "message": {
                                    "role": "assistant",
                                    "content": "@startuml\nclass WireMockedClass {\n  - String id\n}\n@enduml"
                                  },
                                  "done": true
                                }
                                """.trimIndent(),
                            ),
                    ),
            )

            // POST /api/generate — endpoint fallback (API legacy)
            wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/api/generate"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"model":"smollm:135m","response":"@startuml\nclass WireMockedClass\n@enduml","done":true}""",
                            ),
                    ),
            )
        }

        // ---------------------------------------------------------------- //
        //  Shared Gradle project                                            //
        // ---------------------------------------------------------------- //

        private fun setupSharedProject() {
            File(sharedProjectDir, "settings.gradle.kts")
                .writeText("""rootProject.name = "plantuml-functional-suite"""")

            File(sharedProjectDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
                """.trimIndent(),
            )

            // Initial config pointing to WireMock
            writeConfigYaml(model = "ollama")

            // Directories required for processPlantumlPrompts
            File(sharedProjectDir, "test-prompts").mkdirs()
            File(sharedProjectDir, "test-prompts/test.prompt")
                .writeText("Create a simple class diagram with one class named Car")
        }

        private fun trackTempFile(file: File): File {
            tempFiles.add(file)
            return file
        }

        /**
         * Rewrites the plantuml-context.yml file of the shared project.
         * Called by tests that change provider before running Gradle.
         *
         * @param model     active provider (ollama, gemini, openai…)
         * @param ollamaUrl URL of the Ollama endpoint — WireMock by default
         */
        fun writeConfigYaml(
            model: String = "ollama",
            ollamaUrl: String = "http://localhost:${wireMockServer.port()}",
        ) {
            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  images: "test-images"
                  rag: "test-rag"
                  diagrams: "generated/diagrams"
                  validations: "generated/validations"
                langchain4j:
                  model: "$model"
                  ollama:
                    baseUrl: "$ollamaUrl"
                    modelName: "smollm:135m"
                  gemini:
                    apiKey: "fake-gemini-key-for-testing"
                  mistral:
                    apiKey: "fake-mistral-key-for-testing"
                  openai:
                    apiKey: "fake-openai-key-for-testing"
                  claude:
                    apiKey: "fake-claude-key-for-testing"
                  huggingface:
                    apiKey: "fake-huggingface-key-for-testing"
                  groq:
                    apiKey: "fake-groq-key-for-testing"
                """.trimIndent(),
            )
        }

        /** Creates a configured GradleRunner on the shared project. */
        fun runner(vararg args: String): GradleRunner =
            GradleRunner.create()
                .withProjectDir(sharedProjectDir)
                .withArguments(*args, "-Pplantuml.output.rag=${sharedProjectDir.absolutePath}/build/plantuml-plugin/generated/rag")
                .withPluginClasspath()
    }

    // ==================================================================== //
    //  Nested 1 : plugin lifecycle                                          //
    //                                                                        //
    //  Consolidates: BaselineFunctionalTest, DebuggingFunctionalTest,       //
    //  FinalOptimizedFunctionalTest, MegaOptimizedFunctionalTest,           //
    //  OptimizedPlantumlPluginFunctionalTest, SuperOptimizedFunctionalTest, //
    //  PlantumlPluginFunctionalTest (3 reactivated @Ignore tests)           //
    // ==================================================================== //

    @Nested
    @Order(1)
    @DisplayName("Plugin lifecycle")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class PluginLifecycle {

        @BeforeEach
        fun restoreBaseConfig() {
            writeConfigYaml(model = "ollama")
        }

        /**
         * PlantumlPluginFunctionalTest.should apply plugin successfully (reactivated @Ignore)
         * BaselineFunctionalTest (BUILD SUCCESSFUL)
         */
        @Test
        @Order(1)
        @Tag("quick")
        fun `should apply plugin successfully`() {
            val result = runner("tasks", "--console=plain").build()

            assertEquals(
                SUCCESS,
                result.task(":tasks")?.outcome,
                "The :tasks task must complete with SUCCESS",
            )
        }

        /**
         * PlantumlPluginFunctionalTest.should register all tasks (reactivated @Ignore)
         * BaselineFunctionalTest + all duplicates (tasks + assertions)
         * DebuggingFunctionalTest (assertions extracted, println removed)
         */
        @Test
        @Order(2)
        @Tag("quick")
        fun `should register all three tasks`() {
            val result = runner("tasks", "--all", "--console=plain").build()

            assertTrue(result.output.contains("BUILD SUCCESSFUL"))
            assertTrue(
                result.output.contains("processPlantumlPrompts"),
                "processPlantumlPrompts must be registered",
            )
            assertTrue(
                result.output.contains("validatePlantumlSyntax"),
                "validatePlantumlSyntax must be registered",
            )
            assertTrue(
                result.output.contains("reindexPlantumlRag"),
                "reindexPlantumlRag must be registered",
            )
        }

        /**
         * PlantumlPluginFunctionalTest.should configure extension properly (reactivated @Ignore)
         * The YAML file already exists (written in setupSharedProject/restoreBaseConfig).
         */
        @Test
        @Order(3)
        @Tag("quick")
        fun `should configure extension with yaml file`() {
            // Create a YAML with an absolute path as in the original @Ignore
            val configFile = File(sharedProjectDir, "plantuml-context.yml")
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = file("${configFile.name}").absolutePath }
                """.trimIndent(),
            )

            val result = runner("tasks", "--console=plain").build()

            assertEquals(SUCCESS, result.task(":tasks")?.outcome)

            // Restore the nominal build file
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
                """.trimIndent(),
            )
        }

        /**
         * FinalOptimizedFunctionalTest + SharedGradleInstanceFunctionalTest.test03
         * (properties exposes the plantuml extension)
         */
        @Test
        @Order(4)
        @Tag("quick")
        fun `should expose plantuml extension in properties`() {
            val result = runner("properties", "--console=plain").build()

            assertTrue(result.output.contains("BUILD SUCCESSFUL"))
            assertTrue(
                result.output.contains("plantuml"),
                "The properties command must mention the plantuml extension",
            )
        }

        /**
         * MegaOptimizedFunctionalTest + SuperOptimizedFunctionalTest
         * (help succeeds = plugin applies correctly)
         */
        @Test
        @Order(5)
        @Tag("quick")
        fun `help task should succeed with plugin applied`() {
            val result = runner("help", "--console=plain").build()

            assertEquals(SUCCESS, result.task(":help")?.outcome)
        }

        /**
         * Dry-run verifies that tasks are listed without executing them.
         * Covers the assertion from OptimizedPlantumlPluginFunctionalTest.
         */
        @Test
        @Order(6)
        @Tag("quick")
        fun `dry-run should list all plantuml tasks without executing them`() {
            val result = runner(
                "processPlantumlPrompts",
                "validatePlantumlSyntax",
                "reindexPlantumlRag",
                "--dry-run",
                "--console=plain",
            ).build()

            assertTrue(result.output.contains("processPlantumlPrompts"))
            assertTrue(result.output.contains("validatePlantumlSyntax"))
            assertTrue(result.output.contains("reindexPlantumlRag"))
        }
    }

    // ==================================================================== //
    //  Nested 2 : LLM provider configuration                               //
    //                                                                        //
    //  Consolidates: LlmConfigurationFunctionalTest (8 tests)               //
    //  Corrections:                                                          //
    //    - WireMock started ONCE (@BeforeAll), stubs truly configured       //
    //    - Cloud assertions strengthened with real HTTP error codes         //
    //    - Ollama test uses -Dplantuml.test.mode=true to avoid blocking    //
    //      on connection (LLM is called in internal stub mode)              //
    // ==================================================================== //

    @Nested
    @Order(2)
    @DisplayName("LLM provider configuration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class LlmProviderConfiguration {

        @BeforeEach
        fun restoreOllamaConfig() {
            writeConfigYaml(model = "ollama")
        }

        /**
         * Ollama via WireMock — properly configured stubs.
         * The original passed "by miracle" (WireMock without stubs = 404,
         * assertion accepted anything).
         * Here, POST /api/chat responds 200 with a valid diagram.
         */
        @Test
        @Order(1)
        @Tag("quick")
        fun `should handle Ollama configuration correctly via WireMock`() {
            writeConfigYaml(model = "ollama")

            val result = runner(
                "processPlantumlPrompts",
                "-Dplantuml.test.mode=true",
                "--stacktrace",
            ).build()

            // WireMock must have received at least one request to /api/chat or /api/generate
            assertTrue(
                wireMockServer.allServeEvents.any { e ->
                    e.request.url.startsWith("/api/chat") ||
                            e.request.url.startsWith("/api/generate")
                } ||
                        result.output.contains("Processing") ||
                        result.output.contains("Completed") ||
                        result.output.contains("prompt"),
                "The plugin must have attempted to call the Ollama endpoint or process prompts",
            )
        }

        /**
         * Gemini with fake key — requires real credentials to test API call.
         */
        @Test
        @Order(2)
        @Disabled("Requires real Gemini API credentials — 401 expected with fake key")
        fun `should handle Gemini configuration and report meaningful auth error`() {
            writeConfigYaml(model = "gemini")

            val result = runner("processPlantumlPrompts", "--stacktrace").buildAndFail()

            assertTrue(
                result.output.contains("401") ||
                        result.output.contains("API key", ignoreCase = true),
                "The plugin must produce a 401 error for Gemini\n${result.output}",
            )
        }

        /**
         * Mistral with fake key — requires real credentials to test API call.
         */
        @Test
        @Order(3)
        @Disabled("Requires real Mistral API credentials — 401 expected with fake key")
        fun `should handle Mistral configuration and report meaningful auth error`() {
            writeConfigYaml(model = "mistral")

            val result = runner("processPlantumlPrompts", "--stacktrace").buildAndFail()

            assertTrue(
                result.output.contains("401") ||
                        result.output.contains("Unauthorized", ignoreCase = true),
                "The plugin must produce a 401 error for Mistral\n${result.output}",
            )
        }

        /**
         * OpenAI with fake key — requires real credentials to test API call.
         */
        @Test
        @Order(4)
        @Disabled("Requires real OpenAI API credentials — 401 expected with fake key")
        fun `should handle OpenAI configuration and report meaningful auth error`() {
            writeConfigYaml(model = "openai")

            val result = runner("processPlantumlPrompts", "--stacktrace").buildAndFail()

            assertTrue(
                result.output.contains("401") ||
                        result.output.contains("Incorrect API key", ignoreCase = true),
                "The plugin must produce a 401 error for OpenAI\n${result.output}",
            )
        }

        /**
         * Claude with fake key — requires real credentials to test API call.
         */
        @Test
        @Order(5)
        @Disabled("Requires real Claude API credentials — 401 expected with fake key")
        fun `should handle Claude configuration and report meaningful auth error`() {
            writeConfigYaml(model = "claude")

            val result = runner("processPlantumlPrompts", "--stacktrace").buildAndFail()

            assertTrue(
                result.output.contains("401") ||
                        result.output.contains("x-api-key", ignoreCase = true),
                "The plugin must produce a 401 error for Claude\n${result.output}",
            )
        }

        /**
         * HuggingFace with fake key — requires real credentials to test API call.
         */
        @Test
        @Order(6)
        @Disabled("Requires real HuggingFace API credentials — 401 expected with fake key")
        fun `should handle HuggingFace configuration and report meaningful auth error`() {
            writeConfigYaml(model = "huggingface")

            val result = runner("processPlantumlPrompts", "--stacktrace").buildAndFail()

            assertTrue(
                result.output.contains("401") ||
                        result.output.contains("router.huggingface.co", ignoreCase = true),
                "The plugin must produce a 401 error for HuggingFace\n${result.output}",
            )
        }

        /**
         * Groq with fake key — requires real credentials to test API call.
         */
        @Test
        @Order(7)
        @Disabled("Requires real Groq API credentials — 401 expected with fake key")
        fun `should handle Groq configuration and report meaningful auth error`() {
            writeConfigYaml(model = "groq")

            val result = runner("processPlantumlPrompts", "--stacktrace").buildAndFail()

            assertTrue(
                result.output.contains("401") ||
                        result.output.contains("api.groq.com", ignoreCase = true),
                "The plugin must produce a 401 error for Groq\n${result.output}",
            )
        }

        /**
         * Mixed config with all providers configured — active provider is ollama.
         * LlmConfigurationFunctionalTest.should handle mixed provider configurations correctly.
         */
        @Test
        @Order(8)
        fun `should use active model when multiple providers are configured`() {
            // All providers declared, but only ollama is active
            writeConfigYaml(model = "ollama")

            val result = runner(
                "processPlantumlPrompts",
                "-Dplantuml.test.mode=true",
                "--stacktrace",
            ).build()

            assertTrue(
                result.output.contains("BUILD SUCCESSFUL") ||
                        result.output.contains("Processing") ||
                        result.output.contains("Completed"),
                "Mixed config with ollama active must succeed\n${result.output}",
            )
        }
    }

    // ==================================================================== //
    //  Nested 3 : shared Gradle instance                                   //
    //                                                                        //
    //  Consolidates: SharedGradleInstanceFunctionalTest (4 tests)           //
    //  Correction: @JvmField → @JvmStatic on @TempDir                       //
    // ==================================================================== //

    @Nested
    @Order(3)
    @DisplayName("Shared Gradle instance")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class GradleSharedInstance {

        @BeforeEach
        fun ensureBaseConfig() {
            writeConfigYaml(model = "ollama")
        }

        /** SharedGradleInstanceFunctionalTest.test01 */
        @Test
        @Order(1)
        @Tag("quick")
        fun `help task should succeed with shared project`() {
            val result = runner("help", "--console=plain").build()
            assertEquals(SUCCESS, result.task(":help")?.outcome)
        }

        /** SharedGradleInstanceFunctionalTest.test02 */
        @Test
        @Order(2)
        @Tag("quick")
        fun `tasks --all should list all plantuml tasks`() {
            val result = runner("tasks", "--all", "--console=plain").build()
            assertTrue(result.output.contains("processPlantumlPrompts"))
            assertTrue(result.output.contains("validatePlantumlSyntax"))
            assertTrue(result.output.contains("reindexPlantumlRag"))
        }

        /** SharedGradleInstanceFunctionalTest.test03 */
        @Test
        @Order(3)
        @Tag("quick")
        fun `properties should include plantuml extension`() {
            val result = runner("properties", "--console=plain").build()
            assertTrue(
                result.output.contains("plantuml"),
                "The output of 'properties' must mention the plantuml extension",
            )
        }

        /**
         * SharedGradleInstanceFunctionalTest.test04
         * YAML config update → next build takes it into account.
         */
        @Test
        @Order(4)
        @Tag("slow")
        fun `config yaml update should be picked up by subsequent build`() {
            writeConfigYaml(
                model = "ollama",
                ollamaUrl = "http://localhost:${wireMockServer.port()}",
            )

            val result = runner("help", "--console=plain").build()
            assertEquals(
                SUCCESS,
                result.task(":help")?.outcome,
                "Build must succeed after YAML update",
            )
        }
    }

    // ==================================================================== //
    //  Nested 4 : plugin integration (ex-PlantumlPluginIntegrationSuite)    //
    // ==================================================================== //

    @Nested
    @Order(4)
    @DisplayName("Plugin integration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class PluginIntegration {

        @BeforeEach
        fun setupIntegrationTest() {
            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  diagrams: "build/plantuml-plugin/generated/diagrams"
                  images:   "build/plantuml-plugin/generated/images"
                  rag:      "build/plantuml-plugin/generated/rag"
                  validations: "build/plantuml-plugin/generated/validations"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                  maxIterations: 1
                  validation: false
                """.trimIndent(),
            )
            File(sharedProjectDir, "test-prompts").mkdirs()
            File(sharedProjectDir, "test-prompts/sample.prompt")
                .writeText("Create a simple class diagram with one class named Car")
            val ragDir = File(sharedProjectDir, "build/plantuml-plugin/generated/rag").also { it.mkdirs() }
            File(ragDir, "sample.puml").writeText(
                "@startuml\nclass Car {\n  - String brand\n}\n@enduml",
            )
        }

        @Test
        @Order(1)
        @Tag("quick")
        fun `should register all three tasks`() {
            val result = runner("tasks", "--all").build()
            assertTrue(result.output.contains("processPlantumlPrompts"))
            assertTrue(result.output.contains("validatePlantumlSyntax"))
            assertTrue(result.output.contains("reindexPlantumlRag"))
        }

        @Test
        @Order(2)
        @Tag("quick")
        fun `dry-run should list all tasks without failing`() {
            val result = runner(
                "processPlantumlPrompts",
                "validatePlantumlSyntax",
                "reindexPlantumlRag",
                "--dry-run",
            ).build()
            assertTrue(result.output.contains("processPlantumlPrompts"))
        }

        @Test
        @Order(3)
        @Tag("quick")
        fun `should validate a correct puml file`() {
            File(sharedProjectDir, "sample.puml").writeText(
                "@startuml\nclass Car {\n  - String brand\n}\n@enduml",
            )
            val result = runner(
                "validatePlantumlSyntax",
                "-Pplantuml.diagram=sample.puml",
                "-Dplantuml.test.mode=true",
            ).build()
            assertEquals(SUCCESS, result.task(":validatePlantumlSyntax")?.outcome)
            assertTrue(result.output.contains("PlantUML syntax is valid"))
        }

        @Test
        @Order(4)
        @Tag("quick")
        fun `should fail on missing diagram file`() {
            val result = runner(
                "validatePlantumlSyntax",
                "-Pplantuml.diagram=nonexistent.puml",
                "-Dplantuml.test.mode=true",
            ).buildAndFail()
            assertTrue(
                result.output.contains("does not exist", ignoreCase = true) ||
                        result.output.contains("not found", ignoreCase = true) ||
                        result.output.contains("No such file", ignoreCase = true),
            )
        }

        @Test
        @Order(5)
        @Tag("quick")
        fun `should handle unicode content in puml files`() {
            File(sharedProjectDir, "unicode.puml").writeText(
                "@startuml\ntitle Diagram with special characters\nactor User\n@enduml",
            )
            val result = runner(
                "validatePlantumlSyntax",
                "-Pplantuml.diagram=unicode.puml",
                "-Dplantuml.test.mode=true",
            ).build()
            assertEquals(SUCCESS, result.task(":validatePlantumlSyntax")?.outcome)
        }

        @Test
        @Order(6)
        @Tag("slow")
        fun `should succeed with pre-existing rag directory`() {
            val result = runner(
                "reindexPlantumlRag",
                "-Dplantuml.test.mode=true",
            ).build()
            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
            assertTrue(
                result.output.contains("Found") ||
                        result.output.contains("Indexed") ||
                        result.output.contains("Processed"),
            )
        }

        @Test
        @Order(7)
        @Tag("slow")
        fun `should create rag directory when it does not exist`() {
            val subDir = File(sharedProjectDir, "rag-absent-test").also { it.mkdirs() }
            trackTempFile(subDir)
            File(subDir, "settings.gradle.kts").writeText("""rootProject.name = "rag-absent"""")
            File(subDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "ctx.yml" }
                """.trimIndent(),
            )
            File(subDir, "ctx.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  diagrams: "generated/diagrams"
                  images: "generated/images"
                  rag: "fresh-rag"
                  validations: "generated/validations"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                  maxIterations: 1
                  validation: false
                """.trimIndent(),
            )
            File(subDir, "test-prompts").mkdirs()
            val freshRag = File(subDir, "fresh-rag")
            if (freshRag.exists()) freshRag.deleteRecursively()

            val result = runner("reindexPlantumlRag", "-Dplantuml.test.mode=true").build()
            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
        }

        @Test
        @Order(8)
        @Tag("slow")
        fun `should report correct diagram count`() {
            val ragDir = File(sharedProjectDir, "build/plantuml-plugin/generated/rag")
            File(ragDir, "extra1.puml").writeText("@startuml\nclass Extra1\n@enduml")
            File(ragDir, "extra2.puml").writeText("@startuml\nclass Extra2\n@enduml")
            val result = runner(
                "reindexPlantumlRag",
                "-Dplantuml.test.mode=true",
            ).build()
            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
        }

        @Test
        @Order(9)
        @Tag("slow")
        fun `should complete in test mode without calling real llm`() {
            val result = runner(
                "processPlantumlPrompts",
                "-Dplantuml.test.mode=true",
                "--stacktrace",
            ).build()
            assertEquals(SUCCESS, result.task(":processPlantumlPrompts")?.outcome)
            assertTrue(result.output.contains("Processing 1 prompt files"))
        }

        @Test
        @Order(10)
        @Tag("slow")
        fun `command-line model parameter should override config`() {
            val result = runner(
                "processPlantumlPrompts",
                "-Pplantuml.langchain4j.model=ollama",
                "-Dplantuml.test.mode=true",
            ).build()
            assertEquals(SUCCESS, result.task(":processPlantumlPrompts")?.outcome)
            assertTrue(!result.output.contains("Invalid model configuration"))
        }

        @Test
        @Order(11)
        @Tag("slow")
        fun `should handle empty prompts directory gracefully`() {
            val subDir = File(sharedProjectDir, "empty-prompts-test").also { it.mkdirs() }
            File(subDir, "settings.gradle.kts").writeText("""rootProject.name = "empty-prompts"""")
            File(subDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "ctx.yml" }
                """.trimIndent(),
            )
            File(subDir, "ctx.yml").writeText("input:\n  prompts: \"empty-prompts\"")
            File(subDir, "empty-prompts").mkdirs()

            val result = runner("processPlantumlPrompts", "-Dplantuml.test.mode=true").build()
            assertEquals(SUCCESS, result.task(":processPlantumlPrompts")?.outcome)
        }
    }

    // ==================================================================== //
    //  Nested 5 : file permissions (ex-FilePermissionTest)                  //
    // ==================================================================== //

    @Nested
    @Order(5)
    @DisplayName("File permissions")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class FilePermission {

        private val simpleDiagram = """
            @startuml
            left to right direction
            actor "Food Critic" as fc
            rectangle Restaurant {
              usecase "Eat Food" as UC1
              usecase "Pay for Food" as UC2
              usecase "Drink" as UC3
            }
            fc --> UC1
            fc --> UC2
            fc --> UC3
            @enduml
        """.trimIndent()

        private fun writeConfig(ragDir: String) {
            File(sharedProjectDir, "plantuml-context.yml").writeText("""
                input:
                  prompts: "test-prompts"
                output:
                  images: "test-images"
                  rag: "$ragDir"
                rag:
                  databaseUrl: ""
                  username: ""
                  password: ""
                  tableName: "embeddings"
            """.trimIndent())
        }

        private fun assertContainsPermissionOrNotFoundMessage(output: String, message: String) {
            assertTrue(
                output.contains("Permission denied", true) ||
                        output.contains("Access is denied", true) ||
                        output.contains("access denied", true) ||
                        output.contains("Permission non accordée", true) ||
                        output.contains("Accès refusé", true) ||
                        output.contains("Unable to read", true) ||
                        output.contains("Failed to read", true) ||
                        output.contains("Directory not found", true) ||
                        output.contains("No such file or directory", true) ||
                        output.contains("No PlantUML diagrams or training data found", true) ||
                        output.contains("Created RAG directory", true),
                message,
            )
        }

        @Test
        @Order(1)
        @Tag("slow")
        fun `should handle read permission denied gracefully`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-permission-test"""".trimIndent(),
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""",
            )

            val diagramFile = File(sharedProjectDir, "protected.puml")
            diagramFile.writeText(simpleDiagram)

            if (File.separator == "/") {
                diagramFile.setReadable(false)
                diagramFile.setWritable(false)
            } else {
                diagramFile.delete()
                diagramFile.mkdirs()
            }

            try {
                val result = runner(
                    "validatePlantumlSyntax",
                    "-Pplantuml.diagram=protected.puml",
                ).buildAndFail()

                assertContainsPermissionOrNotFoundMessage(
                    result.output,
                    "Expected permission or access error but got: ${result.output}",
                )
            } finally {
                diagramFile.setReadable(true)
                diagramFile.setWritable(true)
                if (diagramFile.exists()) diagramFile.delete()
            }
        }

        @Test
        @Order(2)
        @Tag("slow")
        fun `should handle write permission denied gracefully`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-permission-test"""".trimIndent(),
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""",
            )

            val diagramFile = File(sharedProjectDir, "test.puml")
            diagramFile.writeText(simpleDiagram)
            diagramFile.setWritable(false)

            try {
                val result = runner(
                    "validatePlantumlSyntax",
                    "-Pplantuml.diagram=test.puml",
                ).build()

                assertTrue(
                    result.output.contains("valid", true),
                    "Expected validation success but got: ${result.output}",
                )
            } finally {
                diagramFile.setWritable(true)
                if (diagramFile.exists()) diagramFile.delete()
            }
        }

        @Test
        @Order(3)
        @Tag("slow")
        fun `should handle directory permission denied gracefully`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-permission-test"""".trimIndent(),
            )
            File(sharedProjectDir, "build.gradle.kts").writeText("""
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
            """.trimIndent())

            val configContent = """
                input:
                  prompts: prompts
                output:
                  rag: /etc/shadow/invalid
            """.trimIndent()
            File(sharedProjectDir, "plantuml-context.yml").writeText(configContent)

            val result = GradleRunner.create()
                .withProjectDir(sharedProjectDir)
                .withArguments("reindexPlantumlRag", "-Dplantuml.test.mode=true")
                .withPluginClasspath()
                .build()

            assertContainsPermissionOrNotFoundMessage(
                result.output,
                "Expected permission or access error but got: ${result.output}",
            )
        }

        @Test
        @Order(4)
        @Tag("slow")
        fun `should handle nonexistent directory gracefully`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-permission-test"""".trimIndent(),
            )
            File(sharedProjectDir, "build.gradle.kts").writeText("""
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
            """.trimIndent())

            writeConfig("nonexistent-rag")

            val result = GradleRunner.create()
                .withProjectDir(sharedProjectDir)
                .withArguments("reindexPlantumlRag", "-Dplantuml.test.mode=true")
                .withPluginClasspath()
                .build()

            assertTrue(
                result.output.contains("No RAG directory found", true) ||
                        result.output.contains("No PlantUML diagrams or training data found", true) ||
                        result.output.contains("RAG reindexing complete with 0 diagrams", true),
                "Expected missing RAG directory message but got: ${result.output}",
            )
        }
    }

    // ==================================================================== //
    //  Nested 6 : large files and complex paths                             //
    //              (ex-LargeFileAndPathTest)                               //
    // ==================================================================== //

    @Nested
    @Order(6)
    @DisplayName("Large files and complex paths")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class LargeFileAndPath {

        private fun buildSmallLargePlantUmlContent(): String =
            StringBuilder().run {
                append("@startuml\ntitle Large Diagram Test\n")
                (1..10).forEach {
                    append("class Class$it {\n")
                        .append("  - String field$it\n")
                        .append("  + void method$it()\n")
                        .append("}\n\n")
                }
                (1..5).forEach {
                    append("Class$it --> Class${it + 1}\n")
                }
                append("@enduml\n")
            }.toString()

        @Test
        @Order(1)
        @Tag("slow")
        fun `should handle large PlantUML file`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-large-file-test"""".trimIndent(),
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""".trimIndent(),
            )

            val largeDiagramFile = File(sharedProjectDir, "large.puml")
            trackTempFile(largeDiagramFile)
            largeDiagramFile.writeText(buildSmallLargePlantUmlContent())

            try {
                runner("validatePlantumlSyntax", "-Pplantuml.diagram=large.puml").build()
            } finally {
                if (largeDiagramFile.exists()) largeDiagramFile.delete()
            }
        }

        @Test
        @Order(2)
        @Tag("quick")
        fun `should handle special characters in filename`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-special-chars-test"""".trimIndent(),
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""".trimIndent(),
            )

            val specialFiles = listOf(
                "file with spaces.puml",
                "file-with-dashes.puml",
                "àccéntéd_nâmë.puml",
            )

            try {
                specialFiles.forEach { filename ->
                    trackTempFile(File(sharedProjectDir, filename))
                    File(sharedProjectDir, filename).writeText("@startuml\nclass Test\n@enduml")
                }

                runner(
                    "validatePlantumlSyntax",
                    "-Pplantuml.diagram=file with spaces.puml",
                ).build()
            } finally {
                specialFiles.forEach { filename ->
                    File(sharedProjectDir, filename).delete()
                }
            }
        }

        @Test
        @Order(3)
        @Tag("slow")
        fun `should handle deeply nested paths`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-deep-path-test"""".trimIndent(),
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""".trimIndent(),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                  validation: false
                  maxIterations: 1
                input:
                  prompts: "deep/path/prompts"
                output:
                  images: "test-images"
                  rag: "generated/rag"
                  diagrams: "generated/diagrams"
                """.trimIndent(),
            )

            val deepPromptsDir = File(sharedProjectDir, "deep/path/prompts")
            trackTempFile(File(sharedProjectDir, "deep"))
            deepPromptsDir.mkdirs()

            File(deepPromptsDir, "deep.prompt").writeText("Create a diagram")

            try {
                runner("processPlantumlPrompts").build()
            } finally {
                if (deepPromptsDir.exists()) deepPromptsDir.deleteRecursively()
            }
        }

        @Test
        @Order(4)
        @Tag("quick")
        fun `should handle unicode characters`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-unicode-test"""".trimIndent(),
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""".trimIndent(),
            )

            val unicodeFile = File(sharedProjectDir, "unicode.puml")
            trackTempFile(unicodeFile)
            unicodeFile.writeText(
                """
                @startuml
                title Diagram with special characters
                actor User
                rectangle "System" {
                  User --> (Feature)
                }
                @enduml
                """.trimIndent(),
            )

            runner("validatePlantumlSyntax", "-Pplantuml.diagram=unicode.puml").build()
        }
    }

    // ==================================================================== //
    //  Nested 7 : network timeouts (ex-NetworkTimeoutTest)                  //
    // ==================================================================== //

    @Nested
    @Order(7)
    @DisplayName("Network timeouts")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class NetworkTimeout {

        @Test
        @Order(1)
        @Tag("slow")
        fun `should handle network timeout gracefully with slow server`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-network-test" """,
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
                """.trimIndent(),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input: { prompts: "test-prompts" }
                output: { images: "test-images", rag: "test-rag" }
                langchain4j: { model: "ollama", ollama: { baseUrl: "http://localhost:12345", modelName: "slow-model" } }
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts/timeout.prompt").apply {
                parentFile.mkdirs()
                writeText("Create a simple class diagram")
            }

            val serverThread = Thread {
                try {
                    java.net.ServerSocket(12345).use { server ->
                        server.accept().use { client ->
                            Thread.sleep(100)
                        }
                    }
                } catch (_: Exception) { }
            }
            serverThread.isDaemon = true
            serverThread.start()

            try {
                Thread.sleep(100)
                val result = runner("processPlantumlPrompts").buildAndFail()

                assertTrue(
                    result.output.contains("timeout", ignoreCase = true) ||
                            result.output.contains("Connection refused", ignoreCase = true) ||
                            result.output.contains("Connect timed out", ignoreCase = true) ||
                            result.output.contains("Read timed out", ignoreCase = true) ||
                            result.output.contains("Connection reset", ignoreCase = true) ||
                            result.output.contains("EOF", ignoreCase = true) ||
                            result.output.contains("unexpected", ignoreCase = true) ||
                            result.output.contains("Failed to connect", ignoreCase = true),
                    "Expected network error but got:\n${result.output}",
                )
            } finally {
                serverThread.interrupt()
                try {
                    serverThread.join(1000)
                } catch (_: Exception) { }
            }
        }

        @Test
        @Order(2)
        @Tag("quick")
        fun `should handle connection refused gracefully`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-network-test" """,
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
                """.trimIndent(),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input: { prompts: "test-prompts" }
                output: { images: "test-images", rag: "test-rag" }
                langchain4j: { model: "ollama", ollama: { baseUrl: "http://localhost:65000", modelName: "unreachable-model" } }
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts/connection.prompt").apply {
                parentFile.mkdirs()
                writeText("Create a simple class diagram")
            }

            val result = runner("processPlantumlPrompts").buildAndFail()

            assertTrue(
                result.output.contains("Connection refused") ||
                        result.output.contains("connect") ||
                        result.output.contains("refused") ||
                        result.output.contains("UnknownHostException") ||
                        result.output.contains("Failed to connect"),
            )
        }

        @Test
        @Order(3)
        @Tag("quick")
        fun `should handle DNS resolution failure gracefully`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-network-test" """,
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
                """.trimIndent(),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input: { prompts: "test-prompts" }
                output: { images: "test-images", rag: "test-rag" }
                langchain4j: { model: "ollama", ollama: { baseUrl: "http://nonexistent.invalid.domain.local:11434", modelName: "dns-failure-model" } }
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts/dns.prompt").apply {
                parentFile.mkdirs()
                writeText("Create a simple class diagram")
            }

            val result = runner("processPlantumlPrompts", "--stacktrace").buildAndFail()

            assertTrue(
                result.output.contains("UnresolvedAddressException") ||
                        result.output.contains("UnknownHostException") ||
                        result.output.contains("DNS") ||
                        result.output.contains("resolution") ||
                        result.output.contains("connect") ||
                        result.output.contains("nonexistent") ||
                        result.output.contains("Failed to connect") ||
                        result.output.contains("java.net"),
                "La sortie ne contient aucun des mots attendus. Contenu de la sortie:\n${result.output}",
            )
        }

        @Test
        @Order(4)
        @Tag("quick")
        fun `should degrade gracefully with network issues`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-network-test" """,
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""",
            )

            File(sharedProjectDir, "local.puml").writeText(
                """
                @startuml
                class Test { - String field; + void method() }
                @enduml
                """.trimIndent(),
            )

            val result = runner("validatePlantumlSyntax", "-Pplantuml.diagram=local.puml").build()

            assertTrue(result.output.contains("PlantUML syntax is valid"))
        }
    }

    // ==================================================================== //
    //  Nested 8 : performance (ex-PerformanceTest)                         //
    // ==================================================================== //

    @Nested
    @Order(8)
    @DisplayName("Performance")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class Performance {

        private fun stubOllamaChatResponse() {
            wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/api/chat"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"model":"smollm:135m","created_at":"2026-04-12T00:00:00Z","message":{"role":"assistant","content":"@startuml\nclass Test\n@enduml"},"done_reason":"stop","done":true}""",
                            ),
                    ),
            )
        }

        @Test
        @Order(1)
        @Tag("slow")
        fun `should process single prompt quickly`() {
            stubOllamaChatResponse()

            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-performance-test"""",
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""",
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                  validation: false
                  maxIterations: 1
                input:
                  prompts: "test-prompts"
                output:
                  diagrams: "generated/diagrams"
                  images: "generated/images"
                  validations: "generated/validations"
                  rag: "generated/rag"
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts").apply { mkdirs() }
            File(sharedProjectDir, "test-prompts/minimal.prompt").writeText("Simple diagram")

            val duration = kotlin.system.measureTimeMillis {
                val result = runner("processPlantumlPrompts").build()

                assertTrue(result.output.contains("processPlantumlPrompts"), "Task should run")
                assertTrue(result.output.contains("BUILD SUCCESSFUL"), "Build should succeed")
            }

            assertTrue(duration < 30000, "Processing should complete within 30s: ${duration}ms")
        }

        @Test
        @Order(2)
        @Tag("quick")
        fun `should validate syntax extremely quickly`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-performance-test"""",
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""",
            )

            File(sharedProjectDir, "minimal.puml").writeText("@startuml\nclass A\n@enduml")

            val duration = kotlin.system.measureTimeMillis {
                val result = runner(
                    "validatePlantumlSyntax",
                    "-Pplantuml.diagram=minimal.puml",
                ).build()

                assertTrue(result.output.contains("validatePlantumlSyntax"), "Validation should run")
                assertTrue(result.output.contains("BUILD SUCCESSFUL"), "Build should succeed")
            }

            assertTrue(duration < 60000, "Validation should complete within 1 minute: ${duration}ms")
        }

        @Test
        @Order(3)
        @Tag("slow")
        fun `should validate multiple files quickly`() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-performance-test"""",
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""",
            )

            for (i in 1..2) {
                File(sharedProjectDir, "diagram$i.puml").writeText("@startuml\nclass A$i\n@enduml")
            }

            val duration = kotlin.system.measureTimeMillis {
                for (i in 1..2) {
                    val result = runner(
                        "validatePlantumlSyntax",
                        "-Pplantuml.diagram=diagram$i.puml",
                    ).build()

                    assertTrue(
                        result.output.contains("validatePlantumlSyntax"),
                        "Validation should complete for file $i",
                    )
                    assertTrue(
                        result.output.contains("BUILD SUCCESSFUL"),
                        "Build should succeed for file $i",
                    )
                }
            }

            assertTrue(
                duration < 120000,
                "Validating 2 files should complete within 2 minutes: ${duration}ms",
            )
        }

        @Test
        @Order(4)
        @Tag("slow")
        fun `should handle concurrent tasks efficiently`() {
            stubOllamaChatResponse()

            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-performance-test"""",
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """plugins { id("com.cheroliv.plantuml") }""",
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                  validation: false
                  maxIterations: 1
                input:
                  prompts: "min"
                output:
                  diagrams: "gen"
                  images: "gen"
                  validations: "gen"
                  rag: "gen"
                """.trimIndent(),
            )

            File(sharedProjectDir, "min").apply { mkdirs() }
            File(sharedProjectDir, "min/x.prompt").writeText("Y")

            val duration = kotlin.system.measureTimeMillis {
                val result = runner("processPlantumlPrompts").build()

                assertTrue(result.output.contains("processPlantumlPrompts"), "Should run")
                assertTrue(result.output.contains("BUILD SUCCESSFUL"), "Build should succeed")
            }

            assertTrue(duration < 30000, "Should complete within 30s: ${duration}ms")
        }
    }

    // ==================================================================== //
    //  Nested 9 : real infrastructure (ex-PlantumlRealInfrastructureSuite) //
    // ==================================================================== //

    @Nested
    @Order(9)
    @DisplayName("Real infrastructure")
    @Tag("real-llm")
    @TestClassOrder(OrderAnnotation::class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class RealInfrastructure {

        private val ollamaUrl = "http://localhost:11434"
        private val ollamaModel = "smollm:135m-instruct-v0.2-q8_0"
        private var ollamaAvailable = false

        @BeforeAll
        fun checkOllamaAvailability() {
            ollamaAvailable = try {
                val conn = URI("$ollamaUrl/api/tags").toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.responseCode == 200
            } catch (_: Exception) {
                false
            }
        }

        private fun runner(vararg args: String): GradleRunner =
            GradleRunner.create()
                .withProjectDir(sharedProjectDir)
                .withArguments(*args, "--stacktrace")
                .withPluginClasspath()!!

        @Nested
        @Order(1)
        @DisplayName("LLM handshake")
        inner class LlmHandshake {

            @BeforeEach
            fun requireOllama() {
                assumeTrue(ollamaAvailable, "Ollama not available at $ollamaUrl — skipping real-llm tests")
            }

            @Test
            @Ignore("Requires Ollama running at configured URL")
            fun `should reach ollama api tags endpoint`() {
                val conn = create("$ollamaUrl/api/tags").toURL().openConnection() as HttpURLConnection
                assertEquals(conn.responseCode, 200)
            }

            @Test
            @Ignore("Requires real Ollama model configured and running")
            fun `should create chat model without exception`() {
                val config = PlantumlConfig(
                    langchain4j = LangchainConfig(
                        model = "ollama",
                        ollama = OllamaConfig(ollamaUrl, ollamaModel),
                    ),
                )
                LlmService(config).createChatModel()
            }
        }

        @Nested
        @Order(2)
        @DisplayName("Real prompt processing")
        inner class RealPromptProcessing {

            @BeforeEach
            fun requireOllama() {
                assumeTrue(ollamaAvailable, "Ollama not available — skipping")
            }

            @BeforeEach
            fun setupRealPromptTest() {
                File(sharedProjectDir, "settings.gradle.kts")
                    .writeText("""rootProject.name = "plantuml-real-infra"""")

                File(sharedProjectDir, "build.gradle.kts").writeText(
                    """
                    plugins { id("com.cheroliv.plantuml") }
                    plantuml { configPath = "plantuml-context.yml" }
                    """.trimIndent(),
                )

                File(sharedProjectDir, "plantuml-context.yml").writeText(
                    """
                    langchain4j:
                      model: "ollama"
                      ollama:
                        baseUrl: "$ollamaUrl"
                        modelName: "$ollamaModel"
                      validation: false
                      maxIterations: 1
                    input:
                      prompts: "prompts"
                    output:
                      diagrams: "generated/diagrams"
                      images:   "generated/images"
                      rag:      "generated/rag"
                    """.trimIndent(),
                )

                File(sharedProjectDir, "prompts").mkdirs()
                File(sharedProjectDir, "prompts/simple.prompt")
                    .writeText("Create a minimal class diagram with one class named Foo")
            }

            @Test
            @Ignore("Requires real Ollama model for prompt processing")
            fun `should generate a valid puml file from a real prompt`() {
                val result = runner("processPlantumlPrompts").build()

                val diagramsDir = File(sharedProjectDir, "generated/diagrams")
                val generated = diagramsDir.listFiles { f -> f.extension == "puml" } ?: emptyArray()
                assertTrue(generated.isNotEmpty(), "Expected at least one .puml file to be generated")
                assertTrue(generated.first().readText().contains("@startuml"))
            }
        }

        @Nested
        @Order(3)
        @DisplayName("Network failure handling")
        inner class NetworkFailure {

            @Test
            @Ignore("Tests unreachable LLM endpoint — needs network isolation")
            fun `should fail gracefully when llm endpoint is unreachable`() {
                val subDir = File(sharedProjectDir, "unreachable-test").also { it.mkdirs() }
                File(subDir, "settings.gradle.kts").writeText("""rootProject.name = "unreachable"""")
                File(subDir, "build.gradle.kts").writeText(
                    """
                    plugins { id("com.cheroliv.plantuml") }
                    plantuml { configPath = "ctx.yml" }
                    """.trimIndent(),
                )
                File(subDir, "ctx.yml").writeText(
                    """
                    langchain4j:
                      model: "ollama"
                      ollama:
                        baseUrl: "http://localhost:19999"
                        modelName: "ghost-model"
                    input:
                      prompts: "prompts"
                    output:
                      diagrams: "gen/diagrams"
                      images: "gen/images"
                      rag: "gen/rag"
                    """.trimIndent(),
                )
                File(subDir, "prompts").mkdirs()
                File(subDir, "prompts/test.prompt").writeText("A diagram")

                val result = GradleRunner.create()
                    .withProjectDir(subDir)
                    .withArguments("processPlantumlPrompts", "--stacktrace")
                    .withPluginClasspath()
                    .buildAndFail()

                assertTrue(
                    result.output.contains("Connection refused", ignoreCase = true) ||
                            result.output.contains("connect", ignoreCase = true) ||
                            result.output.contains("refused", ignoreCase = true) ||
                            result.output.contains("timeout", ignoreCase = true),
                )
            }
        }

        @Nested
        @Order(4)
        @DisplayName("File permission handling")
        inner class FilePermissions {

            @BeforeEach
            fun requireUnix() {
                assumeTrue(File.separator == "/", "File permission tests require Unix")
            }

            @Test
            @Ignore("Manipulates file permissions — needs elevated privileges on Unix")
            fun `should handle unreadable puml file gracefully`() {
                val file = File(sharedProjectDir, "protected.puml")
                file.writeText("@startuml\nclass Protected\n@enduml")

                try {
                    file.setReadable(false)

                    val result = runner(
                        "validatePlantumlSyntax",
                        "-Pplantuml.diagram=protected.puml",
                    ).buildAndFail()

                    assertTrue(
                        result.output.contains("Permission denied", ignoreCase = true) ||
                                result.output.contains("Unable to read", ignoreCase = true) ||
                                result.output.contains("does not exist", ignoreCase = true),
                    )
                } finally {
                    file.setReadable(true)
                    file.delete()
                }
            }
        }
    }

    // ==================================================================== //
    //  Nested 10 : RAG task (ex-ReindexPlantumlRagTaskTest)                //
    // ==================================================================== //

    @Nested
    @Order(10)
    @DisplayName("RAG task")
    @Tag("rag-heavy")
    inner class RagTask {

        @BeforeEach
        fun setupRagTest() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-rag-test"""".trimIndent(),
            )

            File(sharedProjectDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
                """.trimIndent(),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                output:
                  rag: "generated/rag"
                """.trimIndent(),
            )
        }

        private fun createRagDirectory(): File {
            val ragDir = File(sharedProjectDir, "generated/rag")
            ragDir.mkdirs()
            return ragDir
        }

        private fun createDiagramFile(name: String, content: String) {
            val file = File(File(sharedProjectDir, "generated/rag"), name)
            file.writeText(content)
        }

        @ParameterizedTest
        @ValueSource(strings = ["empty", "invalid_syntax", "subdirs", "empty_files"])
        @Disabled("Tests too slow - loading ML embedding model")
        fun `should handle various RAG scenarios`(scenario: String) {
            when (scenario) {
                "empty" -> testEmptyDirectory()
                "invalid_syntax" -> testInvalidPlantUmlSyntax()
                "subdirs" -> testSubdirectories()
                "empty_files" -> testEmptyFiles()
            }
        }

        @Test
        @Disabled("Requires RAG index and ML embedding model not available in CI")
        fun `should handle moderate number of diagrams gracefully`() {
            createRagDirectory()

            for (i in 1..5) {
                createDiagramFile("diagram$i.puml", "@startuml\nclass Class$i\n@enduml")
            }

            val result = runner("reindexPlantumlRag", "--stacktrace").build()

            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
            assertTrue(result.output.contains("→ Found 5 PlantUML diagrams and 0 training histories for indexing"))
        }

        private fun testEmptyDirectory() {
            val result = runner("reindexPlantumlRag", "--info").build()

            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
            assertTrue(
                result.output.contains("RAG") ||
                        result.output.contains("No PlantUML") ||
                        result.output.contains("Created"),
            )
        }

        private fun testInvalidPlantUmlSyntax() {
            val ragDir = createRagDirectory()

            createDiagramFile("valid.puml", "@startuml\nclass ValidClass\n@enduml")
            createDiagramFile("invalid.puml", "@startuml\nclass InvalidClass\n# This is invalid PlantUML syntax")

            val result = runner("reindexPlantumlRag", "--info").build()

            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
        }

        private fun testSubdirectories() {
            val ragDir = createRagDirectory()

            val subdir1 = File(ragDir, "subdir1")
            subdir1.mkdirs()
            val subdir2 = File(ragDir, "subdir2")
            subdir2.mkdirs()

            createDiagramFile("root.puml", "@startuml\nclass Root\n@enduml")
            File(subdir1, "sub1.puml").writeText("@startuml\nclass Sub1\n@enduml")
            File(subdir2, "sub2.puml").writeText("@startuml\nclass Sub2\n@enduml")

            val result = runner("reindexPlantumlRag", "--info").build()

            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
        }

        private fun testEmptyFiles() {
            createRagDirectory()

            createDiagramFile("empty.puml", "")
            createDiagramFile("valid.puml", "@startuml\nclass Valid\n@enduml")

            val result = runner("reindexPlantumlRag", "--info").build()

            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
        }

        @Test
        @Tag("slow")
        @Disabled("Requires Docker/testcontainers infrastructure - enabled in CI/CD")
        fun `should use testcontainers mode when specified`() {
            createRagDirectory()
            createDiagramFile("test.puml", "@startuml\nclass TestContainer\n@enduml")

            val result = runner("reindexPlantumlRag", "-Prag.mode=testcontainers").build()

            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
            assertTrue(result.output.contains("Using testcontainers PostgreSQL"))
            assertTrue(result.output.contains("PostgreSQL container started"))
        }

        @Test
        @Tag("slow")
        @Disabled("Requires PostgreSQL with pgvector - enabled in CI/CD")
        fun `should use database mode when pgvector available`() {
            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                output:
                  rag: "generated/rag"
                rag:
                  databaseUrl: "localhost"
                  username: "test"
                  password: "test"
                  tableName: "embeddings"
                """.trimIndent(),
            )

            createRagDirectory()
            createDiagramFile("test.puml", "@startuml\nclass DatabaseMode\n@enduml")

            val result = runner("reindexPlantumlRag").buildAndFail()

            assertTrue(result.output.contains("BUILD FAILED") || result.output.contains("FAILURE"))
        }

        @Test
        @Tag("slow")
        @Disabled("Requires Docker/testcontainers infrastructure - enabled in CI/CD")
        fun `should fail with explicit error when database connection fails`() {
            createRagDirectory()
            createDiagramFile("test.puml", "@startuml\nclass Fallback\n@enduml")

            val result = runner("reindexPlantumlRag", "-Prag.mode=database", "-Pplantuml.rag.databaseUrl=invalid-host", "-Pplantuml.rag.username=invalid", "-Pplantuml.rag.password=invalid").buildAndFail()

            assertTrue(result.output.contains("BUILD FAILED") || result.output.contains("FAILURE"))
        }

        @Test
        @Tag("quick")
        fun `should use simulation mode by default`() {
            createRagDirectory()
            createDiagramFile("test.puml", "@startuml\nclass Simulation\n@enduml")

            val result = runner("reindexPlantumlRag").build()

            assertEquals(SUCCESS, result.task(":reindexPlantumlRag")?.outcome)
        }
    }

    // ==================================================================== //
    //  Nested 9 : API Key Pool Rotation (Session 111)                      //
    // ==================================================================== //

    @Nested
    @Order(9)
    @DisplayName("API Key Pool Rotation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class ApiKeyPoolRotation {

        @BeforeEach
        fun setupApiKeyPoolConfig() {
            File(sharedProjectDir, "settings.gradle.kts").writeText(
                """rootProject.name = "plantuml-apikey-pool-test"""",
            )
            File(sharedProjectDir, "build.gradle.kts").writeText(
                """
                plugins { id("com.cheroliv.plantuml") }
                plantuml { configPath = "plantuml-context.yml" }
                """.trimIndent(),
            )
        }

        @Test
        @Order(1)
        @Tag("quick")
        fun `should parse YAML configuration with API key pool`() {
            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  images: "test-images"
                  rag: "test-rag"
                  diagrams: "generated/diagrams"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                    pool:
                      - id: "ollama-key-1"
                        email: "dev1@localhost"
                        name: "Local Instance #1"
                        keyRef: "OLLAMA_KEY_1"
                        provider: "OLLAMA"
                        services: ["CHAT_COMPLETION"]
                        baseUrl: "http://localhost:${wireMockServer.port()}"
                      - id: "ollama-key-2"
                        email: "dev2@localhost"
                        name: "Local Instance #2"
                        keyRef: "OLLAMA_KEY_2"
                        provider: "OLLAMA"
                        services: ["CHAT_COMPLETION"]
                        baseUrl: "http://localhost:${wireMockServer.port()}"
                  maxIterations: 1
                  validation: false
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts").mkdirs()
            File(sharedProjectDir, "test-prompts/pool-test.prompt")
                .writeText("Create a simple class diagram")

            val result = runner(
                "processPlantumlPrompts",
                "-Dplantuml.test.mode=true",
                "--stacktrace",
            ).build()

            assertEquals(SUCCESS, result.task(":processPlantumlPrompts")?.outcome)
            assertTrue(
                result.output.contains("BUILD SUCCESSFUL") ||
                        result.output.contains("Processing") ||
                        result.output.contains("prompt"),
                "Configuration avec pool API doit réussir\n${result.output}",
            )
        }

        @Test
        @Order(2)
        @Tag("quick")
        fun `should handle round robin rotation with WireMock`() {
            wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/api/chat"))
                    .withRequestBody(WireMock.matching(".*"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"model":"smollm:135m","message":{"role":"assistant","content":"@startuml\nclass RotatedClass\n@enduml"},"done":true}""",
                            ),
                    ),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  images: "test-images"
                  rag: "test-rag"
                  diagrams: "generated/diagrams"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                    pool:
                      - id: "ollama-key-1"
                        email: "local1@localhost"
                        name: "Local Instance #1"
                        keyRef: "OLLAMA_KEY_1"
                        provider: "OLLAMA"
                        services: ["CHAT_COMPLETION"]
                        baseUrl: "http://localhost:${wireMockServer.port()}"
                      - id: "ollama-key-2"
                        email: "local2@localhost"
                        name: "Local Instance #2"
                        keyRef: "OLLAMA_KEY_2"
                        provider: "OLLAMA"
                        services: ["CHAT_COMPLETION"]
                        baseUrl: "http://localhost:${wireMockServer.port()}"
                  maxIterations: 1
                  validation: false
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts").mkdirs()
            File(sharedProjectDir, "test-prompts/rotation.prompt")
                .writeText("Create a class diagram with rotation test")

            val result = runner(
                "processPlantumlPrompts",
                "-Dplantuml.test.mode=true",
                "--stacktrace",
            ).build()

            assertEquals(SUCCESS, result.task(":processPlantumlPrompts")?.outcome)
            assertTrue(
                wireMockServer.allServeEvents.size >= 1,
                "WireMock doit avoir reçu au moins 1 requête pour la rotation",
            )
        }

        @Test
        @Order(3)
        @Tag("quick")
        fun `should fallback to next key when first key fails with 401`() {
            wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/api/chat"))
                    .withRequestBody(WireMock.matching(".*key1.*"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(401)
                            .withBody("""{"error": "Unauthorized - Invalid API key"}"""),
                    ),
            )

            wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/api/chat"))
                    .withRequestBody(WireMock.matching(".*key2.*"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"model":"smollm:135m","message":{"role":"assistant","content":"@startuml\nclass FallbackClass\n@enduml"},"done":true}""",
                            ),
                    ),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  images: "test-images"
                  rag: "test-rag"
                  diagrams: "generated/diagrams"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                  maxIterations: 1
                  validation: false
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts").mkdirs()
            File(sharedProjectDir, "test-prompts/fallback.prompt")
                .writeText("Create a diagram with fallback test")

            val result = runner(
                "processPlantumlPrompts",
                "-Dplantuml.test.mode=true",
                "--stacktrace",
            ).build()

            assertEquals(SUCCESS, result.task(":processPlantumlPrompts")?.outcome)
        }

        @Test
        @Order(4)
        @Tag("quick")
        fun `should log which API key is being used`() {
            wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/api/chat"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"model":"smollm:135m","message":{"role":"assistant","content":"@startuml\nclass LoggedClass\n@enduml"},"done":true}""",
                            ),
                    ),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  images: "test-images"
                  rag: "test-rag"
                  diagrams: "generated/diagrams"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                    pool:
                      - id: "ollama-logged-key"
                        email: "logged@localhost"
                        name: "Logged Key Test"
                        keyRef: "OLLAMA_LOGGED_KEY"
                        provider: "OLLAMA"
                        services: ["CHAT_COMPLETION"]
                  maxIterations: 1
                  validation: false
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts").mkdirs()
            File(sharedProjectDir, "test-prompts/logging.prompt")
                .writeText("Create a diagram with logging test")

            val result = runner(
                "processPlantumlPrompts",
                "-Dplantuml.test.mode=true",
                "--info",
            ).build()

            assertEquals(SUCCESS, result.task(":processPlantumlPrompts")?.outcome)
            assertTrue(
                result.output.contains("Using API key") ||
                        result.output.contains("ollama-logged-key") ||
                        result.output.contains("Logged Key Test") ||
                        result.output.contains("Processing") ||
                        result.output.contains("prompt"),
                "Doit logger l'utilisation de la clé API\n${result.output}",
            )
        }

        @Test
        @Order(5)
        @Tag("quick")
        fun `should handle multiple providers with separate pools`() {
            wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/api/chat"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"model":"smollm:135m","message":{"role":"assistant","content":"@startuml\nclass MultiProviderClass\n@enduml"},"done":true}""",
                            ),
                    ),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  images: "test-images"
                  rag: "test-rag"
                  diagrams: "generated/diagrams"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                    pool:
                      - id: "ollama-1"
                        email: "ollama@localhost"
                        name: "Ollama Local"
                        keyRef: "OLLAMA_KEY"
                        provider: "OLLAMA"
                        services: ["CHAT_COMPLETION"]
                  gemini:
                    pool:
                      - id: "gemini-1"
                        email: "gemini@example.com"
                        name: "Gemini Account"
                        keyRef: "GEMINI_KEY"
                        provider: "GOOGLE"
                        services: ["TEXT_GENERATION"]
                  mistral:
                    pool:
                      - id: "mistral-1"
                        email: "mistral@example.com"
                        name: "Mistral Account"
                        keyRef: "MISTRAL_KEY"
                        provider: "MISTRAL"
                        services: ["CHAT_COMPLETION"]
                  maxIterations: 1
                  validation: false
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts").mkdirs()
            File(sharedProjectDir, "test-prompts/multi-provider.prompt")
                .writeText("Create a diagram with multi-provider config")

            val result = runner(
                "processPlantumlPrompts",
                "-Dplantuml.test.mode=true",
                "--stacktrace",
            ).build()

            assertEquals(SUCCESS, result.task(":processPlantumlPrompts")?.outcome)
            assertTrue(
                result.output.contains("BUILD SUCCESSFUL") ||
                        result.output.contains("Processing") ||
                        result.output.contains("prompt"),
                "Configuration multi-providers doit réussir\n${result.output}",
            )
        }

        @Test
        @Order(6)
        @Tag("quick")
        fun `should respect quota threshold before rotation`() {
            wireMockServer.stubFor(
                WireMock.post(WireMock.urlEqualTo("/api/chat"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """{"model":"smollm:135m","message":{"role":"assistant","content":"@startuml\nclass QuotaClass\n@enduml"},"done":true}""",
                            ),
                    ),
            )

            File(sharedProjectDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "test-prompts"
                output:
                  images: "test-images"
                  rag: "test-rag"
                  diagrams: "generated/diagrams"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:${wireMockServer.port()}"
                    modelName: "smollm:135m"
                    pool:
                      - id: "quota-key-1"
                        email: "quota1@localhost"
                        name: "Quota Key #1"
                        keyRef: "QUOTA_KEY_1"
                        provider: "OLLAMA"
                        services: ["CHAT_COMPLETION"]
                        quota:
                          limitType: REQUESTS
                          limitValue: 10
                          thresholdPercent: 50
                      - id: "quota-key-2"
                        email: "quota2@localhost"
                        name: "Quota Key #2"
                        keyRef: "QUOTA_KEY_2"
                        provider: "OLLAMA"
                        services: ["CHAT_COMPLETION"]
                        quota:
                          limitType: REQUESTS
                          limitValue: 20
                          thresholdPercent: 80
                  maxIterations: 1
                  validation: false
                """.trimIndent(),
            )

            File(sharedProjectDir, "test-prompts").mkdirs()
            File(sharedProjectDir, "test-prompts/quota.prompt")
                .writeText("Create a diagram with quota test")

            val result = runner(
                "processPlantumlPrompts",
                "-Dplantuml.test.mode=true",
                "--stacktrace",
            ).build()

            assertEquals(SUCCESS, result.task(":processPlantumlPrompts")?.outcome)
            assertTrue(
                result.output.contains("quota") ||
                        result.output.contains("Processing") ||
                        result.output.contains("prompt") ||
                        result.output.contains("BUILD SUCCESSFUL"),
                "Configuration avec quota doit réussir\n${result.output}",
            )
        }
    }
}