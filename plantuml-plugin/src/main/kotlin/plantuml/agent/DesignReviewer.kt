package plantuml.agent

/**
 * Port for a vision-based design reviewer (EPIC PLT-AGENT, DIA-01).
 *
 * The reviewer receives the rendered image (PNG bytes) and the diagram source, and
 * returns the vision model's raw feedback. Implementations live outside the domain
 * ([plantuml.service.VisionDesignReviewer]); a `null` reviewer means the design loop
 * is disabled, so the default generation path is unchanged.
 */
fun interface DesignReviewer {
    fun review(pngBytes: ByteArray, source: String): String
}
