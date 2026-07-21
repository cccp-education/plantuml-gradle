package plantuml.apikey

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreemiumWeightCalculatorTest {

    private val tracker = QuotaTracker()

    @Test
    fun `disabled when freemiumRatio is zero`() {
        val calc = FreemiumWeightCalculator(0.0, tracker)
        assertFalse(calc.isEnabled)
    }

    @Test
    fun `enabled when freemiumRatio is positive`() {
        val calc = FreemiumWeightCalculator(0.3, tracker)
        assertTrue(calc.isEnabled)
    }

    @Test
    fun `returns static weight when disabled`() {
        val calc = FreemiumWeightCalculator(0.0, tracker)
        val entry = ApiKeyEntry(
            id = "k1", email = "a@b.com", name = "k1",
            keyRef = "K1", provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            weight = 5
        )
        assertEquals(5, calc.calculateWeight(entry))
    }

    @Test
    fun `rejects invalid freemiumRatio`() {
        assertThrows<IllegalArgumentException> {
            FreemiumWeightCalculator(1.5, tracker)
        }
        assertThrows<IllegalArgumentException> {
            FreemiumWeightCalculator(-0.1, tracker)
        }
    }

    @Test
    fun `free key gets freemiumRatio multiplier`() {
        val calc = FreemiumWeightCalculator(0.3, tracker)
        val entry = ApiKeyEntry(
            id = "free1", email = "f@b.com", name = "free1",
            keyRef = "F1", provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            tier = KeyTier.FREE, weight = 10
        )
        val weight = calc.calculateWeight(entry)
        assertEquals(3, weight)
    }

    @Test
    fun `enterprise key gets inverse freemiumRatio multiplier`() {
        val calc = FreemiumWeightCalculator(0.3, tracker)
        val entry = ApiKeyEntry(
            id = "ent1", email = "e@b.com", name = "ent1",
            keyRef = "E1", provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            tier = KeyTier.ENTERPRISE, weight = 10
        )
        val weight = calc.calculateWeight(entry)
        assertEquals(7, weight)
    }

    @Test
    fun `pro key gets inverse freemiumRatio multiplier`() {
        val calc = FreemiumWeightCalculator(0.3, tracker)
        val entry = ApiKeyEntry(
            id = "pro1", email = "p@b.com", name = "pro1",
            keyRef = "P1", provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            tier = KeyTier.PRO, weight = 10
        )
        val weight = calc.calculateWeight(entry)
        assertEquals(7, weight)
    }

    @Test
    fun `weight decreases as usage increases`() {
        val calc = FreemiumWeightCalculator(0.5, tracker)
        val entry = ApiKeyEntry(
            id = "usage1", email = "u@b.com", name = "usage1",
            keyRef = "U1", provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            tier = KeyTier.FREE, weight = 100,
            quota = QuotaConfig(limitValue = 100)
        )
        val initialWeight = calc.calculateWeight(entry)
        assertEquals(50, initialWeight)

        tracker.trackUsage("usage1")
        tracker.trackUsage("usage1")
        val afterUsage = calc.calculateWeight(entry)
        assertTrue(afterUsage < initialWeight)
    }

    @Test
    fun `weight floors at zero when quota exhausted`() {
        val calc = FreemiumWeightCalculator(0.5, tracker)
        val entry = ApiKeyEntry(
            id = "exhausted", email = "x@b.com", name = "exhausted",
            keyRef = "X1", provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            tier = KeyTier.FREE, weight = 10,
            quota = QuotaConfig(limitValue = 10)
        )
        repeat(10) { tracker.trackUsage("exhausted") }
        assertEquals(0, calc.calculateWeight(entry))
    }

    @Test
    fun `freemiumRatio 1_0 means free keys get full weight paid keys get zero`() {
        val calc = FreemiumWeightCalculator(1.0, tracker)
        val freeEntry = ApiKeyEntry(
            id = "free2", email = "f2@b.com", name = "free2",
            keyRef = "F2", provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            tier = KeyTier.FREE, weight = 10
        )
        val paidEntry = ApiKeyEntry(
            id = "paid2", email = "p2@b.com", name = "paid2",
            keyRef = "P2", provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            tier = KeyTier.ENTERPRISE, weight = 10
        )
        assertEquals(10, calc.calculateWeight(freeEntry))
        assertEquals(0, calc.calculateWeight(paidEntry))
    }
}
