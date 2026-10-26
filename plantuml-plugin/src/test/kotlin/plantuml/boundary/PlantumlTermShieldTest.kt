package plantuml.boundary

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EPIC PLT-TERM-PROTECTION — the term shield protects code/business tokens from a
 * translation provider while leaving the prose translatable.
 */
class PlantumlTermShieldTest {

    private val shield = PlantumlTermShield()

    @Test
    fun `protects a primitive type in a prose label`() {
        val shielded = shield.shield("Le type integer est utilisé")

        assertEquals("Le type __PLT0__ est utilisé", shielded.text)
        assertEquals(listOf("integer"), shielded.terms)
        assertFalse(shielded.isFullyShielded)
    }

    @Test
    fun `protects several code tokens in one label`() {
        val shielded = shield.shield("Créer opencode.json avec AGENT.adoc et ./gradlew serve")

        assertEquals("Créer __PLT0__ avec __PLT1__ et __PLT2__ serve", shielded.text)
        assertEquals(listOf("opencode.json", "AGENT.adoc", "./gradlew"), shielded.terms)
    }

    @Test
    fun `protects a generic type and an identifier`() {
        val shielded = shield.shield("List<User> and PlantumlManager")

        assertEquals("__PLT0__ and __PLT1__", shielded.text)
        assertEquals(listOf("List<User>", "PlantumlManager"), shielded.terms)
    }

    @Test
    fun `does not protect a normal translated word`() {
        val shielded = shield.shield("Utilisateur")

        assertEquals(emptyList(), shielded.terms)
        assertEquals("Utilisateur", shielded.text)
    }

    @Test
    fun `protects a registered client term`() {
        val registry = NonTranslatableTermRegistry().apply { registerAll(listOf("pipeline", "saga")) }
        val shielded = PlantumlTermShield(registry).shield("The pipeline and the saga are fast")

        assertEquals("The __PLT0__ and the __PLT1__ are fast", shielded.text)
        assertEquals(listOf("pipeline", "saga"), shielded.terms)
    }

    @Test
    fun `restores the protected tokens`() {
        val original = "Le type integer est utilisé par opencode.json"
        val shielded = shield.shield(original)

        assertEquals(original, shield.restore(shielded.text, shielded))
    }

    @Test
    fun `restores after a provider translation`() {
        val shielded = shield.shield("Le type integer est utilisé")
        val translated = "The type __PLT0__ is used"

        assertEquals("The type integer is used", shield.restore(translated, shielded))
    }

    @Test
    fun `returns null when the provider dropped a placeholder`() {
        val shielded = shield.shield("Le type integer est utilisé")

        assertNull(shield.restore("The type is used", shielded))
    }

    @Test
    fun `a code-only text is fully shielded`() {
        val shielded = shield.shield("opencode.json")

        assertTrue(shielded.isFullyShielded)
        assertEquals("__PLT0__", shielded.text)
    }

    @Test
    fun `a prose text is not fully shielded`() {
        val shielded = shield.shield("opencode.json est utilisé")

        assertFalse(shielded.isFullyShielded)
    }

    @Test
    fun `protects a version and a PlantUML colour`() {
        val shielded = shield.shield("Version 1.2026.2 <#4CAF50>")

        assertTrue(shielded.terms.contains("1.2026.2"))
        assertTrue(shielded.terms.contains("<#4CAF50>"))
    }

    @Test
    fun `protects a backticked code span as one token`() {
        val shielded = shield.shield("Run `./gradlew serve` now")

        assertEquals(listOf("`./gradlew serve`"), shielded.terms)
        assertEquals("Run __PLT0__ now", shielded.text)
    }
}
