package plantuml.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * DIA-06 — deterministic diagram subtype detection.
 */
class DiagramSubtypeDetectorTest {

    @Test
    fun `detects a C4 context diagram`() {
        assertThat(DiagramSubtypeDetector.detect("C4 context diagram for the API gateway"))
            .isEqualTo(DiagramSubtypeDetector.Subtype.CONTEXT)
    }

    @Test
    fun `detects a container diagram`() {
        assertThat(DiagramSubtypeDetector.detect("C4 container diagram of the platform"))
            .isEqualTo(DiagramSubtypeDetector.Subtype.CONTAINER)
    }

    @Test
    fun `detects a sequence diagram`() {
        assertThat(DiagramSubtypeDetector.detect("Sequence diagram of the login flow"))
            .isEqualTo(DiagramSubtypeDetector.Subtype.SEQUENCE)
    }

    @Test
    fun `detects an activity diagram`() {
        assertThat(DiagramSubtypeDetector.detect("Activity for the order approval"))
            .isEqualTo(DiagramSubtypeDetector.Subtype.ACTIVITY)
    }

    @Test
    fun `detects a UML class diagram`() {
        assertThat(DiagramSubtypeDetector.detect("UML class diagram of the domain model"))
            .isEqualTo(DiagramSubtypeDetector.Subtype.CLASS)
    }

    @Test
    fun `unknown description yields UNKNOWN and no hint`() {
        assertThat(DiagramSubtypeDetector.detect("just draw something")).isEqualTo(DiagramSubtypeDetector.Subtype.UNKNOWN)
        assertThat(DiagramSubtypeDetector.hint("just draw something")).isNull()
    }

    @Test
    fun `known subtype yields a hint`() {
        assertThat(DiagramSubtypeDetector.hint("Sequence diagram of the login flow"))
            .isEqualTo("Prefer a sequence diagram.")
    }
}
