package plantuml.incremental

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * File-based audit logger for incremental processing events.
 *
 * Implements [IncrementalEventListener] to receive events and writes
 * timestamped log lines to the configured log file.
 *
 * @property logFile The file where audit entries are appended
 */
class IncrementalAuditLogger(private val logFile: File) : IncrementalEventListener {

    init {
        if (!logFile.exists()) {
            logFile.parentFile?.mkdirs()
            logFile.createNewFile()
        }
    }

    override fun onEvent(event: IncrementalEvent) {
        log(event)
    }

    /**
     * Logs an incremental processing event to the audit file.
     *
     * @param event The event to log
     */
    fun log(event: IncrementalEvent) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val line = when (event) {
            is PromptSkipped -> "$timestamp | ${event.eventType} | prompt=${event.promptName}"
            is PromptProcessed -> "$timestamp | ${event.eventType} | prompt=${event.promptName} | iterations=${event.iterations}"
            is OutputsCleaned -> "$timestamp | ${event.eventType} | removed=${event.removedCount}"
        }
        logFile.appendText(line + System.lineSeparator())
    }
}