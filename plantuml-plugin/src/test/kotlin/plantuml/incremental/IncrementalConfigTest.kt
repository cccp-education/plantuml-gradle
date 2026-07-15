package plantuml.incremental

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IncrementalConfigTest {

    @Test
    fun `should have default checksums dir`() {
        val config = IncrementalConfig()

        assertThat(config.checksumsDir).isEqualTo("build/plantuml-plugin/checksums")
    }

    @Test
    fun `should have default audit log path`() {
        val config = IncrementalConfig()

        assertThat(config.auditLog).isEqualTo("build/plantuml-plugin/incremental-audit.log")
    }

    @Test
    fun `should have audit log enabled by default`() {
        val config = IncrementalConfig()

        assertThat(config.auditEnabled).isTrue()
    }

    @Test
    fun `should allow custom checksums dir`() {
        val config = IncrementalConfig(checksumsDir = "custom/checksums")

        assertThat(config.checksumsDir).isEqualTo("custom/checksums")
    }

    @Test
    fun `should allow disabling audit log`() {
        val config = IncrementalConfig(auditEnabled = false)

        assertThat(config.auditEnabled).isFalse()
    }
}