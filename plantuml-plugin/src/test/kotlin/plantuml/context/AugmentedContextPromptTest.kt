package plantuml.context

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [AugmentedContextPrompt] (EPIC PLT-CONTEXT, CTX-04).
 *
 * Baby-step TDD (RED → GREEN): the prompt composition that injects the collected
 * augmented context into the LLM generation prompt. Pure — no I/O, no Gradle.
 */
class AugmentedContextPromptTest {

    @Test
    fun `a blank context leaves the prompt untouched`() {
        val prompt = "Generate a class diagram"
        assertEquals(prompt, AugmentedContextPrompt.prepend("", prompt))
        assertEquals(prompt, AugmentedContextPrompt.prepend("   \n  ", prompt))
    }

    @Test
    fun `a non-blank context is prepended under the context header`() {
        val result = AugmentedContextPrompt.prepend("EAGER-RULES", "Generate a class diagram")
        assertTrue(result.startsWith(AugmentedContextPrompt.CONTEXT_HEADER))
        assertTrue(result.contains("EAGER-RULES"))
        assertTrue(result.endsWith("Generate a class diagram"))
    }

    @Test
    fun `the context block precedes the base prompt`() {
        val result = AugmentedContextPrompt.prepend("CONTEXT-BODY", "BASE-PROMPT")
        assertTrue(
            result.indexOf("CONTEXT-BODY") < result.indexOf("BASE-PROMPT"),
            "the augmented context must appear before the base prompt",
        )
    }

    @Test
    fun `the context is trimmed so the block stays stable`() {
        val result = AugmentedContextPrompt.prepend("\n\n  EAGER-RULES  \n\n", "BASE")
        assertEquals(
            "${AugmentedContextPrompt.CONTEXT_HEADER}\nEAGER-RULES\n\nBASE",
            result,
        )
        assertFalse(result.contains("  EAGER-RULES"))
    }
}
