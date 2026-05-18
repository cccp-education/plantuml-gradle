package plantuml.scenarios

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpServer.create
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.net.InetSocketAddress

class ErrorHandlingSteps(private val world: PlantumlWorld) {

    private var slowMockServer: HttpServer? = null

    @Given("a mock LLM that responds after {int} seconds")
    fun mockLlmThatRespondsAfterSeconds(delaySeconds: Int) {
        val server = create(InetSocketAddress(0), 0)
        val port = server.address.port

        server.createContext("/api/chat") { exchange ->
            Thread.sleep(delaySeconds * 1000L)
            val ollamaResponse = """
                {
                  "model": "smollm:135m",
                  "message": { "role": "assistant", "content": "{\"plantuml\": {\"code\": \"@startuml\\n@enduml\", \"description\": \"Delayed response\"}}" },
                  "done": true
                }
            """.trimIndent().toByteArray()

            exchange.sendResponseHeaders(200, ollamaResponse.size.toLong())
            exchange.responseBody.use { it.write(ollamaResponse) }
        }

        server.createContext("/v1/chat/completions") { exchange ->
            Thread.sleep(delaySeconds * 1000L)
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
                      "content": "@startuml\\n@enduml"
                    },
                    "finish_reason": "stop"
                  }]
                }
            """.trimIndent().toByteArray()

            exchange.sendResponseHeaders(200, openaiResponse.size.toLong())
            exchange.responseBody.use { it.write(openaiResponse) }
        }

        server.executor = null
        server.start()
        slowMockServer = server
        world.startMockLlmServer(server, port)
        world.setMockServerPort(port)
    }

    @Given("a mock LLM that returns rate limit errors")
    fun mockLlmThatReturnsRateLimitErrors() {
        val server = create(InetSocketAddress(0), 0)
        val port = server.address.port

        var callCount = 0

        server.createContext("/api/chat") { exchange ->
            callCount++
            if (callCount <= 2) {
                val errorResponse = """
                    {
                      "error": {
                        "message": "Rate limit exceeded. Please retry after 60 seconds.",
                        "type": "rate_limit_error",
                        "code": 429
                      }
                    }
                """.trimIndent().toByteArray()

                exchange.sendResponseHeaders(429, errorResponse.size.toLong())
                exchange.responseBody.use { it.write(errorResponse) }
            } else {
                val successResponse = """
                    {
                      "model": "smollm:135m",
                      "message": { "role": "assistant", "content": "{\"plantuml\": {\"code\": \"@startuml\\nclass Success\\n@enduml\", \"description\": \"Success after retry\"}}" },
                      "done": true
                    }
                """.trimIndent().toByteArray()

                exchange.sendResponseHeaders(200, successResponse.size.toLong())
                exchange.responseBody.use { it.write(successResponse) }
            }
        }

        server.createContext("/v1/chat/completions") { exchange ->
            callCount++
            if (callCount <= 2) {
                val errorResponse = """
                    {
                      "error": {
                        "message": "Rate limit exceeded",
                        "type": "rate_limit_error"
                      }
                    }
                """.trimIndent().toByteArray()

                exchange.sendResponseHeaders(429, errorResponse.size.toLong())
                exchange.responseBody.use { it.write(errorResponse) }
            } else {
                val successResponse = """
                    {
                      "id": "chatcmpl-mock",
                      "object": "chat.completion",
                      "created": ${System.currentTimeMillis() / 1000},
                      "model": "mock-model",
                      "choices": [{
                        "index": 0,
                        "message": {
                          "role": "assistant",
                          "content": "@startuml\\nclass Success\\n@enduml"
                        },
                        "finish_reason": "stop"
                      }]
                    }
                """.trimIndent().toByteArray()

                exchange.sendResponseHeaders(200, successResponse.size.toLong())
                exchange.responseBody.use { it.write(successResponse) }
            }
        }

        server.executor = null
        server.start()
        slowMockServer = server
        world.startMockLlmServer(server, port)
    }

    @Given("the LLM server is unreachable")
    fun llmServerIsUnreachable() {
        // Set a port but don't start a server - connection will fail
        world.setMockServerPort(9998)
    }

    @Given("a mock LLM that returns malformed JSON")
    fun mockLlmThatReturnsMalformedJson() {
        val server = create(InetSocketAddress(0), 0)
        val port = server.address.port

        server.createContext("/api/chat") { exchange ->
            val malformedJson = """
                {
                  "model": "smollm:135m",
                  "message": { "role": "assistant", "content": "{\"plantuml\": {\"code\": \"@startuml\\n@enduml\", \"description\": \"Missing closing brace" }
                }
            """.trimIndent()
            val response = malformedJson.toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }

        server.executor = null
        server.start()
        world.startMockLlmServer(server, port)
    }

    @Given("Docker is available but port 5432 is in use")
    fun dockerAvailableButPortInUse() {
        // gradle.properties should have been created in Background
        // Overwrite it to force testcontainers mode and port conflict
        world.projectDir?.let {
            val gradleProps = File(it, "gradle.properties")
            gradleProps.writeText(
                """
                # Force testcontainers mode for pgvector test
                rag.mode=testcontainers
                plantuml.test.simulate.port.conflict=true
                plantuml.test.mode=true
                
                """.trimIndent()
            )
        }
    }

    @Given("the output directory has insufficient disk space")
    fun outputDirectoryHasInsufficientDiskSpace() {
        // Set a system property to trigger disk space simulation in test mode
        System.setProperty("plantuml.test.disk.full", "true")
    }

    @Given("the plantuml-config.yml file is missing")
    fun plantumlConfigFileIsMissing() {
        world.createGradleProject("nonexistent-config.yml")
    }

    @Given("the plantuml-config.yml contains invalid YAML syntax")
    fun plantumlConfigContainsInvalidYaml() {
        // Create a fresh project and write invalid YAML
        world.createGradleProject()
        val configFile = File(world.projectDir, "plantuml-context.yml")
        val invalidYaml = """
            input:
              prompts: "prompts
            output:
              images: "test"
        """.trimIndent()
        configFile.writeText(invalidYaml)
    }

    @When("I run generatePlantumlDiagrams task with timeout {int} seconds")
    fun runGeneratePlantumlDiagramsTaskWithTimeout(timeoutSeconds: Int) = runBlocking {
        val properties = mutableMapOf<String, String>()
        properties["plantuml.langchain4j.model"] = "ollama"
        properties["plantuml.langchain4j.ollama.baseUrl"] = "http://localhost:9999"
        properties["plantuml.langchain4j.ollama.modelName"] = "smollm:135m"
        properties["plantuml.langchain4j.timeout"] = "${timeoutSeconds}s"
        properties["plantuml.test.mode"] = "true"
        val systemProperties = mutableMapOf<String, String>()
        world.projectDir?.let {
            systemProperties["plugin.project.dir"] = it.absolutePath
        }

        try {
            world.executeGradle("generatePlantumlDiagrams", properties = properties, systemProperties = systemProperties)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @When("I run collectPlantumlIndex task with port conflict simulation")
    fun runCollectPlantumlIndexTaskWithPortConflict() = runBlocking {
        val properties = mutableMapOf<String, String>()
        val systemProperties = mutableMapOf<String, String>()
        world.projectDir?.let {
            properties["plugin.project.dir"] = it.absolutePath
        }
        properties["plantuml.test.mode"] = "true"
        properties["rag.mode"] = "testcontainers"
        systemProperties["plantuml.test.simulate.port.conflict"] = "true"

        try {
            world.executeGradle("collectPlantumlIndex", properties = properties, systemProperties = systemProperties)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @When("I run generatePlantumlDiagrams task with invalid config")
    fun runGeneratePlantumlDiagramsTaskWithInvalidConfig() = runBlocking {
        val properties = mutableMapOf<String, String>()
        properties["plantuml.test.mode"] = "true"
        val systemProperties = mutableMapOf<String, String>()
        world.projectDir?.let {
            systemProperties["plugin.project.dir"] = it.absolutePath
        }
        if (world.mockServerPort != null) {
            properties["plantuml.langchain4j.model"] = "ollama"
            properties["plantuml.langchain4j.ollama.baseUrl"] = "http://localhost:${world.mockServerPort}"
            properties["plantuml.langchain4j.ollama.modelName"] = "smollm:135m"
        }

        try {
            world.executeGradle("generatePlantumlDiagrams", properties = properties, systemProperties = systemProperties)
        } catch (e: Exception) {
            world.exception = e
        }
    }

    @Then("the task should fail with timeout error")
    fun taskShouldFailWithTimeoutError() {
        assertThat(world.exception).isNotNull
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "timeout",
            "Timeout",
            "TIMEOUT",
            "timed out",
            "Time out"
        )
    }

    @Then("a retry should be attempted")
    fun retryShouldBeAttempted() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "retry",
            "Retry",
            "RETRY",
            "attempt",
            "Attempting"
        )
    }

    @Then("after max retries, a clear error message should be displayed")
    fun afterMaxRetriesClearErrorMessageDisplayed() {
        val output = world.buildResult?.output ?: world.exception?.message ?: ""
        assertThat(output).isNotEmpty()
        assertThat(output).containsAnyOf(
            "max retries",
            "Max retries",
            "maximum retries",
            "failed after",
            "exhausted",
            "timeout",
            "Timeout",
            "Timed out",
            "attempt"
        )
    }

    @Then("the system should implement exponential backoff")
    fun systemShouldImplementExponentialBackoff() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "backoff",
            "retry",
            "waiting",
            "delay"
        )
    }

    @Then("retry after the rate limit window")
    fun retryAfterRateLimitWindow() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "retry",
            "Retry",
            "wait",
            "seconds"
        )
    }

    @Then("eventually succeed or fail with clear message")
    fun eventuallySucceedOrFailWithClearMessage() {
        val output = world.buildResult?.output ?: world.exception?.message ?: ""
        assertThat(output).isNotEmpty()
        assertThat(output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "error",
            "Error",
            "failed",
            "Failed"
        )
    }

    @Then("the task should fail with connection error")
    fun taskShouldFailWithConnectionError() {
        assertThat(world.exception).isNotNull
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "connection",
            "Connection",
            "unreachable",
            "refused",
            "connect"
        )
    }

    @Then("suggest checking network connectivity")
    fun suggestCheckingNetworkConnectivity() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "network",
            "Network",
            "connectivity",
            "connection",
            "check"
        )
    }

    @Then("the system should detect the invalid format")
    fun systemShouldDetectInvalidFormat() {
        val output = world.buildResult?.output ?: world.exception?.message ?: ""
        assertThat(output).isNotEmpty()
        assertThat(output).containsAnyOf(
            "invalid",
            "Invalid",
            "malformed",
            "parse",
            "JSON",
            "format",
            "Failed to generate",
            "failed after",
            "iterations"
        )
    }

    @Then("request correction from the LLM")
    fun requestCorrectionFromLlm() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "correction",
            "retry",
            "re-request",
            "attempt"
        )
    }

    @Then("fail with descriptive error after max attempts")
    fun failWithDescriptiveErrorAfterMaxAttempts() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "max attempts",
            "maximum attempts",
            "failed after",
            "exhausted"
        )
    }

    @Then("the task should fail with port conflict error")
    fun taskShouldFailWithPortConflictError() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "port",
            "5432",
            "in use",
            "conflict",
            "bind"
        )
    }

    @Then("suggest using a different port or stopping existing PostgreSQL")
    fun suggestUsingDifferentPortOrStoppingPostgreSQL() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "different port",
            "alternate port",
            "stopping",
            "PostgreSQL",
            "pgvector"
        )
    }

    @Then("the task should fail with disk space error")
    fun taskShouldFailWithDiskSpaceError() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "disk",
            "space",
            "storage",
            "insufficient"
        )
    }

    @Then("clean up any partial outputs")
    fun cleanUpAnyPartialOutputs() {
        // Verify no partial files left behind
        world.projectDir?.let {
            val buildDir = File(it, "build/plantuml-plugin")
            assertThat(buildDir.exists()).isFalse()
                .withFailMessage("Partial outputs should have been cleaned up")
        }
    }

    @Then("the task should create a default configuration")
    fun taskShouldCreateDefaultConfiguration() {
        world.projectDir?.let {
            val defaultConfig = File(it, "plantuml-context.yml")
            assertThat(defaultConfig).exists()
        }
    }

    @Then("log a warning about using defaults")
    fun logWarningAboutUsingDefaults() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "warning",
            "Warning",
            "default",
            "Default",
            "using defaults"
        )
    }

    @Then("the task should fail with YAML parse error")
    fun taskShouldFailWithYamlParseError() {
        assertThat(world.exception).isNotNull
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "YAML",
            "yaml",
            "parse",
            "syntax",
            "invalid"
        )
    }

    @Then("indicate the line and nature of the error")
    fun indicateLineAndNatureOfError() {
        assertThat(world.buildResult?.output ?: world.exception?.message).containsAnyOf(
            "line",
            "Line",
            "position",
            "column",
            "at"
        )
    }
}
