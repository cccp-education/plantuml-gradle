package plantuml

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.text.Charsets.UTF_8

/**
 * MEM-CAT-ROLLOUT-3 (S-029, cross-borough MEMPHIS) — publication hygiene guard.
 *
 * D3: the plugin self version is derived from the published workspace catalog
 * (`ws.versions.plantuml.plugin.get()`) — never a duplicated literal.
 * D4: the borough pins the catalog once in settings.gradle.kts.
 * D5 hygiene: the local toml self version and the ws catalog version must agree,
 * and the platform pin must match the ws catalog BOM version.
 */
class PlantumlPluginPublicationTest {
    private val pluginDir = File(System.getProperty("user.dir")).absoluteFile

    @Test
    fun `plugin version matches root consumer catalog version`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val versionLine =
            buildScript
                .lineSequence()
                .first { it.trimStart().startsWith("version =") }

        // MEM-CAT-ROLLOUT-3 (D3) — self version derived from the published workspace catalog.
        assertThat(versionLine)
            .withFailMessage("build.gradle.kts version must derive from the published workspace catalog (ws.versions.plantuml.plugin)")
            .contains("ws.versions.plantuml.plugin.get()")

        // Hygiene (D5): local toml self version must match the ws catalog version —
        // the ws catalog (workspace-bom repo) is the cross-borough source of truth.
        val pluginCatalogVersion = plantumlVersionFrom(pluginDir.resolve("gradle/libs.versions.toml").readText(UTF_8))
        val wsCatalogVersion = publishedCatalogPlantumlVersion()

        assertThat(pluginCatalogVersion)
            .withFailMessage("plugin catalog plantuml-plugin version ($pluginCatalogVersion) must match ws catalog plantuml-plugin version ($wsCatalogVersion)")
            .isEqualTo(wsCatalogVersion)
    }

    @Test
    fun `workspace bom platform pin matches ws catalog bom version`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val wsBomVersion = publishedCatalogBomVersion()

        assertThat(buildScript)
            .withFailMessage("workspace-bom platform pin must use the ws catalog BOM version ($wsBomVersion)")
            .contains("""platform("education.cccp:workspace-bom:$wsBomVersion")""")
    }

    /**
     * The `ws` catalog `plantuml-plugin` version, **injected by Gradle** at test
     * launch (`systemProperty("plantuml.publishedCatalog.plantumlVersion", …)`).
     *
     * D5-RACE (S-222, mirror graphify S-029 / document): never read the sibling repo
     * working tree — racy between parallel sessions and absent in CI (this repo is
     * checked out alone). The injected value is the true published-catalog source of
     * truth, resolved by Gradle.
     */
    private fun publishedCatalogPlantumlVersion(): String =
        System.getProperty("plantuml.publishedCatalog.plantumlVersion")
            ?: error("plantuml.publishedCatalog.plantumlVersion non injecté par Gradle (D5-RACE)")

    /** The `ws` catalog BOM version, injected by Gradle (see above). */
    private fun publishedCatalogBomVersion(): String =
        System.getProperty("plantuml.publishedCatalog.bomVersion")
            ?: error("plantuml.publishedCatalog.bomVersion non injecté par Gradle (D5-RACE)")

    private fun plantumlVersionFrom(content: String): String =
        content
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .first { it.startsWith("plantuml-plugin =") || it.startsWith("plantuml =") }
            .substringAfter("\"")
            .substringBefore("\"")

    @Test
    fun `plugin group and id are stable for publication`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val pluginId =
            pluginDir
                .resolve("gradle/libs.versions.toml")
                .readText(UTF_8)
                .lineSequence()
                .filter { it.contains("id = \"education.cccp.plantuml\"") }
                .first()
                .substringAfter("id = \"")
                .substringBefore("\"")

        assertThat(buildScript).contains("group = \"education.cccp\"")
        assertThat(pluginId).isEqualTo("education.cccp.plantuml")
    }
}