package plantuml.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import plantuml.PlantumlManager
import plantuml.agent.DescriptionPreflight
import plantuml.agent.DiagramSubtypeDetector
import plantuml.agent.PreflightVerdict
import plantuml.service.LlmService

/**
 * Gradle task: `preflightPlantumlDescription` (EPIC PLT-AGENT, DIA-02).
 *
 * Validates a natural-language diagram description with the LLM *before* any
 * generation cycle, returning clarifying questions instead of a degraded diagram.
 *
 * **Opt-in** (`langchain4j.preflight`, default `false`): a no-op unless enabled.
 * **Degraded** when no LLM is available: the description is treated as valid.
 *
 * Usage:
 * ```bash
 * ./gradlew preflightPlantumlDescription \
 *   -Pplantuml.langchain4j.preflight=true \
 *   -Pplantuml.description="C4 context of the API gateway"
 * ```
 */
@DisableCachingByDefault(because = "Pre-flight involves an LLM call and depends on external input")
abstract class PreflightPlantumlDescriptionTask : DefaultTask() {

    init {
        group = "verification"
        description = "Validates a diagram description before generation (opt-in langchain4j.preflight)"
    }

    @get:Input
    @get:Optional
    abstract val descriptionText: Property<String>

    @TaskAction
    fun preflight() {
        val config = PlantumlManager.Configuration.load(project)

        val enabled = (project.findProperty("plantuml.langchain4j.preflight") as? String)?.toBoolean()
            ?: config.langchain4j.preflight
        if (!enabled) {
            logger.lifecycle("preflightPlantumlDescription: disabled (enable with plantuml.langchain4j.preflight=true)")
            return
        }

        val text = (project.findProperty("plantuml.description") as? String) ?: descriptionText.orNull
        if (text.isNullOrBlank()) {
            logger.lifecycle("preflightPlantumlDescription: no description provided (-Pplantuml.description=...)")
            return
        }

        val chatModel = LlmService(config).createChatModel()
        if (chatModel == null) {
            logger.lifecycle("preflightPlantumlDescription: no LLM available, skipping (degraded)")
            return
        }

        val verdict = try {
            DescriptionPreflight.parse(
                chatModel.chat(DescriptionPreflight.prompt(text, DiagramSubtypeDetector.hint(text)))
            )
        } catch (e: Exception) {
            logger.warn("preflightPlantumlDescription: LLM error, treating description as valid (fail-safe): ${e.message}")
            PreflightVerdict.Valid
        }

        when (verdict) {
            is PreflightVerdict.Valid ->
                logger.lifecycle("preflightPlantumlDescription: description VALID")
            is PreflightVerdict.NeedsClarification ->
                throw GradleException("preflightPlantumlDescription: description needs clarification:\n${verdict.questions}")
        }
    }
}
