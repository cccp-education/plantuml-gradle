package plantuml.incremental

/**
 * Configuration for the incremental processing domain.
 *
 * @property checksumsDir Directory for storing prompt checksums (relative to build dir)
 * @property auditLog Path to the incremental audit log file (relative to build dir)
 * @property auditEnabled Whether audit logging is active
 */
data class IncrementalConfig(
    val checksumsDir: String = "build/plantuml-plugin/checksums",
    val auditLog: String = "build/plantuml-plugin/incremental-audit.log",
    val auditEnabled: Boolean = true
)