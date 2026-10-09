package plantuml.service

import contracts.plantuml.PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * PLT-CR3-2 — probe of the produced diagram image (non-regression guard).
 *
 * `PlantumlService.generateImage` silently degrades: when PlantUML fails it
 * writes the source text (and the error) INTO the `.png` file — a non-empty file
 * that is not an image. Nothing verified the produced artifact was a real,
 * non-empty image whose PlantUML source still parses. This probe is the guard.
 *
 * Pure (operates on bytes + the N0 validator) — unit-testable in isolation.
 */
class DiagramOutputProbeTest {

    private val acceptAll = PlantUmlSyntaxValidator { SyntaxValidationResult.Valid }
    private val rejectAll = PlantUmlSyntaxValidator { SyntaxValidationResult.Invalid("broken", "") }

    private val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    @Test
    fun `a valid PNG whose syntax parses is Rendered`() {
        val bytes = pngMagic + ByteArray(64) { 1 }
        val verdict = DiagramOutputProbe.probe(bytes, "@startuml\nA --> B\n@enduml", acceptAll)
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.Rendered::class.java)
    }

    @Test
    fun `an empty image is EmptyImage`() {
        val verdict = DiagramOutputProbe.probe(ByteArray(0), "@startuml\n@enduml", acceptAll)
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.EmptyImage::class.java)
    }

    @Test
    fun `the text fallback is NotAnImage`() {
        // Exactly what generateImage writes on failure — a non-empty non-image.
        val fallback = "PlantUML diagram:\n\n@startuml\nA --> B\n@enduml\n\nError: boom".toByteArray()
        val verdict = DiagramOutputProbe.probe(fallback, "@startuml\nA --> B\n@enduml", acceptAll)
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.NotAnImage::class.java)
    }

    @Test
    fun `a PNG whose syntax no longer parses is InvalidSyntax`() {
        val bytes = pngMagic + ByteArray(64) { 1 }
        val verdict = DiagramOutputProbe.probe(bytes, "not plantuml", rejectAll)
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.InvalidSyntax::class.java)
    }

    @Test
    fun `an SVG image is accepted`() {
        val svg = """<?xml version="1.0" encoding="UTF-8"?><svg xmlns="http://www.w3.org/2000/svg"/>""".toByteArray()
        val verdict = DiagramOutputProbe.probe(svg, "@startuml\nA --> B\n@enduml", acceptAll)
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.Rendered::class.java)
    }

    @Test
    fun `no validator means the image check alone decides`() {
        val bytes = pngMagic + ByteArray(8)
        val verdict = DiagramOutputProbe.probe(bytes, "@startuml\n@enduml", validator = null)
        assertThat(verdict).isInstanceOf(DiagramOutputVerdict.Rendered::class.java)
    }
}
