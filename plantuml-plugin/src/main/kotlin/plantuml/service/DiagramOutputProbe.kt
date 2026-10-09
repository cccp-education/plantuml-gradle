package plantuml.service

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult

/**
 * Verdict of a [DiagramOutputProbe] check (PLT-CR3-2).
 *
 * Sealed hierarchy — a probe either accepted the produced image ([Rendered]) or
 * rejected it with a precise reason.
 */
sealed class DiagramOutputVerdict {
    /** The artifact is a real, non-empty image and its syntax still parses. */
    data class Rendered(val byteLength: Long) : DiagramOutputVerdict()

    /** The artifact is empty — nothing was rendered. */
    data object EmptyImage : DiagramOutputVerdict()

    /**
     * The artifact is not an image at all — e.g. `PlantumlService.generateImage`
     * wrote its textual fallback (source + error) into the `.png` on failure.
     * This is the silent-degradation vector of a "white" diagram.
     */
    data class NotAnImage(val message: String) : DiagramOutputVerdict()

    /** The image exists but the PlantUML source no longer parses. */
    data class InvalidSyntax(val message: String) : DiagramOutputVerdict()
}

/**
 * Probe of a produced PlantUML diagram image (PLT-CR3-2, EPIC PLT-CR3).
 *
 * The plugin generates a diagram from LLM output, then `PlantumlService.generateImage`
 * writes the artifact — but on failure it silently writes the **source text** into
 * the `.png`, producing a non-empty file that is not an image. Nothing verified the
 * produced artifact was a real image whose source still parses (the "generated a PNG"
 * test only checked `length() > 0`).
 *
 * This probe is that missing guard: it re-checks the artifact is a real image
 * (magic bytes) and, when an N0 [PlantUmlSyntaxValidator] is supplied, that the
 * source still parses. Pure — it operates on bytes and the port, no I/O.
 */
object DiagramOutputProbe {

    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /**
     * Probes an image artifact.
     *
     * @param bytes the produced artifact bytes
     * @param plantumlCode the PlantUML source that produced it
     * @param validator optional N0 syntax validator (D5 round-trip); when absent,
     *   only the image check (non-empty + magic bytes) is applied
     */
    fun probe(
        bytes: ByteArray,
        plantumlCode: String,
        validator: PlantUmlSyntaxValidator? = null,
    ): DiagramOutputVerdict {
        if (bytes.isEmpty()) return DiagramOutputVerdict.EmptyImage
        if (!looksLikeImage(bytes)) {
            return DiagramOutputVerdict.NotAnImage(
                "artifact is not an image (${bytes.size} bytes, no PNG/JPEG/SVG header) — " +
                    "PlantUML likely failed and wrote its textual fallback"
            )
        }
        if (validator != null && validator.validate(plantumlCode) is SyntaxValidationResult.Invalid) {
            return DiagramOutputVerdict.InvalidSyntax("image produced but PlantUML source no longer parses")
        }
        return DiagramOutputVerdict.Rendered(bytes.size.toLong())
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (startsWith(bytes, PNG_MAGIC)) return true
        // JPEG
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            return true
        }
        // SVG (XML text starting with `<?xml` or `<svg`)
        val head = String(bytes.copyOf(minOf(bytes.size, 16)), Charsets.UTF_8).trimStart()
        return head.startsWith("<?xml") || head.startsWith("<svg")
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean {
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) {
            if (bytes[i] != prefix[i]) return false
        }
        return true
    }
}
