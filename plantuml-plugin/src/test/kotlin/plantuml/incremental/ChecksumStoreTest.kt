package plantuml.incremental

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ChecksumStoreTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should compute SHA-256 checksum for a prompt file`() {
        val store = ChecksumStore(tempDir)
        val promptFile = File(tempDir, "test.prompt").apply { writeText("Create a diagram") }

        val checksum = store.computeChecksum(promptFile)

        assertThat(checksum).hasSize(64)
        assertThat(checksum).matches("[0-9a-f]{64}")
    }

    @Test
    fun `should store checksum for a prompt file`() {
        val store = ChecksumStore(tempDir)
        val promptFile = File(tempDir, "test.prompt").apply { writeText("Create a diagram") }

        store.storeChecksum(promptFile)

        val checksumFile = File(tempDir, "test.sha256")
        assertThat(checksumFile).exists()
        assertThat(checksumFile.readText()).hasSize(64)
    }

    @Test
    fun `should return stored checksum for a prompt file`() {
        val store = ChecksumStore(tempDir)
        val promptFile = File(tempDir, "test.prompt").apply { writeText("Create a diagram") }
        store.storeChecksum(promptFile)

        val stored = store.getStoredChecksum(promptFile)

        assertThat(stored).isNotNull
        assertThat(stored).hasSize(64)
    }

    @Test
    fun `should return null when no stored checksum exists`() {
        val store = ChecksumStore(tempDir)
        val promptFile = File(tempDir, "test.prompt").apply { writeText("Create a diagram") }

        val stored = store.getStoredChecksum(promptFile)

        assertThat(stored).isNull()
    }

    @Test
    fun `should detect unchanged prompt when checksums match`() {
        val store = ChecksumStore(tempDir)
        val promptFile = File(tempDir, "test.prompt").apply { writeText("Create a diagram") }
        store.storeChecksum(promptFile)

        val hasChanged = store.hasPromptChanged(promptFile)

        assertThat(hasChanged).isFalse
    }

    @Test
    fun `should detect changed prompt when checksums differ`() {
        val store = ChecksumStore(tempDir)
        val promptFile = File(tempDir, "test.prompt").apply { writeText("Create a diagram") }
        store.storeChecksum(promptFile)

        promptFile.writeText("Create a different diagram")

        val hasChanged = store.hasPromptChanged(promptFile)

        assertThat(hasChanged).isTrue
    }

    @Test
    fun `should detect changed prompt when no stored checksum exists`() {
        val store = ChecksumStore(tempDir)
        val promptFile = File(tempDir, "test.prompt").apply { writeText("Create a diagram") }

        val hasChanged = store.hasPromptChanged(promptFile)

        assertThat(hasChanged).isTrue
    }
}