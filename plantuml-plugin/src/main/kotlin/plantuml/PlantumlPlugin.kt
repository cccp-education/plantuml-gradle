package plantuml

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Main entry point for the PlantUML Gradle plugin.
 *
 * This plugin processes PlantUML diagrams using AI assistance with LangChain4j.
 * It watches prompt files, generates diagrams through LLM interaction, validates
 * syntax, and produces images from valid diagrams.
 */
class PlantumlPlugin : Plugin<Project> {

    /** Applies the plugin by registering tasks and configuring extensions. */
    override fun apply(project: Project) {
        with(project) {
            extensions.create("plantuml", PlantumlExtension::class.java)

            PlantumlManager.Tasks.registerTasks(this)

            project.tasks.register("docs") {
                it.group = "info"
                it.description = "Full documentation pipeline: knowledge graph + generate prompts + process + validate"
                it.dependsOn("generateKnowledgeGraphDiagram")
                it.dependsOn("generateDiagramDocs")
                it.dependsOn("generatePlantumlDiagrams")
                it.finalizedBy("validatePlantumlSyntax")
            }
        }
    }

    /**
     * DSL extension for the plantuml plugin.
     *
     * Usage in build.gradle.kts:
     * ```
     * plantuml {
     *     configPath = file("plantuml-context.yml").absolutePath
     * }
     * ```
     */
    open class PlantumlExtension @Inject constructor(objects: ObjectFactory) {
        val configPath: Property<String> = objects.property(String::class.java)
    }
}

