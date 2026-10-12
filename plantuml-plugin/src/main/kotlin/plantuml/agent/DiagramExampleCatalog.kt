package plantuml.agent

import plantuml.agent.DiagramSubtypeDetector.Subtype

/**
 * Catalogue of canonical PlantUML examples, one per diagram subtype (EPIC PLT-AGENT, DIA-05).
 *
 * Ported from `docToolChain/diag-agent`'s few-shot examples library: a *static*
 * canonical snippet per subtype complements the retrieved RAG corpus. The snippets
 * are classpath resources under `plantuml/examples` (one `.puml` per subtype), so
 * the object is I/O-free beyond a bundled resource read.
 */
object DiagramExampleCatalog {

    private const val DIR = "/plantuml/examples"

    /** Resource file name for a subtype, or `null` when there is no example. */
    fun resourceName(subtype: Subtype): String? = when (subtype) {
        Subtype.CONTEXT -> "context.puml"
        Subtype.CONTAINER -> "container.puml"
        Subtype.COMPONENT -> "component.puml"
        Subtype.SEQUENCE -> "sequence.puml"
        Subtype.ACTIVITY -> "activity.puml"
        Subtype.CLASS -> "class.puml"
        Subtype.UNKNOWN -> null
    }

    /** Returns the canonical example for [subtype], or `null` when none applies. */
    fun selectExample(subtype: Subtype): String? {
        val name = resourceName(subtype) ?: return null
        return DiagramExampleCatalog::class.java
            .getResourceAsStream("$DIR/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
    }
}
