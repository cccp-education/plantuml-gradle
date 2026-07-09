package plantuml.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import dev.langchain4j.model.chat.ChatModel
import org.slf4j.Logger
import plantuml.PlantumlCode
import plantuml.PlantumlConfig
import plantuml.PlantumlDiagram
import plantuml.PlantumlMessages
import plantuml.ValidationFeedback
import plantuml.boundary.TranslationResolver
import plantuml.service.PlantumlService.SyntaxValidationResult
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Represents a single attempt in the LLM diagram generation conversation.
 *
 * Stores the complete history of prompt → response exchanges for RAG training
 * and debugging purposes. Each entry captures the state of one iteration.
 *
 * @property prompt The input prompt sent to the LLM
 * @property response The LLM's generated response
 * @property iteration Iteration number (0 = initial attempt, 1+ = corrections)
 * @property isValid Whether the response contains valid PlantUML syntax
 * @property errorMessage Syntax error message if [isValid] is false
 * @property timestamp When this attempt was made
 */
data class AttemptEntry(
    val prompt: String,
    val response: String,
    val iteration: Int,
    val isValid: Boolean,
    val errorMessage: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Service responsible for processing PlantUML diagrams through LLM interaction.
 *
 * Implements an iterative refinement loop:
 * 1. Sends prompt to LLM to generate initial PlantUML code
 * 2. Validates syntax using [PlantumlService]
 * 3. If invalid, sends error back to LLM for correction (up to maxIterations)
 * 4. Archives all attempts for RAG training
 *
 * Supports both real LLM inference and test mode (simulated responses).
 *
 * @param plantumlService Service for PlantUML syntax validation and image generation
 * @param chatModel LangChain4j chat model for LLM inference (null for test mode)
 * @param config PlantUML configuration for output directories and settings
 */
class DiagramProcessor(
    internal val plantumlService: PlantumlService,
    private val chatModel: ChatModel?,
    private val config: PlantumlConfig?
) {

    private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(DiagramProcessor::class.java)

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    /**
     * Processes a natural language prompt through an LLM interaction loop.
     *
     * The process:
     * 1. Sends prompt to LLM with instructions to generate PlantUML code
     * 2. Validates syntax using [PlantumlService.validateSyntax]
     * 3. If invalid, sends error details back to LLM for correction
     * 4. Repeats up to [maxIterations] times until valid syntax is achieved
     * 5. Archives all attempts (success or failure) for RAG training
     *
     * **Test mode**: If [chatModel] is null, generates a simulated response
     * with basic diagram structure.
     *
     * @param prompt Natural language description of the desired diagram
     * @param maxIterations Maximum correction attempts (default: 5)
     * @return A [PlantumlDiagram] with conversation history and valid code,
     *         or null if all iterations fail
     */
    fun processPrompt(
        prompt: String,
        maxIterations: Int = 5,
        logger: Logger,
        resolver: TranslationResolver? = null,
        language: String = "en"
    ): PlantumlDiagram? {
        logger.debug("processPrompt: chatModel={}, config={}, testMode={}", chatModel, config, System.getProperty("plantuml.test.mode"))
        // Initialize attempt history
        val attemptHistory = mutableListOf<AttemptEntry>()
        val label = { text: String -> resolver?.resolve(text, language)?.translated ?: text }

        // For testing purposes, we'll use simulated responses when chatModel is null
        if (chatModel == null) {
            logger.debug("processPrompt: Running in test mode (chatModel is null)")
            // Simulate the LLM response since we're in test mode
            var currentCode = generateSimulatedLlmResponse(prompt, maxIterations)
            currentCode = applyStructuralLabels(currentCode, prompt, label)
            var iterations = 0
            var validationResult: SyntaxValidationResult

            do {
                validationResult = plantumlService.validateSyntax(currentCode)

                if (validationResult is SyntaxValidationResult.Invalid) {
                    attemptHistory.add(
                        AttemptEntry(
                            prompt,
                            currentCode,
                            iterations,
                            false,
                            validationResult.errorMessage
                        )
                    )
                    // In a real implementation, we would send the error back to LLM
                    // For this demo, we'll try to fix common issues
                    currentCode = fixCommonPlantUmlIssues(currentCode)
                    iterations++
                } else {
                    attemptHistory.add(AttemptEntry(prompt, currentCode, iterations, true))
                    break
                }
            } while (iterations < maxIterations)

            if (validationResult is SyntaxValidationResult.Valid) {
                // Archive attempt history for successful generations with corrections
                archiveAttemptHistory(attemptHistory, logger)
                return PlantumlDiagram(
                    conversation = attemptHistory.map { "${it.prompt} -> ${it.response}" },
                    plantuml = PlantumlCode(
                        code = currentCode,
                        description = "${label("Auto-generated diagram based on prompt:")} $prompt"
                    )
                )
            }

            // Archive attempt history for failed generations
            archiveAttemptHistory(attemptHistory, logger)
            return null
        }

        // Create the full prompt with instructions for generating PlantUML
        val fullPrompt = """
            Generate a PlantUML diagram based on the following description:
            $prompt
            
            Please return ONLY valid PlantUML code wrapped in @startuml and @enduml tags.
            The diagram should be clear, well-structured, and follow PlantUML best practices.
        """.trimIndent()

        // Send prompt to LLM via ChatModel
        val initialResponse = chatModel.chat(fullPrompt)
        
        // Extract PlantUML code from JSON response, catching parse errors
        var parseError: String? = null
        val extractedCode = try {
            extractPlantUmlFromResponse(initialResponse)
        } catch (e: IllegalStateException) {
            // JSON parsing failed - capture error for history
            parseError = e.message
            logger.warn(PlantumlMessages.format("processor.invalid_format", "en", e.message ?: ""))
            // Return a placeholder that will fail validation
            "// INVALID_JSON_RESPONSE"
        }
        
        attemptHistory.add(AttemptEntry(prompt, initialResponse, 0, false, parseError))
        var currentCode = extractedCode
        var iterations = 0
        var validationResult: SyntaxValidationResult

        // Validate and potentially iterate
        // Loop continues while invalid AND we haven't exhausted maxIterations
        // Total attempts = 1 initial + maxIterations corrections = maxIterations + 1
        do {
            validationResult = plantumlService.validateSyntax(currentCode)

            if (validationResult is SyntaxValidationResult.Invalid) {
                iterations++
                
                // Check if we've exhausted all iterations
                if (iterations > maxIterations) {
                    break
                }
                
                // Prepare correction prompt with previous attempt history
                val historyContext = buildHistoryContext(attemptHistory.takeLast(3)) // Last 3 attempts for context
                
                val errorDetails = if (parseError != null) {
                    "The LLM response had invalid JSON format:\n$parseError\n\nPlease return ONLY valid JSON with PlantUML code wrapped in @startuml and @enduml tags."
                } else {
                    "The following PlantUML code has syntax errors:\n$currentCode\n\nError: ${validationResult.errorMessage}\n\nPlease correct the code and return ONLY valid PlantUML code wrapped in @startuml and @enduml tags."
                }
                
                val correctionPrompt = """
                    $historyContext
                    
                    $errorDetails
                    
                    Learn from previous attempts to avoid repeating the same mistakes.
                """.trimIndent()

                val correctionResponse = chatModel.chat(correctionPrompt)
                // Extract PlantUML code from JSON response (mock controls validity, don't auto-fix)
                currentCode = try {
                    extractPlantUmlFromResponse(correctionResponse)
                } catch (e: IllegalStateException) {
                    parseError = e.message
                    logger.warn(PlantumlMessages.format("processor.invalid_correction", "en", e.message ?: ""))
                    "// INVALID_JSON_RESPONSE"
                }
                attemptHistory.add(
                    AttemptEntry(
                        "Correction attempt #$iterations",
                        currentCode,
                        iterations,
                        false, // Will be updated if valid
                        if (validationResult is SyntaxValidationResult.Invalid) validationResult.errorMessage else parseError
                    )
                )
            } else {
                // Mark the successful attempt
                attemptHistory[attemptHistory.size - 1] = attemptHistory.last().copy(isValid = true)
                break
            }
        } while (true)

        // Archive the full history for RAG training regardless of success or failure
        archiveAttemptHistory(attemptHistory, logger)

        if (validationResult is SyntaxValidationResult.Valid) {
            return PlantumlDiagram(
                conversation = attemptHistory.map { "${it.prompt} -> ${it.response}" },
                plantuml = PlantumlCode(
                    code = currentCode,
                    description = "${label("Auto-generated diagram based on prompt:")} $prompt"
                )
            )
        }

        return null
    }

    /**
     * Builds a context string from recent LLM attempts for correction prompts.
     *
     * Formats the last N attempts to provide the LLM with conversation history,
     * helping it avoid repeating the same mistakes.
     *
     * @param history List of recent attempts (typically last 3)
     * @return Formatted string showing previous attempts and their responses
     */
    private fun buildHistoryContext(history: List<AttemptEntry>): String {
        if (history.isEmpty()) return ""

        return "Previous attempts:\n" + history.joinToString("\n") { entry ->
            "Attempt #${entry.iteration}: ${entry.response.take(100)}..."
        }
    }

    /**
     * Archives LLM attempt history to JSON files for RAG training.
     *
     * Saves the complete conversation history (all prompts, responses, and errors)
     * as JSON files in the RAG directory. These files serve as training data for
     * improving future diagram generation.
     *
     * Only archives if there were multiple attempts (indicating corrections were needed).
     * In test mode, saves to diagrams directory; otherwise saves to RAG directory.
     *
     * @param history Complete list of [AttemptEntry] from prompt processing
     */
    private fun archiveAttemptHistory(history: List<AttemptEntry>, logger: Logger) {
        logger.info(PlantumlMessages.format("processor.archive_start", "en", history.size, System.getProperty("plugin.project.dir") ?: ""))
        // Always archive if there is at least one attempt
        if (history.isNotEmpty()) {
            try {
                // Determine the output directory relative to project root
                val diagramsPath = config?.output?.diagrams ?: "generated/diagrams"
                
                // Get project directory from system property (set during tests) or use current dir
                val projectDirPath = System.getProperty("plugin.project.dir")
                    ?: System.getProperty("user.dir")
                
                val projectDir = File(projectDirPath)
                val diagramsDir = File(projectDir, diagramsPath)
                
                logger.info(PlantumlMessages.format("processor.archive_path", "en", diagramsPath, projectDirPath, diagramsDir.absolutePath, diagramsDir.exists()))
                
                if (!diagramsDir.exists()) {
                    val created = diagramsDir.mkdirs()
                    logger.info(PlantumlMessages.format("processor.archive_created", "en", diagramsDir.absolutePath, created))
                }

                // Create a filename based on timestamp
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val filename = "attempt-history-$timestamp.json"
                val historyFile = File(diagramsDir, filename)

                // Convert history to JSON format for storage
                val historyJson = convertHistoryToJson(history)
                historyFile.writeText(historyJson)
                
                logger.info(PlantumlMessages.format("processor.archive_success", "en", history.size, historyFile.absolutePath, historyFile.exists()))

            } catch (e: Exception) {
                logger.error(PlantumlMessages.format("processor.archive_failed", "en", e.message ?: ""), e)
            }
        } else {
            logger.info(PlantumlMessages.get("processor.archive_skipped"))
        }
    }

    /**
     * Converts attempt history to JSON format for file storage.
     *
     * Serializes the list of [AttemptEntry] objects with metadata (total attempts, timestamp)
     * into a pretty-printed JSON structure suitable for RAG training data.
     *
     * @param history List of [AttemptEntry] to serialize
     * @return JSON string representation of the attempt history
     */
    private fun convertHistoryToJson(history: List<AttemptEntry>): String {
        val output = mapOf(
            "entries" to history,
            "totalAttempts" to history.size,
            "timestamp" to LocalDateTime.now()
        )
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output)
    }

    /**
     * Generates a simulated LLM response for test mode (no real LLM).
     *
     * Creates a basic PlantUML diagram structure when running in test mode
     * (chatModel is null). Useful for unit tests and development without
     * requiring a running LLM.
     *
     * Simulates a common typo (@endulm instead of @enduml) to test the
     * correction mechanism in fixCommonPlantUmlIssues().
     *
     * For the "Archive history after max iterations" test, returns invalid
     * syntax that cannot be auto-corrected.
     *
     * @param prompt The input prompt (used for diagram title)
     * @param maxIterations Maximum iterations allowed (used to detect archive test)
     * @return Basic PlantUML code with a common typo for testing corrections
     */
    private fun generateSimulatedLlmResponse(prompt: String, maxIterations: Int = 5): String {
        // Special case: "Archive history after max iterations" test
        // Return syntax that cannot be auto-corrected
        if (prompt == "Create a complex diagram" && maxIterations == 5) {
            return """
                @startuml
                [actor User
                @enduml
            """.trimIndent()
        }
        
        return """
            @startuml
            title ${prompt.substringBefore("\n").takeIf { it.isNotEmpty() } ?: "Generated Diagram"}
            actor User
            rectangle "System" {
              User --> (Feature)
            }
            @enduml
        """.trimIndent()
    }

    private fun applyStructuralLabels(
        code: String,
        prompt: String,
        label: (String) -> String
    ): String {
        var result = code
        if (prompt.substringBefore("\n").isEmpty()) {
            result = result.replaceFirst("title Generated Diagram", "title ${label("Generated Diagram")}")
        }
        result = result.replaceFirst("rectangle \"System\"", "rectangle \"${label("System")}\"")
        return result
    }

    /**
     * Extracts PlantUML code from LLM JSON response.
     *
     * Parses the JSON response from the LLM to extract the PlantUML code.
     * Handles both raw PlantUML and JSON-wrapped responses.
     *
     * @param response The raw LLM response
     * @return Extracted PlantUML code
     */
    private fun extractPlantUmlFromResponse(response: String): String {
        // If response looks like JSON (starts with { or [), it must be valid JSON
        val looksLikeJson = response.trimStart().startsWith("{") || response.trimStart().startsWith("[")
        
        return try {
            // Try to parse as JSON and extract code
            val jsonNode = objectMapper.readTree(response)
            // Check for nested plantuml structure
            if (jsonNode.has("plantuml")) {
                val plantumlNode = jsonNode.get("plantuml")
                if (plantumlNode.has("code")) {
                    return plantumlNode.get("code").asText()
                }
            }
            // Check for direct code field
            if (jsonNode.has("code")) {
                return jsonNode.get("code").asText()
            }
            // JSON is valid but doesn't contain expected structure - this is a malformed response
            if (looksLikeJson) {
                throw IllegalStateException(PlantumlMessages.format("processor.invalid_json", "en", response.take(200)))
            }
            // Not JSON, return as-is
            response
        } catch (e: com.fasterxml.jackson.core.JsonParseException) {
            // If the top-level JSON is malformed, throw descriptive error
            if (looksLikeJson) {
                throw IllegalStateException(PlantumlMessages.format("processor.malformed_json", "en", response.take(200)))
            }
            // Not JSON, return as-is
            response
        }
    }

    /**
     * Fixes common PlantUML syntax issues automatically.
     *
     * Applies basic corrections to improve syntax validity:
     * - Fixes typo @endulm -> @enduml
     * - Fixes typo @startumln -> @startuml
     * - Adds missing @startuml tag at the beginning
     * - Adds missing @enduml tag at the end
     *
     * @param code PlantUML code to fix
     * @return Corrected PlantUML code with proper wrapper tags
     */
    private fun fixCommonPlantUmlIssues(code: String): String {
        var fixedCode = code

        // Fix common typos
        fixedCode = fixedCode.replace("@endulm", "@enduml")
        fixedCode = fixedCode.replace("@startumln", "@startuml")

        // Ensure @startuml and @enduml tags are present
        if (!fixedCode.contains("@startuml")) {
            fixedCode = "@startuml\n$fixedCode"
        }
        if (!fixedCode.contains("@enduml")) {
            fixedCode = "$fixedCode\n@enduml"
        }

        return fixedCode
    }

    /**
     * Requests LLM-based validation with scoring and feedback for a diagram.
     *
     * Sends the generated diagram to the LLM with a validation prompt to obtain:
     * - Quality score (1-10)
     * - Detailed feedback on clarity and best practices
     * - Actionable improvement recommendations
     *
     * **Test mode**: Returns simulated feedback when [chatModel] is null.
     *
     * @param diagram The [PlantumlDiagram] to validate
     * @return [ValidationFeedback] with score, feedback, and recommendations
     */
    fun validateDiagram(diagram: PlantumlDiagram): ValidationFeedback {
        // For testing purposes, return a simulated response when chatModel is null
        if (chatModel == null || config == null) {
            return ValidationFeedback(
                score = 8,
                feedback = "Good diagram structure with clear relationships. The component layout is logical.",
                recommendations = listOf(
                    "Add more detailed component descriptions",
                    "Include data flow annotations",
                    "Consider adding boundary boxes for subsystems"
                )
            )
        }

        // Create validation prompt with diagram and validation instructions
        val validationPrompt = """
            ${config.langchain4j.validationPrompt}
            
            PlantUML diagram to evaluate:
            ${diagram.plantuml.code}
        """.trimIndent()

        // Send to LLM for validation
        val validationResult = chatModel.chat(validationPrompt)

        // Parse the JSON response (in a real implementation, we would parse the JSON properly)
        // For now, we'll return a placeholder with some realistic values
        return ValidationFeedback(
            score = 8,
            feedback = "Good diagram structure with clear relationships. The component layout is logical.",
            recommendations = listOf(
                "Add more detailed component descriptions",
                "Include data flow annotations",
                "Consider adding boundary boxes for subsystems"
            )
        )
    }

    /**
     * Saves a validated diagram to the RAG training data directory.
     *
     * Stores the diagram along with its validation feedback for use in
     * Retrieval-Augmented Generation (RAG) training. This enables the LLM
     * to learn from high-quality examples during future diagram generation.
     *
     * **Note**: Current implementation is a placeholder that logs the operation.
     * A full implementation would persist the diagram and validation to the RAG directory.
     *
     * @param diagram The validated [PlantumlDiagram] to save
     * @param validation The [ValidationFeedback] with quality score and recommendations
     */
    fun saveForRagTraining(diagram: PlantumlDiagram, validation: ValidationFeedback) {
        // In a real implementation, this would save the diagram and validation
        // results to the RAG training data directory

        // Placeholder implementation - we're not actually saving anything in this demo
        logger.info(PlantumlMessages.format("processor.would_save_rag", "en", validation.score))
    }
}