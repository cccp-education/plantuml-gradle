package plantuml.boundary

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EPIC PLT-TERM-PROTECTION — the unquoted prose labels that the N0 label surface
 * misses.
 */
class PlantumlProseLabelExtractorTest {

    @Test
    fun `extracts a sequence message`() {
        val source = "@startuml\nA -> B : Lance le terminal\n@enduml"

        val labels = PlantumlProseLabelExtractor.occurrences(source)

        assertEquals(1, labels.size)
        assertEquals("Lance le terminal", labels.single().text)
    }

    @Test
    fun `extracts an activity label`() {
        val source = "@startuml\nstart\n:Créer le fichier;\nstop\n@enduml"

        assertEquals(listOf("Créer le fichier"), PlantumlProseLabelExtractor.texts(source))
    }

    @Test
    fun `extracts a state description`() {
        val source = "@startuml\nstate Ready : Prêt\n@enduml"

        assertEquals(listOf("Prêt"), PlantumlProseLabelExtractor.texts(source))
    }

    @Test
    fun `does not extract a note directive or quoted label`() {
        val source =
            "@startuml\n" +
                "note right of B : Bonjour\n" +
                "title Évolution\n" +
                "class \"Utilisateur\"\n" +
                "A -> B : \"quoted\"\n" +
                "@enduml"

        assertTrue(PlantumlProseLabelExtractor.occurrences(source).isEmpty())
    }

    @Test
    fun `skips a legend block`() {
        val source =
            "@startuml\n" +
                "legend right\n" +
                "  A -> B : inside the legend\n" +
                "endlegend\n" +
                "@enduml"

        assertTrue(PlantumlProseLabelExtractor.occurrences(source).isEmpty())
    }

    @Test
    fun `extracts every prose form in document order`() {
        val source =
            "@startuml\n" +
                "A -> B : Premier\n" +
                "state Ready : Deuxième\n" +
                ":Troisième;\n" +
                "@enduml"

        assertEquals(listOf("Premier", "Deuxième", "Troisième"), PlantumlProseLabelExtractor.texts(source))
    }

    @Test
    fun `keeps a code-only message for the shield to decide`() {
        val source = "@startuml\nA -> B : getUser()\n@enduml"

        assertEquals(listOf("getUser()"), PlantumlProseLabelExtractor.texts(source))
    }

    @Test
    fun `extracts the actor and use cases of a use-case diagram`() {
        val source = "@startuml\n:Utilisateur: --> (Rédiger documentation)\n@enduml"

        assertEquals(listOf("Utilisateur", "Rédiger documentation"), PlantumlProseLabelExtractor.texts(source))
    }

    @Test
    fun `extracts both use cases of a use-case relation`() {
        val source = "@startuml\n(Rédiger documentation) ..> (Générer PDF) : inclut\n@enduml"

        assertEquals(
            listOf("Rédiger documentation", "Générer PDF", "inclut"),
            PlantumlProseLabelExtractor.texts(source),
        )
    }

    @Test
    fun `extracts mindmap node labels`() {
        val source = "@startmindmap\n* AsciiDoc\n** Syntaxe\n*** Titres\n@endmindmap"

        assertEquals(listOf("AsciiDoc", "Syntaxe", "Titres"), PlantumlProseLabelExtractor.texts(source))
    }

    @Test
    fun `a sequence message with parentheses is not mistaken for a use case`() {
        val source = "@startuml\nA -> B : call(x)\n@enduml"

        assertEquals(listOf("call(x)"), PlantumlProseLabelExtractor.texts(source))
    }
}
