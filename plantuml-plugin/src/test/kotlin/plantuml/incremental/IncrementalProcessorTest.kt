package plantuml.incremental

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IncrementalProcessorTest {

    @TempDir
    lateinit var tempDir: File

    private fun setupProject(prompts: List<Pair<String, String>>): Triple<File, File, File> {
        val projectDir = File(tempDir, "project").apply { mkdirs() }
        val promptsDir = File(projectDir, "prompts").apply { mkdirs() }
        val diagramsDir = File(projectDir, "generated/diagrams").apply { mkdirs() }
        val imagesDir = File(projectDir, "generated/images").apply { mkdirs() }
        prompts.forEach { (name, content) ->
            File(promptsDir, name).writeText(content)
        }
        return Triple(projectDir, diagramsDir, imagesDir)
    }

    @Test
    fun `should identify unchanged prompts as skipped`() {
        val (projectDir, _, _) = setupProject(listOf("test.prompt" to "Create a diagram"))
        val promptsDir = File(projectDir, "prompts")
        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")
        val store = ChecksumStore(checksumsDir)
        val promptFile = File(promptsDir, "test.prompt")
        store.storeChecksum(promptFile)

        val processor = IncrementalProcessor(checksumsDir)
        val decision = processor.decideProcessing(promptFile, forceReprocess = false)

        assertThat(decision).isEqualTo(ProcessingDecision.SKIP)
    }

    @Test
    fun `should identify changed prompts as process`() {
        val (projectDir, _, _) = setupProject(listOf("test.prompt" to "Create a diagram"))
        val promptsDir = File(projectDir, "prompts")
        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")
        val store = ChecksumStore(checksumsDir)
        val promptFile = File(promptsDir, "test.prompt")
        store.storeChecksum(promptFile)
        promptFile.writeText("Create a different diagram")

        val processor = IncrementalProcessor(checksumsDir)
        val decision = processor.decideProcessing(promptFile, forceReprocess = false)

        assertThat(decision).isEqualTo(ProcessingDecision.PROCESS)
    }

    @Test
    fun `should identify new prompts as process`() {
        val (projectDir, _, _) = setupProject(listOf("test.prompt" to "Create a diagram"))
        val promptsDir = File(projectDir, "prompts")
        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")
        val promptFile = File(promptsDir, "test.prompt")

        val processor = IncrementalProcessor(checksumsDir)
        val decision = processor.decideProcessing(promptFile, forceReprocess = false)

        assertThat(decision).isEqualTo(ProcessingDecision.PROCESS)
    }

    @Test
    fun `should force reprocess when flag is true`() {
        val (projectDir, _, _) = setupProject(listOf("test.prompt" to "Create a diagram"))
        val promptsDir = File(projectDir, "prompts")
        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")
        val store = ChecksumStore(checksumsDir)
        val promptFile = File(promptsDir, "test.prompt")
        store.storeChecksum(promptFile)

        val processor = IncrementalProcessor(checksumsDir)
        val decision = processor.decideProcessing(promptFile, forceReprocess = true)

        assertThat(decision).isEqualTo(ProcessingDecision.PROCESS)
    }

    @Test
    fun `should update checksum after processing`() {
        val (projectDir, _, _) = setupProject(listOf("test.prompt" to "Create a diagram"))
        val promptsDir = File(projectDir, "prompts")
        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")
        val promptFile = File(promptsDir, "test.prompt")

        val processor = IncrementalProcessor(checksumsDir)
        processor.markAsProcessed(promptFile)

        assertThat(processor.decideProcessing(promptFile, forceReprocess = false))
            .isEqualTo(ProcessingDecision.SKIP)
    }

    @Test
    fun `should remove orphaned outputs when prompt is deleted`() {
        val projectDir = File(tempDir, "project").apply { mkdirs() }
        val promptsDir = File(projectDir, "prompts").apply { mkdirs() }
        val diagramsDir = File(projectDir, "generated/diagrams").apply { mkdirs() }
        val imagesDir = File(projectDir, "generated/images").apply { mkdirs() }

        File(diagramsDir, "prompt-1.yml").writeText("diagram 1")
        File(diagramsDir, "prompt-2.yml").writeText("diagram 2")
        File(imagesDir, "prompt-1.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()))
        File(imagesDir, "prompt-2.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()))

        File(promptsDir, "prompt-2.prompt").writeText("Create diagram 2")

        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")
        val processor = IncrementalProcessor(checksumsDir)
        processor.cleanupOrphanedOutputs(promptsDir, diagramsDir, imagesDir)

        assertThat(File(diagramsDir, "prompt-1.yml")).doesNotExist()
        assertThat(File(imagesDir, "prompt-1.png")).doesNotExist()
        assertThat(File(diagramsDir, "prompt-2.yml")).exists()
        assertThat(File(imagesDir, "prompt-2.png")).exists()
    }

    @Test
    fun `should reindex puml when source prompt has changed`() {
        val (projectDir, _, _) = setupProject(listOf("test.prompt" to "Create a diagram"))
        val promptsDir = File(projectDir, "prompts")
        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")
        val store = ChecksumStore(checksumsDir)
        val promptFile = File(promptsDir, "test.prompt")
        store.storeChecksum(promptFile)
        promptFile.writeText("Create a different diagram")

        val pumlFile = File(projectDir, "rag/test.puml").apply {
            parentFile.mkdirs()
            writeText("@startuml\nactor User\n@enduml")
        }

        val processor = IncrementalProcessor(checksumsDir)
        assertThat(processor.shouldReindexPuml(pumlFile, promptsDir)).isTrue()
    }

    @Test
    fun `should skip reindex puml when source prompt unchanged`() {
        val (projectDir, _, _) = setupProject(listOf("test.prompt" to "Create a diagram"))
        val promptsDir = File(projectDir, "prompts")
        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")
        val store = ChecksumStore(checksumsDir)
        val promptFile = File(promptsDir, "test.prompt")
        store.storeChecksum(promptFile)

        val pumlFile = File(projectDir, "rag/test.puml").apply {
            parentFile.mkdirs()
            writeText("@startuml\nactor User\n@enduml")
        }

        val processor = IncrementalProcessor(checksumsDir)
        assertThat(processor.shouldReindexPuml(pumlFile, promptsDir)).isFalse()
    }

    @Test
    fun `should reindex puml when no source prompt exists`() {
        val (projectDir, _, _) = setupProject(emptyList())
        val promptsDir = File(projectDir, "prompts")
        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")

        val pumlFile = File(projectDir, "rag/orphan.puml").apply {
            parentFile.mkdirs()
            writeText("@startuml\nactor User\n@enduml")
        }

        val processor = IncrementalProcessor(checksumsDir)
        assertThat(processor.shouldReindexPuml(pumlFile, promptsDir)).isTrue()
    }

    @Test
    fun `should sanitize file names with special characters`() {
        val projectDir = File(tempDir, "project").apply { mkdirs() }
        val promptsDir = File(projectDir, "prompts").apply { mkdirs() }
        val diagramsDir = File(projectDir, "generated/diagrams").apply { mkdirs() }

        File(diagramsDir, "my-complex_diagram__v1.0_.yml").writeText("diagram")
        File(promptsDir, "my-complex diagram (v1.0).prompt").writeText("Create diagram")

        val checksumsDir = File(projectDir, "build/plantuml-plugin/checksums")
        val processor = IncrementalProcessor(checksumsDir)
        processor.cleanupOrphanedOutputs(promptsDir, diagramsDir, File(projectDir, "generated/images"))

        assertThat(File(diagramsDir, "my-complex_diagram__v1.0_.yml")).exists()
    }
}