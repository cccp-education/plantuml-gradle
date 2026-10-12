package plantuml.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * DIA-02 — description pre-flight (fail-safe parsing).
 */
class DescriptionPreflightTest {

    @Test
    fun `VALID yields Valid`() {
        assertThat(DescriptionPreflight.parse("VALID")).isEqualTo(PreflightVerdict.Valid)
    }

    @Test
    fun `INVALID with questions yields NeedsClarification`() {
        val verdict = DescriptionPreflight.parse("INVALID\n1. Which actors?\n2. Which flow?")
        assertThat(verdict).isInstanceOf(PreflightVerdict.NeedsClarification::class.java)
        assertThat((verdict as PreflightVerdict.NeedsClarification).questions).contains("Which actors?")
    }

    @Test
    fun `INVALID without questions is fail-safe Valid`() {
        assertThat(DescriptionPreflight.parse("INVALID")).isEqualTo(PreflightVerdict.Valid)
    }

    @Test
    fun `unexpected output is fail-safe Valid`() {
        assertThat(DescriptionPreflight.parse("hmm, maybe")).isEqualTo(PreflightVerdict.Valid)
    }

    @Test
    fun `prompt embeds the description`() {
        assertThat(DescriptionPreflight.prompt("a gateway")).contains("a gateway")
    }
}
