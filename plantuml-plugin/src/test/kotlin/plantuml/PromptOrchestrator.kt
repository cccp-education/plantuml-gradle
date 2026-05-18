package plantuml

import org.slf4j.Logger
import plantuml.service.DiagramProcessor
import plantuml.service.PlantumlService
import java.io.File
import java.nio.file.Path

enum class ProcessResult { SUCCESS, SKIPPED, FAILED }

/**
 * Business logic for prompt processing, extracted from GeneratePlantumlDiagramsTask.
 *
 * This class knows nothing about Gradle — it receives its dependencies via injection,
 * making it directly testable with JUnit without starting a Gradle process.
 *
 * The Gradle task becomes a thin wrapper that instantiates and delegates.
 */
class PromptOrchestrator(
    private val config: PlantumlConfig,
    private val diagramProcessor: DiagramProcessor,
    private val plantumlService: PlantumlService,
    private val projectDir: Path,
) {
    data class ProcessingResult(
        val totalPrompts: Int,
        val succeeded: Int,
        val failed: Int,
        val skipped: Int,
        val messages: List<String>,
    )

    fun process(logger: Logger): ProcessingResult {
        val promptsDir = projectDir.resolve(config.input.prompts).toFile()

        if (!promptsDir.exists() || !promptsDir.isDirectory) {
            return ProcessingResult(
                totalPrompts = 0,
                succeeded = 0,
                failed = 0,
                skipped = 0,
                messages = listOf("No prompts directory found at: ${promptsDir.absolutePath}"),
            )
        }

        val promptFiles = promptsDir.walkTopDown()
            .filter { it.isFile && it.extension == "prompt" }
            .toList()

        if (promptFiles.isEmpty()) {
            return ProcessingResult(
                totalPrompts = 0,
                succeeded = 0,
                failed = 0,
                skipped = 0,
                messages = listOf("No prompt files found in ${promptsDir.absolutePath}"),
            )
        }

        val messages = mutableListOf("Processing ${promptFiles.size} prompt files")
        var succeeded = 0
        var failed = 0
        var skipped = 0

        promptFiles.forEach { promptFile ->
            runCatching {
                val result = processOnePrompt(promptFile, messages,logger)
                when (result) {
                    ProcessResult.SUCCESS -> succeeded++
                    ProcessResult.SKIPPED -> skipped++
                    ProcessResult.FAILED -> failed++
                }
            }.onFailure { e ->
                failed++
                messages += "Failed to process ${promptFile.name}: ${e.message}"
            }
        }

        return ProcessingResult(
            totalPrompts = promptFiles.size,
            succeeded = succeeded,
            failed = failed,
            skipped = skipped,
            messages = messages,
        )
    }

    private fun processOnePrompt(promptFile: File, messages: MutableList<String>, logger: Logger): ProcessResult {
        val promptText = promptFile.readText().trim()
        if (promptText.isBlank()) {
            messages += "Skipping empty prompt: ${promptFile.name}"
            return ProcessResult.SKIPPED
        }

        val diagram = diagramProcessor.processPrompt(
            prompt = promptText,
            maxIterations = config.langchain4j.maxIterations,
            logger = logger
        ) ?: run {
            messages += "Could not generate valid diagram for: ${promptFile.name}"
            return ProcessResult.SKIPPED
        }

        val outputDir = projectDir.resolve(config.output.diagrams).toFile()
        outputDir.mkdirs()

        val outputFile = File(outputDir, "${promptFile.nameWithoutExtension}.puml")
        outputFile.writeText(diagram.plantuml.code)
        messages += "Generated: ${outputFile.name}"

        if (config.langchain4j.validation) {
            val imageDir = projectDir.resolve(config.output.images).toFile()
            imageDir.mkdirs()
            val imageFile = File(imageDir, "${promptFile.nameWithoutExtension}.${config.output.format}")
            plantumlService.generateImage(diagram.plantuml.code, imageFile)
        }

        return ProcessResult.SUCCESS
    }
}
