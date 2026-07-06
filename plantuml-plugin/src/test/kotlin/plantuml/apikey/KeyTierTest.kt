package plantuml.apikey

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyTierTest {

    @Test
    fun `KeyTier enum should have 3 values`() {
        val tiers = KeyTier.entries
        assertEquals(3, tiers.size)
    }

    @Test
    fun `KeyTier enum values should have correct names`() {
        assertEquals("ENTERPRISE", KeyTier.ENTERPRISE.name)
        assertEquals("PRO", KeyTier.PRO.name)
        assertEquals("FREE", KeyTier.FREE.name)
    }

    @Test
    fun `ApiKeyEntry should expose tier with default FREE`() {
        val entry = ApiKeyEntry(
            id = "k1",
            email = "t@e.com",
            name = "Test",
            keyRef = "REF",
            provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION)
        )
        assertEquals(KeyTier.FREE, entry.tier)
    }

    @Test
    fun `ApiKeyEntry should accept explicit ENTERPRISE tier`() {
        val entry = ApiKeyEntry(
            id = "k1",
            email = "t@e.com",
            name = "Test",
            keyRef = "REF",
            provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            tier = KeyTier.ENTERPRISE
        )
        assertEquals(KeyTier.ENTERPRISE, entry.tier)
    }

    @Test
    fun `ApiKeyEntry should expose weight with default 1`() {
        val entry = ApiKeyEntry(
            id = "k1",
            email = "t@e.com",
            name = "Test",
            keyRef = "REF",
            provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION)
        )
        assertEquals(1, entry.weight)
    }

    @Test
    fun `ApiKeyEntry should accept explicit weight`() {
        val entry = ApiKeyEntry(
            id = "k1",
            email = "t@e.com",
            name = "Test",
            keyRef = "REF",
            provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            weight = 5
        )
        assertEquals(5, entry.weight)
    }
}