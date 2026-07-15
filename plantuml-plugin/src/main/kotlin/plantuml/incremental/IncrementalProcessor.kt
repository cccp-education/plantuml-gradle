package plantuml.incremental

import java.io.File

enum class ProcessingDecision {
    SKIP,
    PROCESS
}

class IncrementalProcessor(checksumsDir: File) {

    private val checksumStore = ChecksumStore(checksumsDir)

    fun decideProcessing(promptFile: File, forceReprocess: Boolean): ProcessingDecision {
        if (forceReprocess) return ProcessingDecision.PROCESS
        return if (checksumStore.hasPromptChanged(promptFile)) {
            ProcessingDecision.PROCESS
        } else {
            ProcessingDecision.SKIP
        }
    }

    fun markAsProcessed(promptFile: File) {
        checksumStore.storeChecksum(promptFile)
    }

    fun cleanupOrphanedOutputs(promptsDir: File, diagramsDir: File, imagesDir: File) {
        val promptNames = promptsDir.listFiles { it.extension == "prompt" }
            ?.map { sanitizeFileName(it.nameWithoutExtension) }
            ?: emptyList()

        cleanupOrphanedDir(diagramsDir, promptNames, setOf("yml", "puml"))
        cleanupOrphanedDir(imagesDir, promptNames, setOf("png"))
    }

    private fun sanitizeFileName(name: String): String =
        name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")

    private fun cleanupOrphanedDir(dir: File, promptNames: List<String>, extensions: Set<String>) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.extension in extensions && file.nameWithoutExtension !in promptNames) {
                file.delete()
            }
        }
    }
}