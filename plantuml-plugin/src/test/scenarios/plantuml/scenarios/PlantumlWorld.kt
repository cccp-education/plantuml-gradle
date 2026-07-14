package plantuml.scenarios

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpServer.create
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeAll
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.File.createTempFile
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI

class PlantumlWorld {
    val log: Logger = getLogger(PlantumlWorld::class.java)

    val scope = CoroutineScope(Default + SupervisorJob())

    var projectDir: File? = null
    var buildResult: BuildResult? = null
    var exception: Throwable? = null
    val environmentVariables = mutableMapOf<String, String>()

    var i18nErrorPool: plantuml.apikey.ApiKeyPool? = null
    var i18nErrorGraphFile: File? = null
    var i18nErrorPromptsDir: File? = null
    var i18nErrorResponse: String? = null

    var tieredPool: plantuml.apikey.ApiKeyPool? = null
    var tieredSelectedKeys: MutableList<plantuml.apikey.ApiKeyEntry> = mutableListOf()

    var crossProviderOrchestrator: plantuml.apikey.CrossProviderFallbackOrchestrator? = null
    var crossProviderSelectedKeys: MutableList<plantuml.apikey.ApiKeyEntry> = mutableListOf()

    var boundaryResolver: plantuml.boundary.TranslationResolver? = null
    var boundaryResult: plantuml.boundary.ResolvedText? = null
    var boundaryDiagram: plantuml.PlantumlDiagram? = null
    var boundaryGraph: plantuml.KnowledgeGraph? = null
    var boundaryRenderedDiagram: String? = null

    var apiKeyPool: plantuml.apikey.ApiKeyPool? = null
    var apiKeyPoolEntries: MutableList<plantuml.apikey.ApiKeyEntry> = mutableListOf()
    var apiKeyPoolSelectedKeys: MutableList<plantuml.apikey.ApiKeyEntry> = mutableListOf()
    var apiKeyPoolConfig: plantuml.apikey.ApiKeyPoolConfig? = null
    var apiKeyPoolAuditEnabled: Boolean = true
    var apiKeyPoolFallbackEnabled: Boolean = true
    var apiKeyPoolVersion: String = "1.0"

    private val asyncJobs = mutableListOf<Deferred<BuildResult>>()
    
    private val version: String = System.getProperty("plugin.version", "0.0.0")

    companion object {
        private var templateProjectDir: File? = null
        private val pluginVersion: String by lazy {
            val projectRoot = File(System.getProperty("user.dir"))
            val tomlFile = File(projectRoot, "gradle/libs.versions.toml")
            check(tomlFile.exists()) { "Cannot find libs.versions.toml at ${tomlFile.absolutePath}" }
            tomlFile.readLines()
                .firstOrNull { it.startsWith("plantuml =") }
                ?.substringAfter("=")
                ?.trim()
                ?.trim('"')
                ?: throw IllegalStateException("Cannot find plantuml version in libs.versions.toml")
        }

        @BeforeAll
        @JvmStatic
        fun createTemplateProject() {
            if (templateProjectDir == null) {
                templateProjectDir = createBaseTemplateProject()
            }
        }

        private fun createBaseTemplateProject(): File {
            val templateDir =
                File(System.getProperty("java.io.tmpdir"), "plantuml-test-template-${System.currentTimeMillis()}")
            templateDir.mkdirs()

            val pluginRoot = File(System.getProperty("user.dir"))

            File(templateDir, "settings.gradle.kts").writeText(
                """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                    }
                }
                includeBuild("${pluginRoot.absolutePath.replace("\\", "\\\\")}")
                rootProject.name = "plantuml-test-template"
                """.trimIndent()
            )

            File(templateDir, "build.gradle.kts").writeText(
                """
                plugins {
                    id("education.cccp.plantuml")
                }
                
                plantuml {
                    configPath = "plantuml-context.yml"
                }
                """.trimIndent()
            )

            File(templateDir, "plantuml-context.yml").writeText(
                """
                input:
                  prompts: "prompts"
                output:
                  images: "generated/images"
                  rag: "generated/rag"
                  diagrams: "generated/diagrams"
                  validations: "generated/validations"
                langchain4j:
                  model: "ollama"
                  ollama:
                    baseUrl: "http://localhost:11438"
                    modelName: "smollm:135m"
                  maxIterations: 5
                """.trimIndent()
            )

            templateDir.deleteOnExit()
            return templateDir
        }
    }

    /** Base URL of the Ollama instance available for this scenario. */
    var ollamaBaseUrl: String? = null
        private set

    /**
     * Base URL of the mock LLM server.
     */
    var mockServerPort: Int? = null
        private set

    private var mockServer: HttpServer? = null
    private var mockResponseQueue: List<String>? = null
    private var mockResponseIndex: Int = 0
    
    private var portConflictServer: java.net.ServerSocket? = null

    /**
     * Starts a mock LLM server with custom handler logic.
     */
    fun startMockLlmServer(server: HttpServer, port: Int) {
        mockServerPort = port
        mockServer = server
        log.info("Custom mock LLM server started on port $port")
    }

    /**
     * Sets the mock server port for unreachable server scenarios.
     */
    fun setMockServerPort(port: Int) {
        mockServerPort = port
    }

    /**
     * Ensures an Ollama instance is available:
     * - If Ollama is running locally on port 11438, uses it directly.
     * - Otherwise starts a Testcontainers Ollama container.
     */
    fun ensureOllama() {
        if (isOllamaLocal()) {
            ollamaBaseUrl = "http://localhost:11438"
            log.info("Ollama detected locally at $ollamaBaseUrl")
        }
    }

    private fun isOllamaLocal(): Boolean = runCatching {
        val conn = URI("http://localhost:11438/api/tags").toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.requestMethod = "GET"
        conn.responseCode == 200
    }.getOrDefault(false)

    /**
     * Starts a minimal Ollama-compatible HTTP server that always returns
     * [responseBody] as the assistant message content.
     */
    fun startMockLlm(responseBody: String) {
        mockResponseQueue = listOf(responseBody)
        mockResponseIndex = 0
        startMockLlmWithQueue()
    }

    /**
     * Starts a minimal Ollama-compatible HTTP server that returns a sequence of responses.
     * Each call to /api/chat returns the next response in the sequence.
     */
    fun mockLlmReturnsSequence(vararg responses: String) {
        mockResponseQueue = responses.toList()
        mockResponseIndex = 0
        startMockLlmWithQueue()
    }

    private fun startMockLlmWithQueue() {
        val server = create(InetSocketAddress(0), 0)
        val port = server.address.port
        mockServerPort = port

        server.createContext("/api/chat") { exchange ->
            val responseBody = mockResponseQueue?.getOrNull(mockResponseIndex) ?: mockResponseQueue?.last() ?: ""
            if (mockResponseQueue != null && mockResponseIndex < mockResponseQueue!!.size - 1) {
                mockResponseIndex++
            }
            
            val ollamaResponse = """
                {
                  "model": "smollm:135m",
                  "message": { "role": "assistant", "content": ${escapeJson(responseBody)} },
                  "done": true
                }
            """.trimIndent().toByteArray()

            exchange.sendResponseHeaders(200, ollamaResponse.size.toLong())
            exchange.responseBody.use { it.write(ollamaResponse) }
        }

        server.createContext("/v1/chat/completions") { exchange ->
            val responseBody = mockResponseQueue?.getOrNull(mockResponseIndex) ?: mockResponseQueue?.last() ?: ""
            if (mockResponseQueue != null && mockResponseIndex < mockResponseQueue!!.size - 1) {
                mockResponseIndex++
            }
            
            val openaiResponse = """
                {
                  "id": "chatcmpl-mock",
                  "object": "chat.completion",
                  "created": ${System.currentTimeMillis() / 1000},
                  "model": "mock-model",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": ${escapeJson(responseBody)}
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 20,
                    "total_tokens": 30
                  }
                }
            """.trimIndent().toByteArray()

            exchange.sendResponseHeaders(200, openaiResponse.size.toLong())
            exchange.responseBody.use { it.write(openaiResponse) }
        }

        server.createContext("/v1beta/models") { exchange ->
            val responseBody = """{"name": "mock-gemini"}""".toByteArray()
            exchange.sendResponseHeaders(200, responseBody.size.toLong())
            exchange.responseBody.use { it.write(responseBody) }
        }

        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent") { exchange ->
            val responseBody = mockResponseQueue?.getOrNull(mockResponseIndex) ?: mockResponseQueue?.last() ?: ""
            if (mockResponseQueue != null && mockResponseIndex < mockResponseQueue!!.size - 1) {
                mockResponseIndex++
            }
            
            val geminiResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{
                        "text": ${escapeJson(responseBody)}
                      }],
                      "role": "model"
                    },
                    "finishReason": "STOP",
                    "index": 0
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 10,
                    "candidatesTokenCount": 20,
                    "totalTokenCount": 30
                  }
                }
            """.trimIndent().toByteArray()

            exchange.sendResponseHeaders(200, geminiResponse.size.toLong())
            exchange.responseBody.use { it.write(geminiResponse) }
        }

        server.createContext("/api/v1/chat/completions") { exchange ->
            val responseBody = mockResponseQueue?.getOrNull(mockResponseIndex) ?: mockResponseQueue?.last() ?: ""
            if (mockResponseQueue != null && mockResponseIndex < mockResponseQueue!!.size - 1) {
                mockResponseIndex++
            }
            
            val mistralResponse = """
                {
                  "id": "mistral-mock",
                  "object": "chat.completion",
                  "created": ${System.currentTimeMillis() / 1000},
                  "model": "mock-mistral",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": ${escapeJson(responseBody)}
                    },
                    "finish_reason": "stop"
                  }]
                }
            """.trimIndent().toByteArray()

            exchange.sendResponseHeaders(200, mistralResponse.size.toLong())
            exchange.responseBody.use { it.write(mistralResponse) }
        }

        server.createContext("/v1/messages") { exchange ->
            val responseBody = mockResponseQueue?.getOrNull(mockResponseIndex) ?: mockResponseQueue?.last() ?: ""
            if (mockResponseQueue != null && mockResponseIndex < mockResponseQueue!!.size - 1) {
                mockResponseIndex++
            }
            
            val anthropicResponse = """
                {
                  "id": "msg_mock",
                  "type": "message",
                  "role": "assistant",
                  "content": [{
                    "type": "text",
                    "text": ${escapeJson(responseBody)}
                  }],
                  "model": "mock-claude",
                  "stop_reason": "end_turn"
                }
            """.trimIndent().toByteArray()

            exchange.sendResponseHeaders(200, anthropicResponse.size.toLong())
            exchange.responseBody.use { it.write(anthropicResponse) }
        }

        server.executor = null
        server.start()
        mockServer = server
        log.info("Mock LLM server started on port $port with ${mockResponseQueue?.size ?: 1} response(s) - Ollama/OpenAI/Gemini/Mistral/Anthropic endpoints")
    }

    private val objectMapper = ObjectMapper()
    
    private fun escapeJson(value: String): String =
        objectMapper.writeValueAsString(value)

    fun stopMockLlm() {
        mockServer?.stop(0)
        mockServer = null
        mockServerPort = null
    }

    /**
     * Executes a Gradle task asynchronously with optional Gradle properties.
     */
    fun executeGradleAsync(
        vararg tasks: String,
        properties: Map<String, String> = emptyMap(),
    ): Deferred<BuildResult> {
        require(projectDir != null) { "Project directory must be initialized" }
        val propArgs = properties.map { (k, v) -> "-P$k=$v" }
        val allArgs = tasks.toList() + propArgs + 
            "-Pplantuml.output.rag=${projectDir!!.absolutePath}/build/plantuml-plugin/generated/rag"
        log.info("Starting async Gradle execution: $allArgs")
        return scope.async {
            try {
                GradleRunner.create()
                    .withProjectDir(projectDir!!)
                    .withArguments(allArgs)
                    .withTestKitDir(File(System.getProperty("user.home"), ".gradle/testkit"))
                    .withGradleVersion("9.4.1")
                    .forwardStdOutput(System.out.writer())
                    .forwardStdError(System.err.writer())
                    .build()
            } catch (e: Exception) {
                log.error("Gradle build failed", e)
                exception = e
                throw e
            }
        }.also { asyncJobs.add(it) }
    }

    fun executeGradle(
        vararg tasks: String,
        properties: Map<String, String> = emptyMap(),
        systemProperties: Map<String, String> = emptyMap(),
    ): BuildResult {
        require(projectDir != null) { "Project directory must be initialized" }
        val propArgs = properties.map { (k, v) -> "-P$k=$v" }
        val systemPropArgs = systemProperties.map { (k, v) -> "-D$k=$v" }
        val allArgs = tasks.toList() + propArgs + systemPropArgs + 
            "-Pplantuml.output.rag=${projectDir!!.absolutePath}/build/plantuml-plugin/generated/rag"
        log.info("Starting sync Gradle execution: $allArgs")
        
        val gradleEnvironment = mutableMapOf<String, String>()
        environmentVariables.forEach { (key, value) ->
            gradleEnvironment[key] = value
        }
        
        return try {
            var runner = GradleRunner.create()
                .withProjectDir(projectDir!!)
                .withArguments(allArgs)
                .withTestKitDir(File(System.getProperty("user.home"), ".gradle/testkit"))
                .withGradleVersion("9.4.1")
                .forwardStdOutput(System.out.writer())
                .forwardStdError(System.err.writer())
            
            if (gradleEnvironment.isNotEmpty()) {
                runner = runner.withEnvironment(gradleEnvironment)
            }
            
            runner.build().also { buildResult = it }
        } catch (e: Exception) {
            log.error("Gradle build failed", e)
            exception = e
            throw e
        } finally {
            environmentVariables.clear()
        }
    }

    suspend fun awaitAll() {
        if (asyncJobs.isNotEmpty()) {
            log.info("Waiting for ${asyncJobs.size} async operations...")
            asyncJobs.awaitAll()
            log.info("All async operations completed")
        }
    }

    /**
     * Creates a temporary Gradle project for testing by copying from template.
     * This is faster than creating from scratch.
     */
    fun createGradleProject(configFileName: String = "plantuml-context.yml", gradleProperties: Map<String, String>? = null): File {
        val templateDir = templateProjectDir ?: createBaseTemplateProject().also { templateProjectDir = it }

        val testDir = createTempFile("gradle-test-", "").apply {
            delete()
            mkdirs()
        }

        templateDir.copyRecursively(testDir, overwrite = true)

        if (configFileName != "plantuml-context.yml") {
            val configFile = File(testDir, configFileName)
            if (!configFile.exists()) {
                configFile.createNewFile()
            }
        }
        
        // Write gradle.properties if provided
        if (gradleProperties != null && gradleProperties.isNotEmpty()) {
            val gradleProps = File(testDir, "gradle.properties")
            gradleProps.writeText(
                gradleProperties.entries.joinToString("\n") { (k, v) -> "$k=$v" }
            )
        }

        projectDir = testDir
        
        // Track for automatic cleanup
        try {
            val cleanupExtension = TestCleanupExtension()
            cleanupExtension.trackTempDirectory(testDir.absolutePath)
        } catch (e: Exception) {
            log.warn("Failed to track temp directory: ${e.message}")
        }
        
        return testDir
    }

    /**
     * Creates a prompt file in the project's prompts directory.
     */
    fun createPromptFile(fileName: String, content: String): File {
        require(projectDir != null) { "Project directory must be initialized first" }
        val promptsDir = File(projectDir, "prompts").apply { 
            if (!exists()) mkdirs() 
        }
        val promptFile = File(promptsDir, fileName)
        promptFile.writeText(content)
        return promptFile
    }

    /**
     * Creates a PlantUML file in the specified directory.
     */
    fun createPlantUmlFile(fileName: String, content: String, directory: String = "test-diagrams"): File {
        require(projectDir != null) { "Project directory must be initialized first" }
        val targetDir = File(projectDir, directory).apply { 
            if (!exists()) mkdirs() 
        }
        val plantumlFile = File(targetDir, fileName)
        plantumlFile.writeText(content)
        return plantumlFile
    }

    /**
     * Verifies that a file exists in the project directory.
     */
    fun verifyFileExists(relativePath: String, message: String = "File should exist") {
        val file = File(projectDir, relativePath)
        assertThat(file).`as`(message).exists()
    }

    /**
     * Verifies that a file does not exist in the project directory.
     */
    fun verifyFileNotExists(relativePath: String, message: String = "File should not exist") {
        val file = File(projectDir, relativePath)
        assertThat(file).`as`(message).doesNotExist()
    }

    /**
     * Verifies that a directory contains files matching a pattern.
     */
    fun verifyDirectoryContainsFiles(
        relativePath: String, 
        extension: String, 
        message: String = "Directory should contain files"
    ): Array<File> {
        val dir = File(projectDir, relativePath)
        assertThat(dir).`as`("Directory $relativePath should exist").exists()
        val files = dir.listFiles { file -> file.extension == extension }
        assertThat(files).`as`(message).isNotNull.isNotEmpty
        return files!!
    }

    /**
     * Sets the port conflict server for simulating port 5432 in use.
     */
    fun setPortConflictServer(server: java.net.ServerSocket?) {
        portConflictServer = server
    }

    @Suppress("unused")
    fun cleanup() {
        stopMockLlm()
        portConflictServer?.close()
        portConflictServer = null
        scope.cancel()
        projectDir?.deleteRecursively()
        projectDir = null
        buildResult = null
        exception = null
        asyncJobs.clear()
        boundaryResolver = null
        boundaryResult = null
        boundaryDiagram = null
        boundaryGraph = null
        boundaryRenderedDiagram = null
        apiKeyPool = null
        apiKeyPoolEntries.clear()
        apiKeyPoolSelectedKeys.clear()
        apiKeyPoolConfig = null
        apiKeyPoolAuditEnabled = true
        apiKeyPoolFallbackEnabled = true
        apiKeyPoolVersion = "1.0"
        System.clearProperty("plantuml.test.disk.full")
        System.clearProperty("plantuml.test.rag.mode")
    }
}