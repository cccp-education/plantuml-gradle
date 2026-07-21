package plantuml.incremental

import java.io.File

/**
 * Decision returned by [IncrementalProcessor.decideProcessing].
 */
enum class ProcessingDecision {
    /** Prompt has not changed; skip processing. */
    SKIP,
    /** Prompt is new or has changed; process it. */
    PROCESS
}

/**
 * Core incremental processing engine for PlantUML prompts.
 *
 * Uses [ChecksumStore] to detect prompt changes, emits domain events
 * via registered listeners, and manages orphaned output cleanup.
 *
 * @param checksumsDir Directory for checksum storage
 */
class IncrementalProcessor(checksumsDir: File) {

    private val checksumStore = ChecksumStore(checksumsDir)
    private val eventListeners = mutableListOf<(IncrementalEvent) -> Unit>()

    /**
     * Registers an event listener for incremental processing events.
     *
     * @param listener Lambda receiving [IncrementalEvent] instances
     */
    fun onEvent(listener: (IncrementalEvent) -> Unit) {
        eventListeners.add(listener)
    }

    private fun emit(event: IncrementalEvent) {
        eventListeners.forEach { it(event) }
    }

    /**
     * Decides whether a prompt file should be processed or skipped.
     *
     * @param promptFile The prompt file to evaluate
     * @param forceReprocess If true, always returns [ProcessingDecision.PROCESS]
     * @return [ProcessingDecision.SKIP] if unchanged, [ProcessingDecision.PROCESS] otherwise
     */
    fun decideProcessing(promptFile: File, forceReprocess: Boolean): ProcessingDecision {
        if (forceReprocess) return ProcessingDecision.PROCESS
        return if (checksumStore.hasPromptChanged(promptFile)) {
            ProcessingDecision.PROCESS
        } else {
            emit(PromptSkipped(promptFile.nameWithoutExtension))
            ProcessingDecision.SKIP
        }
    }

    /**
     * Marks a prompt as processed by storing its current checksum.
     *
     * @param promptFile The processed prompt file
     * @param iterations Number of LLM correction iterations used
     */
    fun markAsProcessed(promptFile: File, iterations: Int = 0) {
        checksumStore.storeChecksum(promptFile)
        emit(PromptProcessed(promptFile.nameWithoutExtension, iterations))
    }

    /**
     * Checks whether a generated `.puml` file should be reindexed in RAG.
     *
     * A `.puml` file needs reindexing if its source prompt has changed
     * or no longer exists.
     *
     * @param pumlFile The generated PlantUML file
     * @param promptsDir Directory containing source `.prompt` files
     * @return true if the diagram should be reindexed
     */
    fun shouldReindexPuml(pumlFile: File, promptsDir: File): Boolean {
        val promptName = pumlFile.nameWithoutExtension
        val promptFile = File(promptsDir, "$promptName.prompt")
        if (!promptFile.exists()) return true
        return checksumStore.hasPromptChanged(promptFile)
    }

    /**
     * Removes orphaned output files whose source prompts no longer exist.
     *
     * @param promptsDir Directory containing source `.prompt` files
     * @param diagramsDir Directory containing generated `.puml` and `.yml` files
     * @param imagesDir Directory containing generated `.png` images
     */
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