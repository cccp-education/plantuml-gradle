package plantuml.service

import org.junit.jupiter.api.Test
import plantuml.ApiKeyPoolEntry
import plantuml.PlantumlConfig
import plantuml.PoolQuotaConfig
import plantuml.apikey.AuditEventType
import plantuml.apikey.Provider
import java.time.LocalDateTime
import kotlin.test.assertTrue

/**
 * Tests for LlmService audit logging functionality.
 *
 * Verifies that LlmService properly logs:
 * - LLM API calls (success/failure)
 * - Key rotations
 * - Quota events
 */
class LlmServiceAuditTest {

    @Test
    fun `should log LLM call on successful chat model creation`() {
        val config = PlantumlConfig().copy(
            langchain4j = PlantumlConfig().langchain4j.copy(
                model = "ollama",
                ollama = PlantumlConfig().langchain4j.ollama.copy(
                    baseUrl = "http://localhost:11438",
                    modelName = "llama2"
                )
            )
        )
        
        val auditLogger = plantuml.apikey.QuotaAuditLogger()
        val llmService = LlmService(config, auditLogger)

        llmService.createChatModel()

        val logs = auditLogger.getLogsByType(AuditEventType.INFO)
        assertTrue(logs.isNotEmpty(), "Should log LLM call")
        assertTrue(logs.any { it.message.contains("OLLAMA", ignoreCase = true) })
    }

    @Test
    fun `should log when using API key from pool`() {
        val poolEntry = ApiKeyPoolEntry(
            id = "test-key-1",
            email = "test@example.com",
            name = "Test Key",
            keyRef = "test-api-key",
            provider = "OPENAI",
            services = listOf("CHAT_COMPLETION"),
            quota = PoolQuotaConfig(
                limitValue = 1000,
                thresholdPercent = 80
            )
        )
        val config = PlantumlConfig().copy(
            langchain4j = PlantumlConfig().langchain4j.copy(
                model = "openai",
                openai = PlantumlConfig().langchain4j.openai.copy(
                    pool = listOf(poolEntry)
                )
            )
        )
        
        val auditLogger = plantuml.apikey.QuotaAuditLogger()
        val llmService = LlmService(config, auditLogger)

        llmService.createChatModel()

        val logs = auditLogger.getLogs()
        assertTrue(logs.isNotEmpty(), "Should log when using pool key")
    }

    @Test
    fun `should log key rotation event`() {
        val poolEntry1 = ApiKeyPoolEntry(
            id = "key-1",
            email = "test1@example.com",
            name = "Key 1",
            keyRef = "key-ref-1",
            provider = "OPENAI",
            services = listOf("CHAT_COMPLETION"),
            quota = PoolQuotaConfig(limitValue = 10, thresholdPercent = 80)
        )
        val poolEntry2 = ApiKeyPoolEntry(
            id = "key-2",
            email = "test2@example.com",
            name = "Key 2",
            keyRef = "key-ref-2",
            provider = "OPENAI",
            services = listOf("CHAT_COMPLETION"),
            quota = PoolQuotaConfig(limitValue = 10, thresholdPercent = 80)
        )
        val config = PlantumlConfig().copy(
            langchain4j = PlantumlConfig().langchain4j.copy(
                model = "openai",
                openai = PlantumlConfig().langchain4j.openai.copy(
                    pool = listOf(poolEntry1, poolEntry2)
                )
            )
        )
        
        val auditLogger = plantuml.apikey.QuotaAuditLogger()
        val llmService = LlmService(config, auditLogger)

        llmService.createChatModel()
        llmService.createChatModel()

        val logs = auditLogger.getLogs()
        assertTrue(logs.size >= 2, "Should log each key usage from pool")
    }

    @Test
    fun `should log quota exceeded event`() {
        val poolEntry = ApiKeyPoolEntry(
            id = "limited-key",
            email = "test@example.com",
            name = "Limited Key",
            keyRef = "limited-key-ref",
            provider = "OPENAI",
            services = listOf("CHAT_COMPLETION"),
            quota = PoolQuotaConfig(limitValue = 1, thresholdPercent = 80)
        )
        val config = PlantumlConfig().copy(
            langchain4j = PlantumlConfig().langchain4j.copy(
                model = "openai",
                openai = PlantumlConfig().langchain4j.openai.copy(
                    pool = listOf(poolEntry)
                )
            )
        )
        
        val auditLogger = plantuml.apikey.QuotaAuditLogger()
        val llmService = LlmService(config, auditLogger)

        llmService.createChatModel()
        llmService.createChatModel()

        val logs = auditLogger.getLogs()
        assertTrue(logs.isNotEmpty(), "Should log usage events")
    }

    @Test
    fun `should include provider name in audit logs`() {
        val config = PlantumlConfig().copy(
            langchain4j = PlantumlConfig().langchain4j.copy(
                model = "openai"
            )
        )
        val auditLogger = plantuml.apikey.QuotaAuditLogger()
        val llmService = LlmService(config, auditLogger)

        llmService.createChatModel()

        val logs = auditLogger.getLogs()
        assertTrue(logs.any { it.provider == Provider.OPENAI || it.message.contains("OPENAI", ignoreCase = true) })
    }

    @Test
    fun `should include timestamp in audit logs`() {
        val config = PlantumlConfig().copy(
            langchain4j = PlantumlConfig().langchain4j.copy(
                model = "ollama",
                ollama = PlantumlConfig().langchain4j.ollama.copy(
                    baseUrl = "http://localhost:11438",
                    modelName = "llama2"
                )
            )
        )
        val auditLogger = plantuml.apikey.QuotaAuditLogger()
        val llmService = LlmService(config, auditLogger)

        val before = LocalDateTime.now()
        llmService.createChatModel()
        val after = LocalDateTime.now()

        val logs = auditLogger.getLogs()
        assertTrue(logs.isNotEmpty())
        logs.forEach { log ->
            assertTrue(log.timestamp.isAfter(before) || log.timestamp.isEqual(before))
            assertTrue(log.timestamp.isBefore(after) || log.timestamp.isEqual(after))
        }
    }

    @Test
    fun `should log usage count for each API call`() {
        val poolEntry = ApiKeyPoolEntry(
            id = "usage-key",
            email = "test@example.com",
            name = "Usage Key",
            keyRef = "usage-key-ref",
            provider = "MISTRAL",
            services = listOf("CHAT_COMPLETION"),
            quota = PoolQuotaConfig(limitValue = 100, thresholdPercent = 80)
        )
        val config = PlantumlConfig().copy(
            langchain4j = PlantumlConfig().langchain4j.copy(
                model = "mistral",
                mistral = PlantumlConfig().langchain4j.mistral.copy(
                    pool = listOf(poolEntry)
                )
            )
        )
        
        val auditLogger = plantuml.apikey.QuotaAuditLogger()
        val llmService = LlmService(config, auditLogger)

        llmService.createChatModel()
        llmService.createChatModel()
        llmService.createChatModel()

        val logs = auditLogger.getLogs()
        assertTrue(logs.size >= 3, "Should log each API call")
    }

    @Test
    fun `should disable audit logging when disabled`() {
        val config = PlantumlConfig().copy(
            langchain4j = PlantumlConfig().langchain4j.copy(
                model = "ollama"
            )
        )
        val disabledLogger = plantuml.apikey.QuotaAuditLogger(enabled = false)
        val llmService = LlmService(config, disabledLogger)

        llmService.createChatModel()

        assertTrue(disabledLogger.getLogs().isEmpty(), "Should not log when disabled")
    }

    @Test
    fun `should log error events gracefully`() {
        val config = PlantumlConfig().copy(
            langchain4j = PlantumlConfig().langchain4j.copy(
                model = "unknown"
            )
        )
        val auditLogger = plantuml.apikey.QuotaAuditLogger()
        val llmService = LlmService(config, auditLogger)

        llmService.createChatModel()

        val logs = auditLogger.getLogs()
        assertTrue(logs.isNotEmpty(), "Should log even for unknown providers")
    }
}
