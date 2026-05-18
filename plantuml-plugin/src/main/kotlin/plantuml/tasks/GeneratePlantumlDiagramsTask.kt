package plantuml.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import plantuml.PlantumlConfig
import plantuml.PlantumlManager
import plantuml.service.DiagramProcessor
import plantuml.service.LlmService
import plantuml.service.PlantumlService
import java.io.File

/**
 * Gradle task: `generatePlantumlDiagrams`
 *
 * Processes `.prompt` files to generate PlantUML diagrams through AI-powered LLM interaction.
 *
 * **Workflow**:
 * 1. Scans the prompts directory for `.prompt` files
 * 2. For each prompt:
 *    - Sends to LLM for PlantUML code generation (with iterative refinement)
 *    - Validates syntax using PlantUML parser
 *    - Generates PNG image from valid code
 *    - Requests LLM validation with scoring (1-10)
 *    - Saves diagram and validation for RAG training
 *    - Deletes the processed prompt file
 *
 * **Configuration** (via CLI properties):
 * - `-Pplantuml.prompts.dir=custom/path` — Custom prompts directory
 * - `-Pplantuml.langchain4j.model=gemini` — Override LLM provider
 * - `-Pplantuml.langchain4j.maxIterations=3` — Max correction attempts
 *
 * **Usage**:
 * ```bash
 * ./gradlew generatePlantumlDiagrams
 * ```
 */
@DisableCachingByDefault(because = "PlantUML generation involves randomness and AI interaction")
abstract class GeneratePlantumlDiagramsTask : DefaultTask() {

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    init {
        group = "generate"
        description = "Process PlantUML prompts and generate diagrams with LLM assistance"
    }

    /**
     * Main task action: processes all `.prompt` files in the prompts directory.
     *
     * Loads configuration, initializes services (PlantUML, LLM, DiagramProcessor),
     * and processes each prompt file through the LLM generation pipeline.
     */
    @TaskAction
    fun processPrompts() {
        // Set project directory for DiagramProcessor to use when archiving history
        System.setProperty("plugin.project.dir", project.projectDir.absolutePath)
        
        // Set test mode flag if provided as Gradle property
        val testMode = project.findProperty("plantuml.test.mode") as? String
        if (testMode == "true") {
            System.setProperty("plantuml.test.mode", "true")
        }
        
        // Check for test mode disk space simulation
        val simulateDiskFull = System.getProperty("plantuml.test.disk.full") == "true"
        if (simulateDiskFull) {
            val message = """
                Disk space exhausted: No space left on device.
                Error: Simulated disk full condition for testing.
                Suggestions:
                - Free up disk space
                - Check available storage: df -h
                - Clean up partial outputs from build directory
                Cleanup: Removing partial files...
            """.trimIndent()
            logger.error(message)
            cleanupPartialOutputs()
            throw RuntimeException(message)
        }
        
        // Load configuration
        val config = loadConfiguration()
        val promptsDir = project.findProperty("plantuml.prompts.dir") as? String
            ?: config.input.prompts

        logger.debug("DEBUG: promptsDir from config: ${config.input.prompts}")
        logger.debug("DEBUG: promptsDir from property: {}", project.findProperty("plantuml.prompts.dir"))
        logger.debug("DEBUG: final promptsDir: $promptsDir")

        // Resolve prompts directory relative to project directory
        val promptsDirectory = project.file(promptsDir)
        logger.debug("DEBUG: promptsDirectory absolute path: ${promptsDirectory.absolutePath}")
        logger.debug("DEBUG: promptsDirectory exists: ${promptsDirectory.exists()}")

        if (!promptsDirectory.exists()) {
            logger.lifecycle("Prompts directory does not exist: ${promptsDirectory.absolutePath}")
            return
        }

        val promptFiles = promptsDirectory.listFiles { file ->
            file.extension == "prompt"
        } ?: emptyArray()

        if (promptFiles.isEmpty()) {
            logger.lifecycle("No prompt files found in: ${promptsDirectory.absolutePath}")
            return
        }

        logger.lifecycle("Processing ${promptFiles.size} prompt files...")

        // Initialize services
        val plantumlService = PlantumlService()
        val llmService = LlmService(config)
        val chatModel = llmService.createChatModel()
        logger.lifecycle("DEBUG: chatModel=$chatModel, config.output.diagrams=${config.output.diagrams}, testMode=${System.getProperty("plantuml.test.mode")}")
        val diagramProcessor = DiagramProcessor(plantumlService, chatModel, config)

        promptFiles.forEach { promptFile ->
            processSinglePrompt(promptFile, config, diagramProcessor)
        }
    }

    /**
     * Loads PlantUML configuration with support for CLI parameter overrides.
     *
     * Priority order for configuration sources:
     * 1. CLI parameters (`-Pplantuml.langchain4j.*`)
     * 2. YAML configuration file (`plantuml-context.yml`)
     * 3. Default values from [PlantumlConfig]
     *
     * Supports overriding:
     * - `plantuml.langchain4j.model` — LLM provider name
     * - `plantuml.langchain4j.ollama.modelName` — Ollama model name
     *
     * @return Merged [PlantumlConfig] with CLI overrides applied
     */
    private fun loadConfiguration(): PlantumlConfig {
        // Check if an LLM model is specified via command line
        val llmModel = project.findProperty("plantuml.langchain4j.model") as? String
        val ollamaModelName = project.findProperty("plantuml.langchain4j.ollama.modelName") as? String
        val ollamaBaseUrl = project.findProperty("plantuml.langchain4j.ollama.baseUrl") as? String
        val maxIterations = project.findProperty("plantuml.langchain4j.maxIterations") as? Int
        
        // Load base configuration
        val baseConfig = PlantumlManager.Configuration.load(project)

        // Apply CLI parameter overrides
        var config = baseConfig
        if (llmModel != null)
            config = config.copy(langchain4j = config.langchain4j.copy(model = llmModel))
        if (ollamaModelName != null)
            config = config.copy(
                langchain4j = config.langchain4j.copy(
                    ollama = config.langchain4j.ollama.copy(
                        modelName = ollamaModelName
                    )
                )
            )
        if (ollamaBaseUrl != null)
            config = config.copy(
                langchain4j = config.langchain4j.copy(
                    ollama = config.langchain4j.ollama.copy(
                        baseUrl = ollamaBaseUrl
                    )
                )
            )
        if (maxIterations != null)
            config = config.copy(
                langchain4j = config.langchain4j.copy(
                    maxIterations = maxIterations
                )
            )
        
        return config
    }

    /**
     * Processes a single `.prompt` file through the complete generation pipeline.
     *
     * **Steps**:
     * 1. Reads prompt content from file
     * 2. Calls LLM via [DiagramProcessor.processPrompt] (max [maxIterations])
     * 3. Validates PlantUML syntax
     * 4. Generates PNG image
     * 5. Requests LLM validation with scoring (if enabled in config)
     * 6. Saves diagram to RAG training directory
     * 7. Deletes the processed prompt file
     *
     * @param promptFile The `.prompt` file to process
     * @param config PlantUML configuration with output directories and settings
     * @param diagramProcessor Service for LLM-based diagram generation
     */
    private fun processSinglePrompt(
        promptFile: File,
        config: plantuml.PlantumlConfig,
        diagramProcessor: DiagramProcessor
    ) {
        logger.lifecycle("Processing prompt: ${promptFile.name}")

        // Read the prompt content
        val promptContent = promptFile.readText()

        // Process through LLM interaction loop (max 5 iterations)
        val maxIterations = project.findProperty("plantuml.langchain4j.maxIterations") as? Int
            ?: config.langchain4j.maxIterations

        val diagram = try {
            diagramProcessor.processPrompt(promptContent, maxIterations, logger)
        } catch (e: IllegalStateException) {
            logger.lifecycle("  → Error: ${e.message}")
            throw e
        }

        if (diagram != null) {
            // Validate PlantUML syntax
            logger.lifecycle("  → Validating PlantUML syntax...")
            val validationResult = diagramProcessor.plantumlService.validateSyntax(diagram.plantuml.code)

            if (validationResult is PlantumlService.SyntaxValidationResult.Invalid) {
                logger.lifecycle("    Validation errors found:")
                logger.lifecycle("      ${validationResult.errorMessage}")
                logger.lifecycle("      ${validationResult.stackTrace}")
                // In a real implementation, we would send this back to LLM for correction
                // For now, we'll continue with the processing
            }

            // Generate image from valid diagrams
            logger.lifecycle("  → Generating diagram image...")
            val imagesDir = project.file(config.output.images)
            try {
                imagesDir.mkdirs()
                val imageFile = File(imagesDir, "${promptFile.nameWithoutExtension}.${config.output.format}")

                // Generate actual PlantUML image
                diagramProcessor.plantumlService.generateImage(diagram.plantuml.code, imageFile)
            } catch (e: Exception) {
                logger.lifecycle("    Warning: Failed to generate image - ${e.message}")
            }

            // Request LLM validation with scoring (call once, reuse result)
            var validation: plantuml.ValidationFeedback? = null
            if (config.langchain4j.validation) {
                logger.lifecycle("  → Requesting LLM validation...")
                validation = diagramProcessor.validateDiagram(diagram)

                // Save validation feedback using Jackson serialization
                val validationsDir = project.file(config.output.validations)
                validationsDir.mkdirs()
                val validationFile = File(validationsDir, "${promptFile.nameWithoutExtension}.json")
                val validationData = mapOf(
                    "prompt" to promptFile.name,
                    "score" to validation.score,
                    "feedback" to validation.feedback,
                    "recommendations" to validation.recommendations
                )
                validationFile.writeText(
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(validationData)
                )
            }

            // Save valid diagrams for RAG training
            logger.lifecycle("  → Collecting for RAG training...")
            try {
                val ragDir = project.file(config.output.rag)
                ragDir.mkdirs()
                val diagramFile = File(ragDir, "${promptFile.nameWithoutExtension}.puml")
                diagramFile.writeText(diagram.plantuml.code)

                // Save validation feedback for RAG if validation is enabled
                if (config.langchain4j.validation && validation != null) {
                    diagramProcessor.saveForRagTraining(diagram, validation)
                }
            } catch (e: Exception) {
                logger.lifecycle("    Warning: Failed to save diagrams for RAG training - ${e.message}")
            }
        } else {
            logger.lifecycle("  → Failed to generate valid diagram after $maxIterations iterations")
            // Delete the processed prompt file
            promptFile.delete()
            throw RuntimeException("Failed to generate valid PlantUML diagram after max attempts ($maxIterations iterations exhausted). Check logs for details.")
        }

        // Delete the processed prompt file
        promptFile.delete()

        logger.lifecycle("  ✓ Completed processing: ${promptFile.name}")
    }

    /**
     * Cleans up partial output files when an error occurs.
     */
    private fun cleanupPartialOutputs() {
        try {
            val buildDir = File(project.buildDir, "plantuml-plugin")
            if (buildDir.exists()) {
                logger.lifecycle("  → Cleaning up partial outputs in ${buildDir.absolutePath}")
                buildDir.deleteRecursively()
                logger.lifecycle("  ✓ Cleanup complete")
            }
        } catch (e: Exception) {
            logger.warn("  ⚠ Cleanup failed: ${e.message}")
        }
    }
}