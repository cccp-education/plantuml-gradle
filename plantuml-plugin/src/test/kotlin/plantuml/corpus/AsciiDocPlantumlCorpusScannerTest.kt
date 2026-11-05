package plantuml.corpus

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsciiDocPlantumlCorpusScannerTest {
    private val scanner = AsciiDocPlantumlCorpusScanner

    @Test
    fun `reads an explicit block id and format`() {
        val blocks =
            scanner.scan(
                "fr/article.adoc",
                """
                = Article

                [plantuml, session-evolution, svg]
                ----
                @startuml
                A --> B
                @enduml
                ----
                """.trimIndent(),
            )

        assertEquals(1, blocks.size)
        assertEquals("session-evolution", blocks.single().id)
        assertEquals(4, blocks.single().line)
        assertEquals("@startuml\nA --> B\n@enduml", blocks.single().source)
    }

    @Test
    fun `falls back to the attribute line when the id is omitted`() {
        val block =
            scanner
                .scan(
                    "article.adoc",
                    """
                    [plantuml]
                    ----
                    @startuml
                    @enduml
                    ----
                    """.trimIndent(),
                ).single()

        assertEquals("line-1", block.id)
    }

    @Test
    fun `strips quotes around an explicit id`() {
        val block =
            scanner
                .scan(
                    "article.adoc",
                    """
                    [plantuml, "jbake-class-diagram", "png"]
                    ----
                    @startuml
                    @enduml
                    ----
                    """.trimIndent(),
                ).single()

        assertEquals("jbake-class-diagram", block.id)
    }

    @Test
    fun `reads a hash id attribute`() {
        val block =
            scanner
                .scan(
                    "article.adoc",
                    """
                    [plantuml#knowledge-graph]
                    ----
                    @startuml
                    @enduml
                    ----
                    """.trimIndent(),
                ).single()

        assertEquals("knowledge-graph", block.id)
    }

    @Test
    fun `reads a dot delimited block`() {
        val block =
            scanner
                .scan(
                    "article.adoc",
                    """
                    [plantuml, sequence, svg]
                    ....
                    @startuml
                    A -> B
                    @enduml
                    ....
                    """.trimIndent(),
                ).single()

        assertEquals("@startuml\nA -> B\n@enduml", block.source)
    }

    @Test
    fun `uses a preceding anchor as id`() {
        val block =
            scanner
                .scan(
                    "article.adoc",
                    """
                    [[deployment-view]]
                    [plantuml]
                    ----
                    @startuml
                    @enduml
                    ----
                    """.trimIndent(),
                ).single()

        assertEquals("deployment-view", block.id)
    }

    @Test
    fun `reads several blocks in document order`() {
        val blocks =
            scanner.scan(
                "article.adoc",
                """
                [plantuml, first]
                ----
                @startuml
                @enduml
                ----

                [plantuml, second]
                ----
                @startmindmap
                * Root
                @endmindmap
                ----
                """.trimIndent(),
            )

        assertEquals(listOf("first", "second"), blocks.map { it.id })
    }

    @Test
    fun `ignores a source plantuml listing`() {
        val blocks =
            scanner.scan(
                "article.adoc",
                """
                [source,plantuml]
                ----
                @startuml
                A --> B
                @enduml
                ----
                """.trimIndent(),
            )

        assertTrue(blocks.isEmpty(), "a source listing is not a diagram")
    }

    @Test
    fun `ignores a plantuml attribute quoted inside a literal block`() {
        val blocks =
            scanner.scan(
                "article.adoc",
                """
                [source,text]
                ....
                [plantuml, quoted]
                ----
                @startuml
                @enduml
                ----
                ....
                """.trimIndent(),
            )

        assertTrue(blocks.isEmpty(), "a quoted attribute is not a diagram")
    }

    @Test
    fun `ignores a comment fence`() {
        val blocks =
            scanner.scan(
                "article.adoc",
                """
                ////
                [plantuml, commented]
                ----
                @startuml
                @enduml
                ----
                ////
                """.trimIndent(),
            )

        assertTrue(blocks.isEmpty(), "a commented block is not published")
    }

    @Test
    fun `ignores a commented attribute line`() {
        val blocks =
            scanner.scan(
                "article.adoc",
                """
                // [plantuml, commented]
                ----
                @startuml
                @enduml
                ----
                """.trimIndent(),
            )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `does not emit an unterminated block`() {
        val blocks =
            scanner.scan(
                "article.adoc",
                """
                [plantuml, dangling]
                ----
                @startuml
                @enduml
                """.trimIndent(),
            )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `does not confuse a similarly named attribute`() {
        val blocks =
            scanner.scan(
                "article.adoc",
                """
                [plantumlx]
                ----
                @startuml
                @enduml
                ----
                """.trimIndent(),
            )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `a document without a diagram yields no block`() {
        assertTrue(scanner.scan("article.adoc", "= Article\n\nJust prose.").isEmpty())
    }
}
