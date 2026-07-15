package plantuml.incremental

import java.io.File
import java.security.MessageDigest

class ChecksumStore(private val checksumsDir: File) {

    init {
        if (!checksumsDir.exists()) {
            checksumsDir.mkdirs()
        }
    }

    fun computeChecksum(file: File): String {
        val bytes = file.readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun storeChecksum(promptFile: File) {
        val checksum = computeChecksum(promptFile)
        checksumFile(promptFile).writeText(checksum)
    }

    fun getStoredChecksum(promptFile: File): String? {
        val file = checksumFile(promptFile)
        return if (file.exists()) file.readText() else null
    }

    fun hasPromptChanged(promptFile: File): Boolean {
        val stored = getStoredChecksum(promptFile) ?: return true
        return stored != computeChecksum(promptFile)
    }

    private fun checksumFile(promptFile: File): File =
        File(checksumsDir, "${promptFile.nameWithoutExtension}.sha256")
}