package plantuml.repair

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * GBL-010 / G-3 — the PlantUML label integrity of a diagram block.
 *
 * CHE-DIAGRAM proved the D2 break in production: a translation introduced a
 * **real line break** inside a quoted label (`rectangle "vibe coding CLI\nOpen
 * source, free"` → two lines). PlantUML then fails (`Syntax Error?`), the block
 * degrades to a literal listing, and the variant ships no diagram. The port
 * already re-escapes the label it translates (D5); this object is the canonical,
 * testable, owner-visible rule the port delegates to, plus the source-level audit
 * that finds the frozen blocks before a bake.
 */
class PlantumlLabelIntegrityTest {

    @Test
    fun `escapeNewlines turns a real newline into the plantuml escape`() {
        assertThat(PlantumlLabelIntegrity.escapeNewlines("a\nb")).isEqualTo("a\\nb")
    }

    @Test
    fun `escapeNewlines normalises windows and mac newlines`() {
        assertThat(PlantumlLabelIntegrity.escapeNewlines("a\r\nb\rc")).isEqualTo("a\\nb\\nc")
    }

    @Test
    fun `escapeNewlines leaves an already escaped label unchanged`() {
        assertThat(PlantumlLabelIntegrity.escapeNewlines("a\\nb")).isEqualTo("a\\nb")
    }

    @Test
    fun `a quoted label spanning a real newline is broken`() {
        val source = "@startuml\nrectangle \"vibe coding CLI\nOpen source, free\" as CLI\n@enduml"

        assertThat(PlantumlLabelIntegrity.hasBrokenQuotedLabel(source)).isTrue
    }

    @Test
    fun `a quoted label using the escape is intact`() {
        val source = "@startuml\nrectangle \"vibe coding CLI\\nOpen source, free\" as CLI\n@enduml"

        assertThat(PlantumlLabelIntegrity.hasBrokenQuotedLabel(source)).isFalse
    }

    @Test
    fun `repair re-escapes the newline inside a quoted label`() {
        val source = "@startuml\nrectangle \"a\nb\" as X\n@enduml"

        val repaired = PlantumlLabelIntegrity.repair(source)

        assertThat(repaired).contains("\"a\\nb\"")
        assertThat(PlantumlLabelIntegrity.hasBrokenQuotedLabel(repaired)).isFalse
    }

    @Test
    fun `repair never touches a newline outside a quoted label`() {
        val source = "@startuml\ntitle Two\nlines\nrectangle \"a\" as X\n@enduml"

        assertThat(PlantumlLabelIntegrity.repair(source)).isEqualTo(source)
    }
}
