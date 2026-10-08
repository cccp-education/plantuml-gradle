package plantuml.toolchain

/**
 * Reference versions of the PlantUML toolchain, governed by the plantuml borough
 * (EPIC PLT-DIAGRAM-OWNERSHIP US-5, decision D6).
 *
 * The rendering of a `[plantuml]` block is done by asciidoctor-diagram inside the
 * bake, but the version of that chain had **three divergent sources** (bakery
 * hardcode, bakery local toml, MEMPHIS BOM) — the architectural cause of the
 * CHE-DIAGRAM white-SVG defect (D1). This value object makes the plantuml
 * borough the **single source of truth** for the toolchain versions; consumers
 * (bakery, runner) align on it and a guard proves the local catalog cannot
 * drift from it.
 *
 * @property engineVersion `net.sourceforge.plantuml:plantuml` engine version
 * @property diagramVersion `org.asciidoctor:asciidoctorj-diagram` version
 * @property diagramPlantumlVersion `org.asciidoctor:asciidoctorj-diagram-plantuml`
 *   version (bundles the PlantUML used by the bake)
 */
data class PlantUmlToolchainReference(
    val engineVersion: String,
    val diagramVersion: String,
    val diagramPlantumlVersion: String,
) {
    init {
        require(engineVersion.isNotBlank()) { "engineVersion must not be blank" }
        require(diagramVersion.isNotBlank()) { "diagramVersion must not be blank" }
        require(diagramPlantumlVersion.isNotBlank()) { "diagramPlantumlVersion must not be blank" }
    }
}

/**
 * The PlantUML toolchain versions of reference (EPIC PLT-DIAGRAM-OWNERSHIP US-5).
 *
 * This is the value the plantuml borough **exposes** to the rest of the
 * ecosystem (D6). The local `gradle/libs.versions.toml` MUST match it — proved by
 * `PlantUmlToolchainTest` (anti-drift guard).
 */
object PlantUmlToolchain {
    val reference: PlantUmlToolchainReference =
        PlantUmlToolchainReference(
            engineVersion = "1.2026.0",
            diagramVersion = "3.2.0",
            diagramPlantumlVersion = "1.2025.3",
        )
}
