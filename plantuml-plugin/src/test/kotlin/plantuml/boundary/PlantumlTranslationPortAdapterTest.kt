package plantuml.boundary

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlStrategy
import contracts.plantuml.PlantUmlTranslationOutcome
import contracts.plantuml.PlantUmlTranslationRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlantumlTranslationPortAdapterTest {
    private fun translator(map: Map<String, String>) =
        object : TranslationService {
            override fun translate(request: TranslationRequest): TranslationResult =
                map[request.sourceText]?.let { TranslationResult.Success(it) }
                    ?: TranslationResult.Failure("no entry")
        }

    private fun port(map: Map<String, String>) = PlantumlTranslationPortAdapter(translator(map))

    @Test
    fun `preserves a block with only semantic identity`() {
        val adapter = port(emptyMap())
        val block = PlantUmlBlock("@startuml\nU --> Foo.Bar\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `translates a quoted label via the provider`() {
        val adapter = port(mapOf("Utilisateur" to "User"))
        val block = PlantUmlBlock("@startuml\nclass \"Utilisateur\"\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Translated)
        assertTrue((outcome as PlantUmlTranslationOutcome.Translated).block.raw.contains("\"User\""))
    }

    @Test
    fun `translates an unquoted directive value`() {
        val adapter = port(mapOf("Évolution Mensuelle" to "Monthly Evolution"))
        val block = PlantUmlBlock("@startuml\ntitle Évolution Mensuelle\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue((outcome as PlantUmlTranslationOutcome.Translated).block.raw.contains("title Monthly Evolution"))
    }

    @Test
    fun `translates a single-line note even when the block has no label`() {
        val adapter = port(mapOf("Bonjour" to "Hello"))
        val block = PlantUmlBlock("@startuml\nA -> B\nnote right of B : Bonjour\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Translated)
        assertTrue((outcome as PlantUmlTranslationOutcome.Translated).block.raw.contains("note right of B : Hello"))
    }

    @Test
    fun `translates a multi-line note body in place`() {
        val adapter = port(mapOf("Bonjour\nMonde" to "Hello\nWorld"))
        val block = PlantUmlBlock("@startuml\nnote right of B\n  Bonjour\n  Monde\nend note\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("Hello\nWorld"), "note body must be translated in place: $raw")
        assertTrue(raw.contains("end note"), "note structure must be preserved")
    }

    @Test
    fun `escapes a real newline returned by the provider for a single-line note`() {
        val adapter = port(mapOf("Bonjour" to "Hello\nWorld"))
        val block = PlantUmlBlock("@startuml\nnote right of B : Bonjour\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("Hello\\nWorld"), "a single-line note must escape the newline: $raw")
        assertTrue(!raw.contains("Hello\nWorld"), "a raw line break must not survive in a single-line note")
    }

    @Test
    fun `keeps a real newline returned by the provider for a multi-line note body`() {
        val adapter = port(mapOf("Bonjour" to "Hello\nWorld"))
        val block = PlantUmlBlock("@startuml\nnote right of B\n  Bonjour\nend note\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("Hello\nWorld"), "a note body keeps real newlines: $raw")
        assertTrue(!raw.contains("Hello\\nWorld"), "a multi-line note body must not be escaped")
    }

    // ── PLT-OUT-2 — quoted notes and note-specific correction/PRESERVE ──────

    @Test
    fun `translates a quoted floating note without corrupting the alias`() {
        val adapter = port(mapOf("Bonjour" to "Hello"))
        val block = PlantUmlBlock("@startuml\nnote \"Bonjour\" as N1\nA -> B\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("note \"Hello\" as N1"), raw)
    }

    @Test
    fun `a quoted note containing a colon is translated as one note`() {
        val adapter = port(mapOf("Warning: see below" to "Attention : voir ci-dessous"))
        val block = PlantUmlBlock("@startuml\nnote \"Warning: see below\" as N1\nA -> B\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("note \"Attention : voir ci-dessous\" as N1"), raw)
        assertTrue(raw.contains("A -> B"), "the rest of the diagram must be preserved")
    }

    @Test
    fun `escapes a quote introduced by the provider inside a quoted note`() {
        val adapter = port(mapOf("Bonjour" to "Hello \"World\""))
        val block = PlantUmlBlock("@startuml\nnote \"Bonjour\" as N1\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("note \"Hello \\\"World\\\"\" as N1"), raw)
    }

    @Test
    fun `escapes a quote introduced by the provider inside a quoted label`() {
        val adapter = port(mapOf("Utilisateur" to "User \"X\""))
        val block = PlantUmlBlock("@startuml\nclass \"Utilisateur\"\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("class \"User \\\"X\\\"\""), raw)
    }

    @Test
    fun `a corrected quoted label passes the real syntax validator`() {
        val adapter =
            PlantumlTranslationPortAdapter(
                translator(mapOf("Utilisateur" to "User \"X\"")),
                validator = PlantumlSyntaxValidatorAdapter(),
            )
        val block = PlantUmlBlock("@startuml\nclass \"Utilisateur\"\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Translated)
        assertTrue((outcome as PlantUmlTranslationOutcome.Translated).block.raw.contains("class \"User \\\"X\\\"\""))
    }

    @Test
    fun `a blank note translation preserves the source note`() {
        val adapter = port(mapOf("Bonjour" to "   "))
        val block = PlantUmlBlock("@startuml\nnote right of B : Bonjour\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
        assertTrue((outcome as PlantUmlTranslationOutcome.Preserved).reason.isNotBlank())
    }

    @Test
    fun `a literal newline escape in a note is shielded translated and restored`() {
        val adapter = port(mapOf("query__PLT0__modèles" to "query__PLT0__models"))
        val block = PlantUmlBlock("@startuml\nnote right of B : query\\nmodèles\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("note right of B : query\\nmodels"), raw)
    }

    @Test
    fun `a note translation that breaks syntax is preserved by the round-trip gate`() {
        val adapter =
            PlantumlTranslationPortAdapter(
                translator(mapOf("Bonjour" to "end note")),
                validator = validator(valid = false),
            )
        val block = PlantUmlBlock("@startuml\nnote right of B\n  Bonjour\nend note\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
        assertTrue((outcome as PlantUmlTranslationOutcome.Preserved).reason.contains("syntax"))
    }

    @Test
    fun `a note-only block with a provider failure is preserved`() {
        val adapter = port(emptyMap())
        val block = PlantUmlBlock("@startuml\nA -> B\nnote right of B : Bonjour\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `converts a raw newline returned by the provider back to the plantuml escape`() {
        val adapter = port(mapOf("Outil de vibe coding" to "vibe coding CLI\nOpen source, free"))
        val block = PlantUmlBlock("@startuml\nrectangle \"Outil de vibe coding\" as TOOL\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("\\n"), "newline must be escaped: $raw")
        assertTrue(!raw.contains("vibe coding CLI\nOpen"), "raw line break must not survive")
    }

    // ── EPIC PLT-TERM-PROTECTION — code/business terms survive the translation ──

    @Test
    fun `translates a sequence message and protects a primitive type`() {
        val adapter = port(mapOf("Le type __PLT0__ est utilisé" to "The type __PLT0__ is used"))
        val block = PlantUmlBlock("@startuml\nA -> B : Le type integer est utilisé\n@enduml")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertEquals("A -> B : The type integer is used", raw.lines()[1])
    }

    @Test
    fun `translates an activity label and protects a file name`() {
        val adapter = port(mapOf("Créer __PLT0__" to "Create __PLT0__"))
        val block = PlantUmlBlock("@startuml\nstart\n:Créer opencode.json;\nstop\n@enduml")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains(":Create opencode.json;"), raw)
    }

    @Test
    fun `translates a quoted label and protects a type inside it`() {
        val adapter = port(mapOf("Le type __PLT0__" to "The type __PLT0__"))
        val block = PlantUmlBlock("@startuml\nclass \"Le type integer\"\n@enduml")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        assertTrue((outcome as PlantUmlTranslationOutcome.Translated).block.raw.contains("\"The type integer\""))
    }

    @Test
    fun `translates a note and protects a file name`() {
        val adapter = port(mapOf("Voir __PLT0__" to "See __PLT0__"))
        val block = PlantUmlBlock("@startuml\nnote right: Voir opencode.json\n@enduml")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        assertTrue((outcome as PlantUmlTranslationOutcome.Translated).block.raw.contains("note right: See opencode.json"))
    }

    @Test
    fun `a fully shielded prose label is not sent to the provider`() {
        val adapter = port(emptyMap())
        val block = PlantUmlBlock("@startuml\nA -> B : getUser()\n@enduml")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `a provider that drops a placeholder preserves the source label`() {
        val adapter = port(mapOf("Le type __PLT0__ est utilisé" to "The type is used"))
        val block = PlantUmlBlock("@startuml\nA -> B : Le type integer est utilisé\n@enduml")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `translates the actor and use case of a use-case diagram`() {
        val adapter =
            port(
                mapOf(
                    "Utilisateur" to "User",
                    "Rédiger documentation" to "Write documentation",
                ),
            )
        val block = PlantUmlBlock("@startuml\n:Utilisateur: --> (Rédiger documentation)\n@enduml")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains(":User: --> (Write documentation)"), raw)
    }

    @Test
    fun `translates a mindmap node and preserves a protected product name`() {
        val adapter = port(mapOf("Syntaxe" to "Syntax"))
        val block = PlantUmlBlock("@startmindmap\n* Syntaxe\n** PlantUML\n@endmindmap")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("* Syntax"), raw)
        assertTrue(raw.contains("** PlantUML"), raw)
    }

    @Test
    fun `empty block is preserved`() {
        val adapter = port(emptyMap())
        val outcome = adapter.translate(PlantUmlTranslationRequest(PlantUmlBlock(""), "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `provider failure preserves the block`() {
        val adapter = port(emptyMap())
        val block = PlantUmlBlock("@startuml\nclass \"Utilisateur\"\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    @Test
    fun `unified strategy alias points to the N0 contract enum`() {
        // D4 — one vocabulary: the boundary alias resolves to the contract enum.
        assertEquals(
            listOf(PlantUmlStrategy.TRANSLATE, PlantUmlStrategy.BORROW, PlantUmlStrategy.PRESERVE),
            contracts.plantuml.PlantUmlStrategy.entries
                .toList(),
        )
    }

    // ── US-3 — round-trip validation (D5) ────────────────────────────────────

    private fun validator(valid: Boolean) =
        object : contracts.plantuml.PlantUmlSyntaxValidator {
            override fun validate(plantumlCode: String): contracts.plantuml.SyntaxValidationResult =
                if (valid) {
                    contracts.plantuml.SyntaxValidationResult.Valid
                } else {
                    contracts.plantuml.SyntaxValidationResult.Invalid("broken", "")
                }
        }

    @Test
    fun `a translation rejected by the round-trip gate preserves the source block`() {
        // The provider returns a quote: it is escaped before validation (PLT-OUT-2).
        // The fake validator still rejects the block, proving the gate preserves
        // the source when a correction cannot make the block valid.
        val adapter =
            PlantumlTranslationPortAdapter(
                translator(mapOf("Utilisateur" to "User\" broken")),
                validator = validator(valid = false),
            )
        val block = PlantUmlBlock("@startuml\nclass \"Utilisateur\"\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(
            outcome is PlantUmlTranslationOutcome.Preserved,
            "a translation breaking PlantUML syntax must fall back to the source block",
        )
        assertTrue((outcome as PlantUmlTranslationOutcome.Preserved).reason.contains("syntax"))
    }

    @Test
    fun `a valid translation passes the round-trip gate`() {
        val adapter =
            PlantumlTranslationPortAdapter(
                translator(mapOf("Utilisateur" to "User")),
                validator = validator(valid = true),
            )
        val block = PlantUmlBlock("@startuml\nclass \"Utilisateur\"\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Translated)
    }

    @Test
    fun `an already valid source block is never validated when preserved`() {
        // PRESERVE short-circuits before any validation: no label change.
        val adapter =
            PlantumlTranslationPortAdapter(
                translator(emptyMap()),
                validator = validator(valid = false),
            )
        val block = PlantUmlBlock("@startuml\nU --> Foo.Bar\n@enduml")
        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))
        assertTrue(outcome is PlantUmlTranslationOutcome.Preserved)
    }

    // ── S-073 — label batching (one provider call per diagram) ────────────────

    @Test
    fun `batch translates every label in a single provider call`() {
        val calls = mutableListOf<String>()
        val batching =
            object : TranslationService {
                override fun translate(request: TranslationRequest): TranslationResult {
                    calls += request.sourceText
                    val labels = request.sourceText.split("\n${PlantumlLabelBatch.SEPARATOR}\n")
                    return TranslationResult.Success(labels.joinToString("\n${PlantumlLabelBatch.SEPARATOR}\n") { "T:$it" })
                }
            }
        val adapter = PlantumlTranslationPortAdapter(batching)
        val block = PlantUmlBlock("@startuml\nclass \"Alpha\"\nclass \"Beta\"\n@enduml")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("\"T:Alpha\""), raw)
        assertTrue(raw.contains("\"T:Beta\""), raw)
        assertEquals(1, calls.size, "exactly one batched provider call expected")
    }

    @Test
    fun `batch falls back to per-label when the provider cannot batch`() {
        val calls = mutableListOf<String>()
        val nonBatching =
            object : TranslationService {
                override fun translate(request: TranslationRequest): TranslationResult {
                    calls += request.sourceText
                    return when (request.sourceText) {
                        "Alpha" -> TranslationResult.Success("A")
                        "Beta" -> TranslationResult.Success("B")
                        else -> TranslationResult.Failure("cannot batch")
                    }
                }
            }
        val adapter = PlantumlTranslationPortAdapter(nonBatching)
        val block = PlantUmlBlock("@startuml\nclass \"Alpha\"\nclass \"Beta\"\n@enduml")

        val outcome = adapter.translate(PlantUmlTranslationRequest(block, "fr", "en"))

        val raw = (outcome as PlantUmlTranslationOutcome.Translated).block.raw
        assertTrue(raw.contains("\"A\""), raw)
        assertTrue(raw.contains("\"B\""), raw)
        assertEquals(3, calls.size, "one batch attempt then two per-label calls")
    }
}
