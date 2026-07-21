package plantuml.incremental

/**
 * Functional interface for listening to incremental processing events.
 *
 * Implementations receive [IncrementalEvent] instances (e.g., [PromptSkipped],
 * [PromptProcessed], [OutputsCleaned]) as they occur.
 */
fun interface IncrementalEventListener {
    fun onEvent(event: IncrementalEvent)
}