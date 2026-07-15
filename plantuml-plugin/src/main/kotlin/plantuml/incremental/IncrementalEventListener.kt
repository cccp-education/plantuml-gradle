package plantuml.incremental

fun interface IncrementalEventListener {
    fun onEvent(event: IncrementalEvent)
}