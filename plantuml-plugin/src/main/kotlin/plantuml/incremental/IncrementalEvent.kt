package plantuml.incremental

/**
 * Base class for incremental processing domain events.
 *
 * @property eventType Unique event type identifier
 */
sealed class IncrementalEvent {
    abstract val eventType: String
}

/**
 * Emitted when a prompt is skipped because its checksum has not changed.
 *
 * @property promptName Name of the skipped prompt (without extension)
 */
data class PromptSkipped(
    val promptName: String
) : IncrementalEvent() {
    override val eventType: String = "PROMPT_SKIPPED"
}

/**
 * Emitted when a prompt has been processed and its checksum stored.
 *
 * @property promptName Name of the processed prompt (without extension)
 * @property iterations Number of LLM correction iterations used
 */
data class PromptProcessed(
    val promptName: String,
    val iterations: Int
) : IncrementalEvent() {
    override val eventType: String = "PROMPT_PROCESSED"
}

/**
 * Emitted when orphaned output files are cleaned up.
 *
 * @property removedCount Number of files removed
 */
data class OutputsCleaned(
    val removedCount: Int
) : IncrementalEvent() {
    override val eventType: String = "OUTPUTS_CLEANED"
}