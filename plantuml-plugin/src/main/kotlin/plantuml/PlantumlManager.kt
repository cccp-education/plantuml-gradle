package plantuml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import contracts.context.ContextChannel
import org.gradle.api.Project
import plantuml.PlantumlPlugin.PlantumlExtension
import plantuml.tasks.CollectPlantumlAugmentedContextTask
import plantuml.tasks.GeneratePlantumlDiagramsTask
import plantuml.tasks.CollectPlantumlIndexTask
import plantuml.tasks.ValidatePlantumlSyntaxTask
import plantuml.tasks.GenerateDiagramDocsTask
import plantuml.tasks.GenerateKnowledgeGraphDiagramTask
import plantuml.tasks.PreflightPlantumlDescriptionTask
import java.io.File

/**
 * Central manager for the PlantUML Gradle plugin.
 *
 * Coordinates all plugin functionality by delegating responsibilities to
 * nested objects organized by concern:
 * - [Configuration] — Configuration loading and management
 * - [Tasks] — Task registration and configuration
 * - [Extensions] — Extension point management
 */
object PlantumlManager {

    /**
     * Manages plugin configuration.
     */
    object Configuration {
        private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
            .registerKotlinModule()

        const val CONFIG_FILE_NAME = "plantuml-context.yml"

        fun load(project: Project, cliParams: Map<String, Any?> = emptyMap()): PlantumlConfig {
            val extension = project.extensions.findByType(PlantumlExtension::class.java)
            val configPath = extension?.configPath?.orNull

            val configFile = if (configPath != null) File(project.projectDir, configPath)
            else File(project.projectDir, CONFIG_FILE_NAME)

            val yamlConfig = if (!configFile.exists() || configFile.length() == 0L) {
                project.logger.lifecycle(PlantumlMessages.get("manager.no_config"))
                PlantumlConfig()
            } else {
                try {
                    val config = ConfigLoader.load(configFile)
                    project.logger.lifecycle(PlantumlMessages.format("manager.config_loaded", "en", configFile.absolutePath))
                    config
                } catch (e: com.fasterxml.jackson.core.JsonParseException) {
                    val lineNum = e.location?.lineNr ?: -1
                    val colNum = e.location?.columnNr ?: -1
                    val locationMsg = if (lineNum > 0 && colNum > 0)
                        " (line $lineNum, column $colNum)" else ""
                    val errorMessage = PlantumlMessages.format(
                        "manager.invalid_yaml", "en", configFile.absolutePath, e.message ?: "", locationMsg
                    )
                    project.logger.error(PlantumlMessages.format("manager.error_prefix", "en", errorMessage))
                    throw IllegalStateException(errorMessage)
                } catch (e: com.fasterxml.jackson.databind.exc.MismatchedInputException) {
                    val lineNum = e.location?.lineNr ?: -1
                    val colNum = e.location?.columnNr ?: -1
                    val locationMsg = if (lineNum > 0 && colNum > 0)
                        " (line $lineNum, column $colNum)" else ""
                    val errorMessage = PlantumlMessages.format(
                        "manager.invalid_syntax", "en", configFile.absolutePath, e.message ?: "", locationMsg
                    )
                    project.logger.error(PlantumlMessages.format("manager.error_prefix", "en", errorMessage))
                    throw IllegalStateException(errorMessage)
                } catch (e: Exception) {
                    val errorMessage = PlantumlMessages.format(
                        "manager.parse_failed", "en", configFile.absolutePath, e.message ?: ""
                    )
                    project.logger.error(PlantumlMessages.format("manager.error_prefix", "en", errorMessage))
                    throw IllegalStateException(errorMessage)
                }
            }

            return ConfigMerger.merge(project, yamlConfig, cliParams)
                .also { project.logger.lifecycle(PlantumlMessages.get("manager.config_merged")) }
        }
    }

    /**
     * Registers all PlantUML-specific Gradle tasks.
     */
    object Tasks {
        fun registerTasks(project: Project) {
            project.tasks.register("generatePlantumlDiagrams", GeneratePlantumlDiagramsTask::class.java)
            project.tasks.register("validatePlantumlSyntax", ValidatePlantumlSyntaxTask::class.java)
            project.tasks.register("collectPlantumlIndex", CollectPlantumlIndexTask::class.java)
            project.tasks.register("generateDiagramDocs", GenerateDiagramDocsTask::class.java)
            project.tasks.register("generateKnowledgeGraphDiagram", GenerateKnowledgeGraphDiagramTask::class.java)
            project.tasks.register("preflightPlantumlDescription", PreflightPlantumlDescriptionTask::class.java)
            registerCollectAugmentedContextTask(project)
        }

        /**
         * Registers `collectPlantumlAugmentedContext` (EPIC PLT-CONTEXT, CTX-03).
         *
         * EAGER governance files come from the consumer project root; the
         * RAG/Graphify/Docs channels are injected via `-Pcontext.*` properties
         * (CTX-04 will consume the rendered artefact). Outputs live under
         * `build/plantuml/` — declared Gradle-native, so the task is UP-TO-DATE
         * when nothing changed (law of economy of ink).
         */
        private fun registerCollectAugmentedContextTask(project: Project) {
            project.tasks.register(
                "collectPlantumlAugmentedContext",
                CollectPlantumlAugmentedContextTask::class.java,
            ) { task ->
                val lang = PlantumlManager.resolveLanguage(project)
                task.group = PlantumlMessages.get("task.context.group", lang)
                task.description = PlantumlMessages.get("task.context.description", lang)
                // Tolerant EAGER input (CTX-04): only existing governance files are
                // declared, so the task (now a dependency of generation) never fails
                // validation on a project that has no `.agents/INDEX.adoc`.
                task.eagerFiles.from(
                    project.provider {
                        listOf(
                            project.layout.projectDirectory.file(".agents/INDEX.adoc").asFile,
                            project.layout.projectDirectory.file("PROMPT_REPRISE.adoc").asFile,
                            project.layout.projectDirectory.file("AGENT.adoc").asFile,
                        ).filter { it.exists() }
                    },
                )
                task.ragContent.set(project.findProperty("context.ragContent")?.toString().orEmpty())
                task.graphifyContent.set(project.findProperty("context.graphifyContent")?.toString().orEmpty())
                task.docsContent.set(project.findProperty("context.docsContent")?.toString().orEmpty())
                task.tokenBudget.set(
                    project.findProperty("context.tokenBudget")?.toString()?.toIntOrNull()
                        ?: ContextChannel.DEFAULT_TOKEN_BUDGET,
                )
                task.outputFile.set(project.layout.buildDirectory.file("plantuml/augmented-context.txt"))
                task.provenanceFile.set(project.layout.buildDirectory.file("plantuml/context-provenance.json"))
            }
            // CTX-04 — generation consumes the collected artefact: the collector runs
            // first so `generatePlantumlDiagrams` reads a fresh augmented context.
            project.tasks.named("generatePlantumlDiagrams") { generate ->
                generate.dependsOn("collectPlantumlAugmentedContext")
            }
        }
    }

    fun resolveLanguage(project: Project): String {
        val config = Configuration.load(project)
        return config.language
    }

    /**
     * Manages plugin extension points.
     */
    object Extensions {
        fun configureExtensions(project: Project) {
            // Configure any additional extensions needed
        }
    }
}