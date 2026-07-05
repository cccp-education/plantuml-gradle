package plantuml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.Project
import plantuml.PlantumlPlugin.PlantumlExtension
import plantuml.tasks.GeneratePlantumlDiagramsTask
import plantuml.tasks.CollectPlantumlIndexTask
import plantuml.tasks.ValidatePlantumlSyntaxTask
import plantuml.tasks.GenerateDiagramDocsTask
import plantuml.tasks.GenerateKnowledgeGraphDiagramTask
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