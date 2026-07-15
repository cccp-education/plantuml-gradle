package plantuml.incremental

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IncrementalAuditLoggerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should create audit log file on initialization`() {
        val logFile = File(tempDir, "audit.log")

        IncrementalAuditLogger(logFile)

        assertThat(logFile).exists()
    }

    @Test
    fun `should log PromptSkipped event with timestamp and prompt name`() {
        val logFile = File(tempDir, "audit.log")
        val logger = IncrementalAuditLogger(logFile)

        logger.log(PromptSkipped("user-diagram"))

        val content = logFile.readText()
        assertThat(content).contains("PROMPT_SKIPPED")
        assertThat(content).contains("user-diagram")
    }

    @Test
    fun `should log PromptProcessed event with prompt name and iterations`() {
        val logFile = File(tempDir, "audit.log")
        val logger = IncrementalAuditLogger(logFile)

        logger.log(PromptProcessed("user-diagram", iterations = 3))

        val content = logFile.readText()
        assertThat(content).contains("PROMPT_PROCESSED")
        assertThat(content).contains("user-diagram")
        assertThat(content).contains("iterations=3")
    }

    @Test
    fun `should log OutputsCleaned event with removed count`() {
        val logFile = File(tempDir, "audit.log")
        val logger = IncrementalAuditLogger(logFile)

        logger.log(OutputsCleaned(removedCount = 5))

        val content = logFile.readText()
        assertThat(content).contains("OUTPUTS_CLEANED")
        assertThat(content).contains("removed=5")
    }

    @Test
    fun `should append multiple events to the same log file`() {
        val logFile = File(tempDir, "audit.log")
        val logger = IncrementalAuditLogger(logFile)

        logger.log(PromptSkipped("prompt-1"))
        logger.log(PromptProcessed("prompt-2", iterations = 1))
        logger.log(OutputsCleaned(removedCount = 2))

        val lines = logFile.readLines()
        assertThat(lines).hasSize(3)
        assertThat(lines[0]).contains("PROMPT_SKIPPED")
        assertThat(lines[1]).contains("PROMPT_PROCESSED")
        assertThat(lines[2]).contains("OUTPUTS_CLEANED")
    }

    @Test
    fun `should implement IncrementalEventListener`() {
        val logFile = File(tempDir, "audit.log")
        val logger = IncrementalAuditLogger(logFile)

        assertThat(logger).isInstanceOf(IncrementalEventListener::class.java)
    }

    @Test
    fun `IncrementalProcessor should wire audit logger as event listener`() {
        val logFile = File(tempDir, "audit.log")
        val auditLogger = IncrementalAuditLogger(logFile)
        val promptsDir = File(tempDir, "prompts").apply { mkdirs() }
        val checksumsDir = File(tempDir, "checksums").apply { mkdirs() }
        val promptFile = File(promptsDir, "test.prompt").apply { writeText("Create a diagram") }
        ChecksumStore(checksumsDir).storeChecksum(promptFile)

        val processor = IncrementalProcessor(checksumsDir)
        processor.onEvent(auditLogger::log)

        processor.decideProcessing(promptFile, forceReprocess = false)

        val content = logFile.readText()
        assertThat(content).contains("PROMPT_SKIPPED")
        assertThat(content).contains("test")
    }
}