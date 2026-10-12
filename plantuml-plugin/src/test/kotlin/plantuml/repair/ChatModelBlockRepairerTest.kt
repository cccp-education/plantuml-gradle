package plantuml.repair

import dev.langchain4j.model.chat.ChatModel
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [ChatModelBlockRepairer] (EPIC GBL-010, G-3).
 *
 * Baby-step TDD (RED → GREEN): the AI adapter proposes a corrected source and
 * extracts it from the LLM response (fenced or raw) via [plantuml.agent.LlmCodeExtractor].
 * The round-trip gate is the corrector's responsibility, not this adapter's.
 */
class ChatModelBlockRepairerTest {

    private fun repairerReturning(response: String): ChatModelBlockRepairer {
        val model = mock(ChatModel::class.java)
        `when`(model.chat(any<String>())).thenReturn(response)
        return ChatModelBlockRepairer(model)
    }

    @Test
    fun `extracts a fenced corrected block`() {
        val repairer = repairerReturning("Here is the fix:\n```plantuml\n@startuml\nclass Fixed\n@enduml\n```")
        assertEquals("@startuml\nclass Fixed\n@enduml", repairer.repair("@startuml\nclass BrOKen\n@enduml"))
    }

    @Test
    fun `extracts a raw corrected block when there is no fence`() {
        val repairer = repairerReturning("Sure. @startuml\nclass Fixed\n@enduml done.")
        assertEquals("@startuml\nclass Fixed\n@enduml", repairer.repair("@startuml\nclass Broken\n@enduml"))
    }

    @Test
    fun `returns null when the response carries no PlantUML`() {
        val repairer = repairerReturning("I cannot fix this diagram.")
        assertNull(repairer.repair("@startuml\nclass Broken\n@enduml"))
    }
}
