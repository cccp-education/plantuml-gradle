package plantuml.incremental

import java.io.File
import java.security.MessageDigest

/**
 * Stores and compares SHA-256 checksums of prompt files for incremental processing.
 *
 * Checksums are persisted as `.sha256` files in the configured directory,
 * enabling detection of prompt changes between builds.
 *
 * @property checksumsDir Directory where checksum files are stored
 */
class ChecksumStore(private val checksumsDir: File) {

    init {
        if (!checksumsDir.exists()) {
            checksumsDir.mkdirs()
        }
    }

    /**
     * Computes the SHA-256 checksum of a file.
     *
     * @param file The file to hash
     * @return Hex-encoded SHA-256 digest
     */
    fun computeChecksum(file: File): String {
        val bytes = file.readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Stores the current checksum of a prompt file.
     *
     * @param promptFile The prompt file whose checksum to store
     */
    fun storeChecksum(promptFile: File) {
        val checksum = computeChecksum(promptFile)
        checksumFile(promptFile).writeText(checksum)
    }

    /**
     * Retrieves the previously stored checksum for a prompt file.
     *
     * @param promptFile The prompt file
     * @return The stored checksum, or null if none exists
     */
    fun getStoredChecksum(promptFile: File): String? {
        val file = checksumFile(promptFile)
        return if (file.exists()) file.readText() else null
    }

    /**
     * Checks whether a prompt file has changed since its last stored checksum.
     *
     * @param promptFile The prompt file to check
     * @return true if the file is new or has changed
     */
    fun hasPromptChanged(promptFile: File): Boolean {
        val stored = getStoredChecksum(promptFile) ?: return true
        return stored != computeChecksum(promptFile)
    }

    private fun checksumFile(promptFile: File): File =
        File(checksumsDir, "${promptFile.nameWithoutExtension}.sha256")
}