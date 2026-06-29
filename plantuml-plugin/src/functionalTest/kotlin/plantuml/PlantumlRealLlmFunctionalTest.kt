@file:Suppress("FunctionName")

package plantuml

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.*
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.assertTrue

/**
 * Couche de test fonctionnel sans WireMock — exerce le plugin contre
 * un véritable serveur Ollama local.
 *
 * Ces tests sont opt-in (tag "real-llm") et gardent les données
 * existantes WireMock intactes pour le diagnostic différentiel :
 *   - WireMock     → valide le pipeline Gradle + LangChain4j (mocké)
 *   - RealOllama   → valide l'intégration bout-en-bout avec le LLM réel
 *
 * Usage :
 *   ./gradlew functionalTest -Ptest.tags="real-llm"
 */
@Tag("real-llm")
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("PlantUML plugin — integration Ollama locale (sans WireMock)")
class PlantumlRealLlmFunctionalTest {

    private val ollamaBaseUrl = "http://localhost:11438"
    private val ollamaModel = "smollm:135m-instruct-v0.2-q8_0"
    private var ollamaAvailable = false

    @TempDir
    lateinit var projectDir: File

    @BeforeAll
    fun checkOllama() {
        ollamaAvailable = try {
            val conn = URI("$ollamaBaseUrl/api/tags").toURL()
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 3_000
            conn.readTimeout = 3_000
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun assumeOllama() {
        Assumptions.assumeTrue(
            ollamaAvailable,
            "Ollama indisponible sur $ollamaBaseUrl — test ignoré",
        )
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    private fun gradleRunner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*args, "--stacktrace")
            .withPluginClasspath()

    private fun setupProject(promptName: String, promptContent: String) {
        projectDir.resolve("settings.gradle.kts")
            .writeText("""rootProject.name = "plantuml-real-ollama"""")

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("education.cccp.plantuml") }
            plantuml { configPath = "plantuml-context.yml" }
            """.trimIndent(),
        )

        projectDir.resolve("plantuml-context.yml").writeText(
            """
            langchain4j:
              model: "ollama"
              ollama:
                baseUrl: "$ollamaBaseUrl"
                modelName: "$ollamaModel"
              validation: false
              maxIterations: 1
              temperature: 0.3
            input:
              prompts: "prompts"
            output:
              diagrams: "generated/diagrams"
              images:   "generated/images"
              rag:      "generated/rag"
            """.trimIndent(),
        )

        val promptsDir = projectDir.resolve("prompts").also { it.mkdirs() }
        promptsDir.resolve(promptName).writeText(promptContent)
    }

    // ------------------------------------------------------------------ //
    //  1. Class diagram                                                   //
    // ------------------------------------------------------------------ //

    @Nested
    @Order(1)
    @DisplayName("Class diagram")
    @TestInstance(Lifecycle.PER_CLASS)
    inner class ClassDiagram {

        @BeforeAll
        fun setup() {
            assumeOllama()
            setupProject(
                "class-diagram.prompt",
                "Create a class diagram with a single class named Person " +
                        "that has two private fields: name (String) and age (Integer).",
            )
        }

        @Test
        @DisplayName("should generate a valid .puml file from a class-diagram prompt")
        fun `generate class diagram`() {
            val result = gradleRunner("generatePlantumlDiagrams").build()
            assertTrue(result.task(":generatePlantumlDiagrams")?.outcome == SUCCESS)

            val diagrams = projectDir.resolve("generated/diagrams")
            val pumls = diagrams.listFiles { f -> f.extension == "puml" } ?: emptyArray()
            assertTrue(pumls.isNotEmpty(), "Aucun fichier .puml généré")
            val content = pumls.first().readText()
            assertTrue(content.contains("@startuml"), "Contenu manquant @startuml")
            assertTrue(content.contains("@enduml"), "Contenu manquant @enduml")
        }
    }

    // ------------------------------------------------------------------ //
    //  2. Sequence diagram                                                //
    // ------------------------------------------------------------------ //

    @Nested
    @Order(2)
    @DisplayName("Sequence diagram")
    @TestInstance(Lifecycle.PER_CLASS)
    inner class SequenceDiagram {

        @BeforeAll
        fun setup() {
            assumeOllama()
            setupProject(
                "sequence-diagram.prompt",
                "Create a sequence diagram where a Client calls a Service " +
                        "with method login(username, password). " +
                        "The Service responds with OK.",
            )
        }

        @Test
        @DisplayName("should generate a valid .puml sequence diagram")
        fun `generate sequence diagram`() {
            val result = gradleRunner("generatePlantumlDiagrams").build()
            assertTrue(result.task(":generatePlantumlDiagrams")?.outcome == SUCCESS)

            val diagrams = projectDir.resolve("generated/diagrams")
            val pumls = diagrams.listFiles { f -> f.extension == "puml" } ?: emptyArray()
            assertTrue(pumls.isNotEmpty(), "Aucun fichier .puml généré")
            val content = pumls.first().readText()
            assertTrue(content.contains("@startuml"), "Contenu manquant @startuml")
            assertTrue(content.contains("@enduml"), "Contenu manquant @enduml")
        }
    }

    // ------------------------------------------------------------------ //
    //  3. Use-case diagram                                                //
    // ------------------------------------------------------------------ //

    @Nested
    @Order(3)
    @DisplayName("Use-case diagram")
    @TestInstance(Lifecycle.PER_CLASS)
    inner class UseCaseDiagram {

        @BeforeAll
        fun setup() {
            assumeOllama()
            setupProject(
                "usecase-diagram.prompt",
                "Create a use case diagram with an actor named Admin " +
                        "and two use cases: ManageUsers and ViewReports.",
            )
        }

        @Test
        @DisplayName("should generate a valid .puml use-case diagram")
        fun `generate use-case diagram`() {
            val result = gradleRunner("generatePlantumlDiagrams").build()
            assertTrue(result.task(":generatePlantumlDiagrams")?.outcome == SUCCESS)

            val diagrams = projectDir.resolve("generated/diagrams")
            val pumls = diagrams.listFiles { f -> f.extension == "puml" } ?: emptyArray()
            assertTrue(pumls.isNotEmpty(), "Aucun fichier .puml généré")
            val content = pumls.first().readText()
            assertTrue(content.contains("@startuml"), "Contenu manquant @startuml")
            assertTrue(content.contains("@enduml"), "Contenu manquant @enduml")
        }
    }

    // ------------------------------------------------------------------ //
    //  4. Health-check (smoke test)                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @Order(4)
    @DisplayName("Health-check")
    @TestInstance(Lifecycle.PER_CLASS)
    inner class HealthCheck {

        @BeforeEach
        fun assumeOllamaAvailable() {
            assumeOllama()
        }

        @Test
        @DisplayName("Ollama /api/tags should return 200")
        fun `ollama tags endpoint reachable`() {
            val conn = URI("$ollamaBaseUrl/api/tags").toURL()
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            Assertions.assertEquals(200, conn.responseCode)
        }

        @Test
        @DisplayName("model smollm:135m-instruct-v0.2-q8_0 should be listed in /api/tags")
        fun `smollm instruct model listed`() {
            val conn = URI("$ollamaBaseUrl/api/tags").toURL()
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val body = conn.inputStream.bufferedReader().readText()
            assertTrue(
                body.contains(ollamaModel),
                "Le modèle $ollamaModel n'est pas listé dans /api/tags",
            )
        }

        @Test
        @DisplayName("chat model creation should not throw")
        fun `create chat model without exception`() {
            val config = PlantumlConfig(
                langchain4j = LangchainConfig(
                    model = "ollama",
                    ollama = OllamaConfig(ollamaBaseUrl, ollamaModel),
                ),
            )
            plantuml.service.LlmService(config).createChatModel()
        }
    }
}
