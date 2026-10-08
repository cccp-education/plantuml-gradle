package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import plantuml.toolchain.PlantUmlToolchain
import java.io.File
import kotlin.text.Charsets.UTF_8

/**
 * Steps for `24_toolchain_governance.feature` (EPIC PLT-DIAGRAM-OWNERSHIP US-5).
 *
 * Prefix-free step names are unique to this feature (anti-glue collision rule
 * S-088 — the shared `plantuml.scenarios` glue package).
 */
class ToolchainSteps {

    private val pluginDir = File(System.getProperty("user.dir")).absoluteFile

    @Given("the governed PlantUML toolchain")
    fun theGovernedToolchain() {
        // The reference is a pure object — nothing to arrange.
    }

    @Then("the reference toolchain versions should not be blank")
    fun referenceIsNotBlank() {
        val reference = PlantUmlToolchain.reference
        assertThat(reference.engineVersion).isNotBlank
        assertThat(reference.diagramVersion).isNotBlank
        assertThat(reference.diagramPlantumlVersion).isNotBlank
    }

    @Then("the local catalog should match the reference toolchain")
    fun localCatalogMatches() {
        val toml = pluginDir.resolve("gradle/libs.versions.toml").readText(UTF_8)
        val reference = PlantUmlToolchain.reference
        assertThat(versionOf(toml, "plantuml-engine")).isEqualTo(reference.engineVersion)
        assertThat(versionOf(toml, "asciidoctorj-diagram")).isEqualTo(reference.diagramVersion)
        assertThat(versionOf(toml, "asciidoctorj-diagram-plantuml"))
            .isEqualTo(reference.diagramPlantumlVersion)
    }

    private fun versionOf(toml: String, key: String): String =
        toml
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .first { it.startsWith("$key =") }
            .substringAfter("\"")
            .substringBefore("\"")
}
