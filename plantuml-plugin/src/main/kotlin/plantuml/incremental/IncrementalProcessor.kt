package plantuml.incremental

import java.io.File

enum class ProcessingDecision {
    SKIP,
    PROCESS
}

class IncrementalProcessor(checksumsDir: File) {

    private val checksumStore = ChecksumStore(checksumsDir)
    private val eventListeners = mutableListOf<(IncrementalEvent) -> Unit>()

    fun onEvent(listener: (IncrementalEvent) -> Unit) {
        eventListeners.add(listener)
    }

    private fun emit(event: IncrementalEvent) {
        eventListeners.forEach { it(event) }
    }

    fun decideProcessing(promptFile: File, forceReprocess: Boolean): ProcessingDecision {
        if (forceReprocess) return ProcessingDecision.PROCESS
        return if (checksumStore.hasPromptChanged(promptFile)) {
            ProcessingDecision.PROCESS
        } else {
            emit(PromptSkipped(promptFile.nameWithoutExtension))
            ProcessingDecision.SKIP
        }
    }

    fun markAsProcessed(promptFile: File, iterations: Int = 0) {
        checksumStore.storeChecksum(promptFile)
        emit(PromptProcessed(promptFile.nameWithoutExtension, iterations))
    }

    fun cleanupOrphanedOutputs(promptsDir: File, diagramsDir: File, imagesDir: File) {
        val promptNames = promptsDir.listFiles { it.extension == "prompt" }
            ?.map { sanitizeFileName(it.nameWithoutExtension) }
            ?: emptyList()

        val removedCount = cleanupOrphanedDir(diagramsDir, promptNames, setOf("yml", "puml")) +
            cleanupOrphanedDir(imagesDir, promptNames, setOf("png"))

        if (removedCount > 0) {
            emit(OutputsCleaned(removedCount))
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")

    private fun cleanupOrphanedDir(dir: File, promptNames: List<String>, extensions: Set<String>): Int {
        if (!dir.exists()) return 0
        var count = 0
        dir.listFiles()?.forEach { file ->
            if (file.extension in extensions && file.nameWithoutExtension !in promptNames) {
                file.delete()
                count++
            }
        }
        return count
    }
}