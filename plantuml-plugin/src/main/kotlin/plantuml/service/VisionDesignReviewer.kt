package plantuml.service

import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import plantuml.agent.DesignReview
import plantuml.agent.DesignReviewer
import java.util.Base64

/**
 * Vision-based design reviewer (EPIC PLT-AGENT, DIA-01).
 *
 * Bridges the [DesignReviewer] port to a LangChain4j [ChatModel] that supports images.
 * The rendered PNG is sent as a base64 data image alongside the review prompt. A model
 * without vision capability raises on the call — the caller degrades (review skipped),
 * so the design loop is never a hard prerequisite of `check`.
 */
class VisionDesignReviewer(
    private val chatModel: ChatModel,
) : DesignReviewer {

    override fun review(pngBytes: ByteArray, source: String): String {
        val image = ImageContent.from(Base64.getEncoder().encodeToString(pngBytes), "image/png")
        val message = UserMessage.from(TextContent.from(DesignReview.prompt()), image)
        return chatModel.chat(message).aiMessage().text()
    }
}
