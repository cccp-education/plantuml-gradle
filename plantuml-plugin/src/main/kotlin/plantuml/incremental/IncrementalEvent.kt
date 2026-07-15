package plantuml.incremental

sealed class IncrementalEvent {
    abstract val eventType: String
}

data class PromptSkipped(
    val promptName: String
) : IncrementalEvent() {
    override val eventType: String = "PROMPT_SKIPPED"
}

data class PromptProcessed(
    val promptName: String,
    val iterations: Int
) : IncrementalEvent() {
    override val eventType: String = "PROMPT_PROCESSED"
}

data class OutputsCleaned(
    val removedCount: Int
) : IncrementalEvent() {
    override val eventType: String = "OUTPUTS_CLEANED"
}