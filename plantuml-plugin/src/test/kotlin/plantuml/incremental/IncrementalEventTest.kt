package plantuml.incremental

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IncrementalEventTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `PromptSkipped event should contain prompt file name`() {
        val event = PromptSkipped("user-diagram")

        assertThat(event.promptName).isEqualTo("user-diagram")
        assertThat(event.eventType).isEqualTo("PROMPT_SKIPPED")
    }

    @Test
    fun `PromptProcessed event should contain prompt file name and iteration count`() {
        val event = PromptProcessed("user-diagram", iterations = 2)

        assertThat(event.promptName).isEqualTo("user-diagram")
        assertThat(event.iterations).isEqualTo(2)
        assertThat(event.eventType).isEqualTo("PROMPT_PROCESSED")
    }

    @Test
    fun `OutputsCleaned event should contain count of removed files`() {
        val event = OutputsCleaned(removedCount = 3)

        assertThat(event.removedCount).isEqualTo(3)
        assertThat(event.eventType).isEqualTo("OUTPUTS_CLEANED")
    }

    @Test
    fun `IncrementalProcessor should emit PromptSkipped when decision is SKIP`() {
        val promptsDir = File(tempDir, "prompts").apply { mkdirs() }
        val checksumsDir = File(tempDir, "checksums").apply { mkdirs() }
        val promptFile = File(promptsDir, "test.prompt").apply { writeText("Create a diagram") }
        val store = ChecksumStore(checksumsDir)
        store.storeChecksum(promptFile)

        val processor = IncrementalProcessor(checksumsDir)
        val events = mutableListOf<IncrementalEvent>()
        processor.onEvent { events.add(it) }

        processor.decideProcessing(promptFile, forceReprocess = false)

        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(PromptSkipped::class.java)
        assertThat((events[0] as PromptSkipped).promptName).isEqualTo("test")
    }

    @Test
    fun `IncrementalProcessor should emit PromptProcessed when markAsProcessed is called`() {
        val promptsDir = File(tempDir, "prompts").apply { mkdirs() }
        val checksumsDir = File(tempDir, "checksums").apply { mkdirs() }
        val promptFile = File(promptsDir, "test.prompt").apply { writeText("Create a diagram") }

        val processor = IncrementalProcessor(checksumsDir)
        val events = mutableListOf<IncrementalEvent>()
        processor.onEvent { events.add(it) }

        processor.markAsProcessed(promptFile, iterations = 1)

        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(PromptProcessed::class.java)
        assertThat((events[0] as PromptProcessed).iterations).isEqualTo(1)
    }

    @Test
    fun `IncrementalProcessor should emit OutputsCleaned when orphaned outputs are removed`() {
        val projectDir = File(tempDir, "project").apply { mkdirs() }
        val promptsDir = File(projectDir, "prompts").apply { mkdirs() }
        val diagramsDir = File(projectDir, "generated/diagrams").apply { mkdirs() }
        val imagesDir = File(projectDir, "generated/images").apply { mkdirs() }

        File(diagramsDir, "orphaned.yml").writeText("diagram")
        File(imagesDir, "orphaned.png").writeBytes(byteArrayOf(0x89.toByte()))
        File(promptsDir, "active.prompt").writeText("Create diagram")

        val checksumsDir = File(projectDir, "checksums")
        val processor = IncrementalProcessor(checksumsDir)
        val events = mutableListOf<IncrementalEvent>()
        processor.onEvent { events.add(it) }

        processor.cleanupOrphanedOutputs(promptsDir, diagramsDir, imagesDir)

        assertThat(events).hasSize(1)
        assertThat(events[0]).isInstanceOf(OutputsCleaned::class.java)
        assertThat((events[0] as OutputsCleaned).removedCount).isEqualTo(2)
    }
}