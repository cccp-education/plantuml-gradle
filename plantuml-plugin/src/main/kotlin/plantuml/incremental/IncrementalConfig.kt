package plantuml.incremental

data class IncrementalConfig(
    val checksumsDir: String = "build/plantuml-plugin/checksums",
    val auditLog: String = "build/plantuml-plugin/incremental-audit.log",
    val auditEnabled: Boolean = true
)