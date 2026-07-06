package plantuml.apikey

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TieredRotationStrategyTest {

    private fun entry(
        id: String,
        tier: KeyTier = KeyTier.FREE,
        weight: Int = 1
    ): ApiKeyEntry = ApiKeyEntry(
        id = id,
        email = "t@e.com",
        name = "Test $id",
        keyRef = "REF_$id",
        provider = Provider.GOOGLE,
        services = listOf(ServiceType.CHAT_COMPLETION),
        tier = tier,
        weight = weight
    )

    @Test
    fun `select should return ENTERPRISE key first when present`() {
        val entries = listOf(
            entry("free1", KeyTier.FREE),
            entry("ent1", KeyTier.ENTERPRISE),
            entry("pro1", KeyTier.PRO)
        )
        val strategy = TieredRotationStrategy()

        val selected = strategy.select(entries)

        assertEquals("ent1", selected.id)
    }

    @Test
    fun `select should return PRO key when no ENTERPRISE available`() {
        val entries = listOf(
            entry("free1", KeyTier.FREE),
            entry("pro1", KeyTier.PRO),
            entry("free2", KeyTier.FREE)
        )
        val strategy = TieredRotationStrategy()

        val selected = strategy.select(entries)

        assertEquals("pro1", selected.id)
    }

    @Test
    fun `select should return FREE key when only FREE available`() {
        val entries = listOf(
            entry("free1", KeyTier.FREE),
            entry("free2", KeyTier.FREE)
        )
        val strategy = TieredRotationStrategy()

        val selected = strategy.select(entries)

        assertEquals("free1", selected.id)
    }

    @Test
    fun `select intra-tier should pick higher weight first`() {
        val entries = listOf(
            entry("ent1", KeyTier.ENTERPRISE, weight = 1),
            entry("ent2", KeyTier.ENTERPRISE, weight = 5)
        )
        val strategy = TieredRotationStrategy()

        val selected = strategy.select(entries)

        assertEquals("ent2", selected.id)
    }

    @Test
    fun `select should throw IllegalArgumentException when entries empty`() {
        val strategy = TieredRotationStrategy()

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            strategy.select(emptyList())
        }
    }
}