package plantuml.repair

import dev.langchain4j.model.chat.ChatModel
import plantuml.agent.LlmCodeExtractor

/**
 * Reference AI adapter of [PlantumlBlockRepairer] (EPIC GBL-010, G-3).
 *
 * The plantuml borough owns the `[plantuml]` leaf: this adapter is the concrete
 * "assisted correction" of a block that fails validation. It asks a [ChatModel] to
 * fix the PlantUML **without changing its meaning** and extracts the corrected
 * source from the (possibly prose-wrapped) response via the PLT-AGENT extractor
 * [LlmCodeExtractor] (DIA-04) — fenced block first, then raw `@startuml … @enduml`.
 *
 * The adapter only **proposes** a source: the round-trip gate is enforced by
 * [PlantumlBlockCorrector] (a proposal that does not parse is discarded and the
 * source preserved, D5). The prompt is English-only (LLM prompt engineering is not
 * i18n-governed).
 */
class ChatModelBlockRepairer(
    private val chatModel: ChatModel,
) : PlantumlBlockRepairer {

    override fun repair(source: String): String? {
        val response = chatModel.chat(buildRepairPrompt(source))
        return LlmCodeExtractor.stripFences(response) ?: LlmCodeExtractor.rawPlantUml(response)
    }

    private fun buildRepairPrompt(source: String): String =
        """
        The following PlantUML diagram fails to parse. Fix its syntax while preserving
        its meaning (keep every element, relation and label unchanged where possible).
        Return ONLY the corrected PlantUML code wrapped in @startuml and @enduml tags.

        $source
        """.trimIndent()
}
