package plantuml.toolchain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.text.Charsets.UTF_8

/**
 * Anti-drift guard for the governed PlantUML toolchain (EPIC
 * PLT-DIAGRAM-OWNERSHIP US-5, decision D6).
 *
 * The reference versions ([PlantUmlToolchain]) are the single source of truth
 * exposed by the plantuml borough. This guard proves the local catalog cannot
 * drift from it (the same class of bug that produced D1 — white SVG — where
 * bakery/CI/local disagreed on the resolved version).
 */
class PlantUmlToolchainTest {

    private val pluginDir = File(System.getProperty("user.dir")).absoluteFile

    @Test
    fun `reference versions are exposed and non blank`() {
        val reference = PlantUmlToolchain.reference
        assertThat(reference.engineVersion).isNotBlank
        assertThat(reference.diagramVersion).isNotBlank
        assertThat(reference.diagramPlantumlVersion).isNotBlank
    }

    @Test
    fun `reference matches the local catalog — anti drift`() {
        val toml = pluginDir.resolve("gradle/libs.versions.toml").readText(UTF_8)

        assertThat(versionOf(toml, "plantuml-engine"))
            .withFailMessage("plantuml-engine version must match the governed toolchain reference")
            .isEqualTo(PlantUmlToolchain.reference.engineVersion)
        assertThat(versionOf(toml, "asciidoctorj-diagram"))
            .withFailMessage("asciidoctorj-diagram must match the governed toolchain reference")
            .isEqualTo(PlantUmlToolchain.reference.diagramVersion)
        assertThat(versionOf(toml, "asciidoctorj-diagram-plantuml"))
            .withFailMessage("asciidoctorj-diagram-plantuml must match the governed toolchain reference")
            .isEqualTo(PlantUmlToolchain.reference.diagramPlantumlVersion)
    }

    @Test
    fun `a blank reference version is rejected`() {
        assertThatThrownBy {
            PlantUmlToolchainReference(engineVersion = " ", diagramVersion = "1", diagramPlantumlVersion = "1")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun versionOf(toml: String, key: String): String =
        toml
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .first { it.startsWith("$key =") }
            .substringAfter("\"")
            .substringBefore("\"")
}
