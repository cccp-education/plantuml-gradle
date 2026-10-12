package plantuml.repair

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [PlantumlNoteExtractor] (EPIC GBL-010, G-3 — "notes").
 *
 * Baby-step TDD (RED → GREEN): the plantuml leaf surfaces the note texts that the
 * N0 label surface misses.
 */
class PlantumlNoteExtractorTest {

    @Test
    fun `extracts a single-line note text`() {
        val source = "@startuml\nnote left of Car : This is an important note\n@enduml"
        assertEquals(listOf("This is an important note"), PlantumlNoteExtractor.notes(source))
    }

    @Test
    fun `extracts a multi-line note body`() {
        val source = "@startuml\nnote right of Car\n  line one\n  line two\nend note\n@enduml"
        assertEquals(listOf("line one\nline two"), PlantumlNoteExtractor.notes(source))
    }

    @Test
    fun `extracts every note in order`() {
        val source = """
            @startuml
            note left of A : first
            note over A, B
              second body
            end note
            @enduml
        """.trimIndent()
        assertEquals(listOf("first", "second body"), PlantumlNoteExtractor.notes(source))
    }

    @Test
    fun `returns an empty list when there is no note`() {
        val source = "@startuml\nclass Car {\n  - String brand\n}\n@enduml"
        assertTrue(PlantumlNoteExtractor.notes(source).isEmpty())
        assertFalse(PlantumlNoteExtractor.hasNotes(source))
    }

    @Test
    fun `captures the body of an unterminated note`() {
        val source = "@startuml\nnote right of Car\n  dangling body\n@enduml"
        assertEquals(listOf("dangling body"), PlantumlNoteExtractor.notes(source))
    }

    @Test
    fun `deduplicates repeated note texts`() {
        val source = "@startuml\nnote left : same\nnote right : same\n@enduml"
        assertEquals(listOf("same"), PlantumlNoteExtractor.notes(source))
    }

    @Test
    fun `hasNotes is true when a note is present`() {
        assertTrue(PlantumlNoteExtractor.hasNotes("@startuml\nnote as N\n  body\nend note\n@enduml"))
    }
}
