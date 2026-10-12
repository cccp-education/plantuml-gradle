package plantuml.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import plantuml.agent.DiagramSubtypeDetector.Subtype

/**
 * DIA-05 — canonical example catalogue.
 */
class DiagramExampleCatalogTest {

    @Test
    fun `each known subtype has a canonical example`() {
        Subtype.entries.filter { it != Subtype.UNKNOWN }.forEach { subtype ->
            val example = DiagramExampleCatalog.selectExample(subtype)
            assertThat(example).describedAs("example for $subtype").isNotNull
            assertThat(example).contains("@startuml")
            assertThat(example).contains("@enduml")
        }
    }

    @Test
    fun `unknown subtype has no example`() {
        assertThat(DiagramExampleCatalog.selectExample(Subtype.UNKNOWN)).isNull()
        assertThat(DiagramExampleCatalog.resourceName(Subtype.UNKNOWN)).isNull()
    }
}
