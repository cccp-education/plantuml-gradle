@file:Suppress("FunctionName")

package plantuml

import org.junit.jupiter.api.*
import org.junit.jupiter.api.ClassOrderer.OrderAnnotation
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.slf4j.LoggerFactory
import org.testcontainers.containers.Container
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertTrue

@Tag("fine-tune")
@TestClassOrder(OrderAnnotation::class)
@EnabledIfSystemProperty(named = "test.tags", matches = ".*fine-tune.*")
@DisplayName("PlantUML plugin — fine-tuning SmolLM2 en conteneur Python")
@TestInstance(Lifecycle.PER_CLASS)
class SmolLMFineTuneFunctionalTest {

    private val log = LoggerFactory.getLogger(javaClass)

    @Test
    @Order(1)
    @DisplayName("1-step smoke → adapter weights")
    fun smokeTest() {
        withContainer(name = "smollm-smoke", withModel = true) { c ->
            val r = c.execInContainer(
                "python3", "/scripts/fine_tune.py",
                "--model-dir", "/model",
                "--output-dir", "/tmp/lora-smoke",
                "--max-steps", "1",
            )
            logInfo(r)
            assertTrue(r.exitCode == 0, "code=${r.exitCode}: ${r.stderr}")
            assertTrue(r.stdout.contains("SUCCESS"), "Missing SUCCESS")
            assertTrue(ls(c, "/tmp/lora-smoke").contains("adapter_model.safetensors"),
                "Missing adapter")
        }
    }

    @Test
    @Order(2)
    @DisplayName("5-step pipeline → adapter + metrics.json")
    fun fullPipeline() {
        withContainer(name = "smollm-pipeline", withModel = true) { c ->
            val r = c.execInContainer(
                "python3", "/scripts/fine_tune.py",
                "--model-dir", "/model",
                "--output-dir", "/tmp/lora-pipeline",
                "--max-steps", "5",
            )
            logInfo(r)
            assertTrue(r.exitCode == 0, "code=${r.exitCode}: ${r.stderr}")
            assertTrue(r.stdout.contains("SUCCESS"), "Missing SUCCESS")
            assertTrue(r.stdout.contains("Training loss:"), "Missing loss")

            val adapters = ls(c, "/tmp/lora-pipeline/adapter")
            assertTrue(adapters.contains("adapter_model.safetensors"),
                "Missing adapter: $adapters")
            assertTrue(adapters.contains("adapter_config.json"),
                "Missing config: $adapters")

            val m = c.execInContainer("cat", "/tmp/lora-pipeline/metrics.json").stdout
            assertTrue(m.contains(""""status": "ok""""), "Bad metrics: $m")
            assertTrue(m.contains(""""max_steps": 5"""), "Bad max_steps: $m")
        }
    }

    @Test
    @Order(3)
    @DisplayName("missing model directory → non-zero exit")
    fun missingModel() {
        withContainer(name = "smollm-nomodel", withModel = false) { c ->
            val r = c.execInContainer(
                "python3", "/scripts/fine_tune.py",
                "--model-dir", "/nonexistent",
                "--output-dir", "/tmp/lora-err",
                "--max-steps", "1",
            )
            logInfo(r)
            assertTrue(r.exitCode != 0, "Expected failure, got ${r.exitCode}")
            assertTrue(
                r.stderr.contains("model.safetensors not found") ||
                        r.stdout.contains("model.safetensors not found") ||
                        r.stderr.contains("ERROR") ||
                        r.stdout.contains("ERROR"),
                "Bad error: stdout=${r.stdout} stderr=${r.stderr}",
            )
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    private val modelDir: Path = findResource("models/SmolLM2-135M-Instruct", "model.safetensors")
    private val pythonDir: Path = findResource("python", "fine_tune.py")

    private fun findResource(dir: String, marker: String): Path {
        val url = javaClass.classLoader.getResource("$dir/$marker")
            ?: error("Resource not found on classpath: $dir/$marker")
        return Path.of(url.toURI()).parent
    }

    private fun <T> withContainer(
        name: String,
        withModel: Boolean,
        block: (GenericContainer<Nothing>) -> T,
    ): T {
        val c = GenericContainer<Nothing>("plantuml-fine-tune:latest")
        c.withCopyFileToContainer(
            MountableFile.forHostPath(pythonDir.resolve("fine_tune.py").toAbsolutePath()),
            "/scripts/fine_tune.py",
        )
        c.withCopyFileToContainer(
            MountableFile.forHostPath(pythonDir.resolve("requirements.txt").toAbsolutePath()),
            "/scripts/requirements.txt",
        )
        if (withModel) {
            c.withCopyFileToContainer(
                MountableFile.forHostPath(modelDir.toAbsolutePath()),
                "/model",
            )
        }
        c.withEnv("PYTHONUNBUFFERED", "1")
        c.withLogConsumer { frame -> log.debug(frame.utf8String.trimEnd()) }
        c.withCreateContainerCmdModifier { cmd -> cmd.withName(name) }
        c.withStartupTimeout(Duration.ofSeconds(30))
        c.waitingFor(Wait.forLogMessage(".*FINE_TUNE_READY.*", 1))
        c.withCommand("sh", "-c", "echo 'FINE_TUNE_READY' && tail -f /dev/null")

        try {
            c.start()
            return block(c)
        } finally {
            c.stop()
        }
    }

    private fun ls(container: GenericContainer<*>, path: String): List<String> {
        val r = container.execInContainer("find", path, "-type", "f")
        return r.stdout.lines().filter { it.isNotBlank() }
            .map { it.trimStart('/').substringAfterLast('/') }
    }

    private fun logInfo(r: Container.ExecResult) {
        log.info("STDOUT:\n{}", r.stdout)
        if (r.stderr.isNotBlank()) log.warn("STDERR:\n{}", r.stderr)
    }
}
