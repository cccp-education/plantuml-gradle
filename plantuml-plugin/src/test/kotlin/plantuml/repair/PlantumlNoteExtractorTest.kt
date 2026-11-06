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
        val source =
            """
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

    @Test
    fun `a single-line occurrence exposes the trimmed text as its raw region`() {
        val source = "@startuml\nnote left of Car : This is a note\n@enduml"
        val occurrence = PlantumlNoteExtractor.occurrences(source).single()
        assertEquals("This is a note", occurrence.raw)
        assertEquals("This is a note", occurrence.text)
        assertTrue(occurrence.singleLine)
    }

    @Test
    fun `a multi-line occurrence exposes the raw indented body for in-place replacement`() {
        val source = "@startuml\nnote right of Car\n  line one\n  line two\nend note\n@enduml"
        val occurrence = PlantumlNoteExtractor.occurrences(source).single()
        assertEquals("  line one\n  line two", occurrence.raw)
        assertEquals("line one\nline two", occurrence.text)
        assertTrue(source.contains(occurrence.raw), "the raw region must be an exact substring of the source")
        assertFalse(occurrence.singleLine)
    }

    @Test
    fun `a quoted floating note is a single-line note`() {
        val source = "@startuml\nnote \"Hello world\" as N1\nA -> B\n@enduml"
        val occurrence = PlantumlNoteExtractor.occurrences(source).single()
        assertEquals("Hello world", occurrence.text)
        assertEquals("Hello world", occurrence.raw)
        assertTrue(occurrence.singleLine)
        assertTrue(occurrence.quoted)
    }

    @Test
    fun `a quoted note containing a colon does not swallow the rest of the diagram`() {
        val source = "@startuml\nnote \"Warning: see below\" as N1\nA -> B\n@enduml"
        val occurrence = PlantumlNoteExtractor.occurrences(source).single()
        assertEquals("Warning: see below", occurrence.text)
        assertTrue(occurrence.quoted)
        assertFalse(occurrence.text.contains("A -> B"), "the diagram must not become the note body")
    }

    @Test
    fun `a quoted note keeps the inner raw text exactly for the quoted replacement`() {
        val source = "@startuml\nnote \"  Hello world  \" as N1\n@enduml"
        val occurrence = PlantumlNoteExtractor.occurrences(source).single()
        assertEquals("Hello world", occurrence.text)
        assertEquals("  Hello world  ", occurrence.raw)
        assertTrue(source.contains("\"${occurrence.raw}\""))
    }

    @Test
    fun `a literal newline escape is part of the note text`() {
        val source = "@startuml\nnote right of Car : query\\nmodèles\n@enduml"
        assertEquals(listOf("query\\nmodèles"), PlantumlNoteExtractor.notes(source))
    }
}
