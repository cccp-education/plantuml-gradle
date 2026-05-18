package plantuml

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import plantuml.PlantumlManager.Configuration
import plantuml.PlantumlManager.Extensions
import plantuml.PlantumlManager.Tasks
import plantuml.PlantumlPlugin.PlantumlExtension
import plantuml.tasks.GeneratePlantumlDiagramsTask
import plantuml.tasks.CollectPlantumlIndexTask
import plantuml.tasks.ValidatePlantumlSyntaxTask
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlantumlManagerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should load default config when no config file exists`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        val config = Configuration.load(project)

        assertNotNull(config)
        assertNotNull(config.input)
        assertNotNull(config.output)
        assertNotNull(config.langchain4j)
    }

    @Test
    fun `should load default config when config file is empty`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        val emptyConfigFile = File(tempDir, "plantuml-context.yml")
        emptyConfigFile.writeText("")

        val config = Configuration.load(project)

        assertNotNull(config)
        assertNotNull(config.input)
        assertNotNull(config.output)
        assertNotNull(config.langchain4j)
    }

    @Test
    fun `should throw exception when YAML is invalid`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        val invalidConfigFile = File(tempDir, "plantuml-context.yml")
        invalidConfigFile.writeText("invalid: yaml: content: [")

        val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            Configuration.load(project)
        }

        org.junit.jupiter.api.Assertions.assertTrue(
            exception.message!!.contains("Invalid YAML configuration") || 
            exception.message!!.contains("line")
        )
    }

    @Test
    fun `should load config from extension configPath`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        val customConfigFile = File(tempDir, "custom-config.yml")
        customConfigFile.writeText(
            """
            input:
              prompts: "custom/prompts"
            output:
              diagrams: "custom/diagrams"
        """.trimIndent()
        )

        project.extensions.create(
            "plantuml",
            PlantumlExtension::class.java
        ).apply { configPath.set("custom-config.yml") }

        val config = Configuration.load(project)

        assertNotNull(config)
        assertEquals("custom/prompts", config.input.prompts)
        assertEquals("custom/diagrams", config.output.diagrams)
    }

    @Test
    fun `should use default config file when extension not set`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        val defaultConfigFile = File(tempDir, "plantuml-context.yml")
        defaultConfigFile.writeText(
            """
            input:
              prompts: "default/prompts"
        """.trimIndent()
        )

        val config = Configuration.load(project)

        assertNotNull(config)
        assertEquals("default/prompts", config.input.prompts)
    }

    @Test
    fun `should register all three tasks correctly`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        Tasks.registerTasks(project)

        assertNotNull(project.tasks.findByName("generatePlantumlDiagrams"))
        assertNotNull(project.tasks.findByName("validatePlantumlSyntax"))
        assertNotNull(project.tasks.findByName("collectPlantumlIndex"))
    }

    @Test
    fun `should register tasks with correct types`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        Tasks.registerTasks(project)

        assertTrue(project.tasks.findByName("generatePlantumlDiagrams") is GeneratePlantumlDiagramsTask)
        assertTrue(project.tasks.findByName("validatePlantumlSyntax") is ValidatePlantumlSyntaxTask)
        assertTrue(project.tasks.findByName("collectPlantumlIndex") is CollectPlantumlIndexTask)
    }

    @Test
    fun `should call configureExtensions without throwing`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        Extensions.configureExtensions(project)
    }

    @Test
    fun `should load config with all sections populated`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText(
            """
            input:
              prompts: "my-prompts"
            output:
              diagrams: "output/diagrams"
              images: "output/images"
            langchain4j:
              model: "ollama"
              maxIterations: 3
              validation: true
              ollama:
                baseUrl: "http://localhost:11434"
                modelName: "smollm:135m"
              openai:
                apiKey: "sk-test"
              gemini:
                apiKey: "gem-test"
              mistral:
                apiKey: "mis-test"
              claude:
                apiKey: "cla-test"
              huggingface:
                apiKey: "hf-test"
              groq:
                apiKey: "gq-test"
            git:
              userName: "test-user"
              userEmail: "test@example.com"
              commitMessage: "test commit"
              watchedBranches:
                - "main"
                - "develop"
            rag:
              databaseUrl: "jdbc:postgresql://localhost:5432/rag"
              username: "raguser"
              password: "ragpass"
              tableName: "plantuml_embeddings"
        """.trimIndent()
        )

        val config = Configuration.load(project)

        assertNotNull(config)
        assertEquals("my-prompts", config.input.prompts)
        assertEquals("output/diagrams", config.output.diagrams)
        assertEquals("output/images", config.output.images)
        assertEquals("ollama", config.langchain4j.model)
        assertEquals(3, config.langchain4j.maxIterations)
        assertTrue(config.langchain4j.validation)
        assertEquals("http://localhost:11434", config.langchain4j.ollama.baseUrl)
        assertEquals("smollm:135m", config.langchain4j.ollama.modelName)
        assertEquals("sk-test", config.langchain4j.openai.apiKey)
        assertEquals("gem-test", config.langchain4j.gemini.apiKey)
        assertEquals("test-user", config.git.userName)
        assertEquals("test@example.com", config.git.userEmail)
        assertEquals("test commit", config.git.commitMessage)
        assertEquals(listOf("main", "develop"), config.git.watchedBranches)
        assertEquals("jdbc:postgresql://localhost:5432/rag", config.rag.databaseUrl)
        assertEquals("raguser", config.rag.username)
        assertEquals("plantuml_embeddings", config.rag.tableName)
    }

    @Test
    fun `should prioritize extension configPath over default file`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        val defaultConfigFile = File(tempDir, "plantuml-context.yml")
        defaultConfigFile.writeText(
            """
            input:
              prompts: "default-prompts"
        """.trimIndent()
        )

        val customConfigFile = File(tempDir, "custom.yml")
        customConfigFile.writeText(
            """
            input:
              prompts: "custom-prompts"
        """.trimIndent()
        )

        project.extensions.create(
            "plantuml",
            PlantumlExtension::class.java
        ).apply { configPath.set("custom.yml") }

        val config = Configuration.load(project)

        assertEquals("custom-prompts", config.input.prompts)
    }

    @Test
    fun `should handle partial config and use defaults for missing sections`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        val configFile = File(tempDir, "plantuml-context.yml")
        configFile.writeText(
            """
            input:
              prompts: "only-prompts"
        """.trimIndent()
        )

        val config = Configuration.load(project)

        assertNotNull(config)
        assertEquals("only-prompts", config.input.prompts)
        assertEquals("en", config.input.defaultLang)
        assertEquals("generated/diagrams", config.output.diagrams)
        assertEquals("generated/images", config.output.images)
        assertEquals("ollama", config.langchain4j.model)
        assertEquals(5, config.langchain4j.maxIterations)
        assertTrue(config.langchain4j.validation)
        assertEquals("http://localhost:11434", config.langchain4j.ollama.baseUrl)
        assertEquals("smollm:135m", config.langchain4j.ollama.modelName)
    }
}
