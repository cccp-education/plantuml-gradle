package plantuml.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * DIA-04 — robust extraction of diagram source from a raw LLM response.
 */
class LlmCodeExtractorTest {

    @Test
    fun `strips a fenced block with a language tag`() {
        val response = "```plantuml\n@startuml\nA --> B\n@enduml\n```"
        assertThat(LlmCodeExtractor.stripFences(response)).isEqualTo("@startuml\nA --> B\n@enduml")
    }

    @Test
    fun `strips a fence with a hyphenated language tag`() {
        val response = "```c4-plantuml\n@startuml\n@enduml\n```"
        assertThat(LlmCodeExtractor.stripFences(response)).isEqualTo("@startuml\n@enduml")
    }

    @Test
    fun `strips a fence surrounded by prose`() {
        val response = "Here is your diagram:\n```\n@startuml\nX\n@enduml\n```\nEnjoy!"
        assertThat(LlmCodeExtractor.stripFences(response)).isEqualTo("@startuml\nX\n@enduml")
    }

    @Test
    fun `strips an unterminated fence`() {
        val response = "```\n@startuml\nA\n@enduml"
        assertThat(LlmCodeExtractor.stripFences(response)).isEqualTo("@startuml\nA\n@enduml")
    }

    @Test
    fun `returns null when there is no fence`() {
        assertThat(LlmCodeExtractor.stripFences("@startuml\n@enduml")).isNull()
    }

    @Test
    fun `inline backticks without a newline are not a fence`() {
        assertThat(LlmCodeExtractor.stripFences("use `A --> B` please")).isNull()
    }

    @Test
    fun `cut raw plantuml from surrounding prose`() {
        val response = "Sure:\n@startuml\nA --> B\n@enduml\nDone."
        assertThat(LlmCodeExtractor.rawPlantUml(response)).isEqualTo("@startuml\nA --> B\n@enduml")
    }

    @Test
    fun `raw unclosed block returns to the end`() {
        assertThat(LlmCodeExtractor.rawPlantUml("prose @startuml\nA\n(no end)"))
            .isEqualTo("@startuml\nA\n(no end)")
    }

    @Test
    fun `no startuml returns null`() {
        assertThat(LlmCodeExtractor.rawPlantUml("just prose")).isNull()
    }
}
