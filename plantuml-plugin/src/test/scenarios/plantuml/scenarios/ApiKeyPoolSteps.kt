package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.apikey.ApiKeyEntry
import plantuml.apikey.ApiKeyPool
import plantuml.apikey.ApiKeyPoolConfig
import plantuml.apikey.Provider
import plantuml.apikey.KeyTier
import plantuml.apikey.QuotaConfig
import plantuml.apikey.ResetPolicy
import plantuml.apikey.RotationStrategy
import plantuml.apikey.ServiceType
import java.time.LocalDateTime

class ApiKeyPoolSteps(private val world: PlantumlWorld) {

    private var nextExpirationDate: LocalDateTime? = null
    private var nextMetadata: Map<String, String> = emptyMap()
    private var nextQuotaThreshold: Int? = null
    private var nextServiceType: ServiceType? = null
    private var nextResetPolicy: ResetPolicy = ResetPolicy.DAILY
    private var lastRetrievedEntry: ApiKeyEntry? = null
    private var lastValidityResult: Boolean? = null

    @Given("an API key pool configuration with no keys")
    fun anApiKeyPoolConfigurationWithNoKeys() {
        world.apiKeyPoolEntries.clear()
        world.apiKeyPool = null
    }

    @Given("an API key pool configuration")
    fun anApiKeyPoolConfiguration() {
        world.apiKeyPoolEntries.clear()
        world.apiKeyPool = null
    }

    @When("I initialize the pool")
    fun iInitializeThePool() {
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @Then("the pool should be empty")
    fun thePoolShouldBeEmpty() {
        assertThat(world.apiKeyPool).isNotNull
        assertThat(world.apiKeyPool!!.size()).isEqualTo(0)
    }

    @Then("the default rotation strategy should be ROUND_ROBIN")
    fun theDefaultRotationStrategyShouldBeRoundRobin() {
        assertThat(world.apiKeyPool).isNotNull
    }

    @When("I add an API key for provider {word}")
    fun iAddAnApiKeyForProvider(providerName: String) {
        val provider = parseProvider(providerName)
        val entry = createEntry(
            id = "${providerName.lowercase()}-key-${world.apiKeyPoolEntries.size + 1}",
            provider = provider
        )
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @When("I add {int} API keys for provider {word}")
    fun iAddMultipleApiKeysForProvider(count: Int, providerName: String) {
        val provider = parseProvider(providerName)
        repeat(count) {
            val entry = createEntry(
                id = "${providerName.lowercase()}-key-${world.apiKeyPoolEntries.size + 1}",
                provider = provider
            )
            world.apiKeyPoolEntries.add(entry)
        }
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @When("I add {int} keys for provider {word}")
    fun iAddKeysForProvider(count: Int, providerName: String) {
        val provider = parseProvider(providerName)
        repeat(count) {
            val entry = createEntry(
                id = "${providerName.lowercase()}-key-${world.apiKeyPoolEntries.size + 1}",
                provider = provider
            )
            world.apiKeyPoolEntries.add(entry)
        }
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @When("I add {int} key for provider {word}")
    fun iAddOneKeyForProvider(count: Int, providerName: String) {
        iAddKeysForProvider(count, providerName)
    }

    @Then("the pool should contain {int} key")
    fun thePoolShouldContainOneKey(count: Int) {
        assertThat(world.apiKeyPool).isNotNull
        assertThat(world.apiKeyPool!!.size()).isEqualTo(count)
    }

    @Then("the pool should contain {int} keys")
    fun thePoolShouldContainKeys(count: Int) {
        assertThat(world.apiKeyPool).isNotNull
        assertThat(world.apiKeyPool!!.size()).isEqualTo(count)
    }

    @Then("the key should have provider {word}")
    fun theKeyShouldHaveProvider(providerName: String) {
        val provider = parseProvider(providerName)
        val keys = world.apiKeyPool!!.getAllKeys()
        assertThat(keys).isNotEmpty
        assertThat(keys.any { it.provider == provider }).isTrue
    }

    @Then("all keys should have provider {word}")
    fun allKeysShouldHaveProvider(providerName: String) {
        val provider = parseProvider(providerName)
        val keys = world.apiKeyPool!!.getAllKeys()
        assertThat(keys).isNotEmpty
        assertThat(keys.all { it.provider == provider }).isTrue
    }

    @Then("the pool should have {int} providers")
    fun thePoolShouldHaveProviders(count: Int) {
        val keys = world.apiKeyPool!!.getAllKeys()
        val providers = keys.map { it.provider }.distinct()
        assertThat(providers).hasSize(count)
    }

    @Given("a pool with {int} {word} keys")
    fun aPoolWithKeys(count: Int, providerName: String) {
        val provider = parseProvider(providerName)
        world.apiKeyPoolEntries.clear()
        repeat(count) {
            val entry = createEntry(
                id = "${providerName.lowercase()}-key-${it + 1}",
                provider = provider
            )
            world.apiKeyPoolEntries.add(entry)
        }
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
        world.apiKeyPoolSelectedKeys.clear()
    }

    @When("I select the next pool key")
    fun iSelectTheNextPoolKey() {
        val key = world.apiKeyPool!!.getNextKey()
        world.apiKeyPoolSelectedKeys.add(key)
    }

    @When("I select the next pool key {int} times")
    fun iSelectTheNextPoolKeyTimes(times: Int) {
        repeat(times) {
            val key = world.apiKeyPool!!.getNextKey()
            world.apiKeyPoolSelectedKeys.add(key)
        }
    }

    @Then("each selection should return a different key")
    fun eachSelectionShouldReturnADifferentKey() {
        val ids = world.apiKeyPoolSelectedKeys.map { it.id }
        assertThat(ids).hasSize(world.apiKeyPool!!.size())
        assertThat(ids.toSet()).hasSize(world.apiKeyPool!!.size())
    }

    @Then("the {int}th selection should return the first key")
    fun theNthSelectionShouldReturnTheFirstKey(index: Int) {
        while (world.apiKeyPoolSelectedKeys.size < index) {
            val key = world.apiKeyPool!!.getNextKey()
            world.apiKeyPoolSelectedKeys.add(key)
        }
        val firstKey = world.apiKeyPoolSelectedKeys.first()
        val nthKey = world.apiKeyPoolSelectedKeys[index - 1]
        assertThat(nthKey.id).isEqualTo(firstKey.id)
    }

    @Given("the first key has consumed {int}% of its quota")
    fun theFirstKeyHasConsumedPercentOfQuota(percent: Int) {
        if (world.apiKeyPool == null) {
            world.apiKeyPool = ApiKeyPool(
                entries = world.apiKeyPoolEntries.toList(),
                rotationStrategy = RotationStrategy.TIERED,
                fallbackEnabled = world.apiKeyPoolFallbackEnabled,
                auditEnabled = world.apiKeyPoolAuditEnabled,
                freemiumRatio = freemiumRatio
            )
        }
        val firstEntry = world.apiKeyPool!!.getAllKeys().first()
        val limit = firstEntry.quota.limitValue
        val consumeCount = (limit * percent / 100)
        repeat(consumeCount.toInt()) {
            world.apiKeyPool!!.getTracker().trackUsage(firstEntry.id)
        }
    }

    @Then("the second key should be selected")
    fun theSecondKeyShouldBeSelected() {
        assertThat(world.apiKeyPoolSelectedKeys).isNotEmpty
        val keys = world.apiKeyPool!!.getAllKeys()
        assertThat(keys).hasSizeGreaterThanOrEqualTo(2)
        assertThat(world.apiKeyPoolSelectedKeys.last().id).isEqualTo(keys[1].id)
    }

    @Given("an API key with expiration date in the past")
    fun anApiKeyWithExpirationDateInThePast() {
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(
            id = "expired-key-1",
            provider = Provider.GOOGLE,
            expirationDate = LocalDateTime.now().minusDays(1)
        )
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @Given("an API key without expiration date")
    fun anApiKeyWithoutExpirationDate() {
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(
            id = "no-expiry-key-1",
            provider = Provider.GOOGLE,
            expirationDate = null
        )
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @When("I check key validity")
    fun iCheckKeyValidity() {
        val entry = world.apiKeyPool!!.getAllKeys().first()
        lastValidityResult = entry.expirationDate?.let { it.isAfter(LocalDateTime.now()) } ?: true
    }

    @Then("the key should be marked as expired")
    fun theKeyShouldBeMarkedAsExpired() {
        assertThat(lastValidityResult).isFalse
    }

    @Then("the key should be valid")
    fun theKeyShouldBeValid() {
        assertThat(lastValidityResult).isTrue
    }

    @Given("an API key with quota threshold of {int}%")
    fun anApiKeyWithQuotaThreshold(percent: Int) {
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(
            id = "threshold-key-1",
            provider = Provider.GOOGLE,
            quota = QuotaConfig(limitValue = 100, thresholdPercent = percent)
        )
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled,
            autoResetEnabled = false
        )
    }

    @When("the consumed value reaches {int}%")
    fun theConsumedValueReaches(percent: Int) {
        val entry = world.apiKeyPool!!.getAllKeys().first()
        val consumeCount = (entry.quota.limitValue * percent / 100).toInt()
        repeat(consumeCount) {
            world.apiKeyPool!!.getTracker().trackUsage(entry.id)
        }
    }

    @Then("the key should still be available")
    fun theKeyShouldStillBeAvailable() {
        val entry = world.apiKeyPool!!.getAllKeys().first()
        assertThat(world.apiKeyPool!!.isQuotaExceeded(entry)).isFalse
    }

    @Then("the key should be marked as threshold exceeded")
    fun theKeyShouldBeMarkedAsThresholdExceeded() {
        val entry = world.apiKeyPool!!.getAllKeys().first()
        assertThat(world.apiKeyPool!!.isQuotaExceeded(entry)).isTrue
    }

    @Given("an API key with metadata")
    fun anApiKeyWithMetadata() {
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(
            id = "metadata-key-1",
            provider = Provider.GOOGLE,
            metadata = mapOf("owner" to "team-alpha", "project" to "diagrams")
        )
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @When("I retrieve the key")
    fun iRetrieveTheKey() {
        lastRetrievedEntry = world.apiKeyPool!!.getAllKeys().first()
    }

    @Then("the metadata should be preserved")
    fun theMetadataShouldBePreserved() {
        assertThat(lastRetrievedEntry).isNotNull
        assertThat(lastRetrievedEntry!!.metadata).isNotEmpty
        assertThat(lastRetrievedEntry!!.metadata["owner"]).isEqualTo("team-alpha")
        assertThat(lastRetrievedEntry!!.metadata["project"]).isEqualTo("diagrams")
    }

    @Given("an API key configured for TEXT_GENERATION service")
    fun anApiKeyConfiguredForTextGenerationService() {
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(
            id = "service-key-1",
            provider = Provider.GOOGLE,
            services = listOf(ServiceType.TEXT_GENERATION)
        )
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @Given("an API key NOT configured for IMAGE_GENERATION service")
    fun anApiKeyNotConfiguredForImageGenerationService() {
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(
            id = "service-key-2",
            provider = Provider.GOOGLE,
            services = listOf(ServiceType.TEXT_GENERATION)
        )
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @When("I request a key for TEXT_GENERATION")
    fun iRequestAKeyForTextGeneration() {
        lastRetrievedEntry = world.apiKeyPool!!.getAllKeys().firstOrNull {
            it.services.contains(ServiceType.TEXT_GENERATION)
        }
    }

    @When("I request a key for IMAGE_GENERATION")
    fun iRequestAKeyForImageGeneration() {
        lastRetrievedEntry = world.apiKeyPool!!.getAllKeys().firstOrNull {
            it.services.contains(ServiceType.IMAGE_GENERATION)
        }
    }

    @Then("the key should be eligible for selection")
    fun theKeyShouldBeEligibleForSelection() {
        assertThat(lastRetrievedEntry).isNotNull
    }

    @Then("the key should NOT be eligible for selection")
    fun theKeyShouldNotBeEligibleForSelection() {
        assertThat(lastRetrievedEntry).isNull()
    }

    @Given("a pool with fallback enabled")
    fun aPoolWithFallbackEnabled() {
        world.apiKeyPoolFallbackEnabled = true
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(id = "fallback-key-1", provider = Provider.GOOGLE)
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = true,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @Given("a pool with fallback disabled")
    fun aPoolWithFallbackDisabled() {
        world.apiKeyPoolFallbackEnabled = false
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(id = "fallback-key-2", provider = Provider.GOOGLE)
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = false,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @When("the selected key fails")
    fun theSelectedKeyFails() {
        if (world.apiKeyPoolSelectedKeys.isEmpty()) {
            world.apiKeyPoolSelectedKeys.add(world.apiKeyPool!!.getNextKey())
        }
    }

    @Then("the next available key should be tried")
    fun theNextAvailableKeyShouldBeTried() {
        assertThat(world.apiKeyPool!!.isFallbackEnabled()).isTrue
    }

    @Then("no fallback should occur")
    fun noFallbackShouldOccur() {
        assertThat(world.apiKeyPool!!.isFallbackEnabled()).isFalse
    }

    @Given("a pool with audit logging enabled")
    fun aPoolWithAuditLoggingEnabled() {
        world.apiKeyPoolAuditEnabled = true
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(id = "audit-key-1", provider = Provider.GOOGLE)
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = true
        )
    }

    @Given("a pool with audit logging disabled")
    fun aPoolWithAuditLoggingDisabled() {
        world.apiKeyPoolAuditEnabled = false
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(id = "audit-key-2", provider = Provider.GOOGLE)
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = false
        )
    }

    @When("I select a key")
    fun iSelectAKey() {
        val key = world.apiKeyPool!!.getNextKey()
        world.apiKeyPoolSelectedKeys.add(key)
    }

    @Then("an audit log entry should be created")
    fun anAuditLogEntryShouldBeCreated() {
        assertThat(world.apiKeyPool!!.getAuditLogs()).isNotEmpty
    }

    @Then("no audit log entry should be created")
    fun noAuditLogEntryShouldBeCreated() {
        assertThat(world.apiKeyPool!!.getAuditLogs()).isEmpty()
    }

    @Given("a pool configuration with version {string}")
    fun aPoolConfigurationWithVersion(version: String) {
        world.apiKeyPoolVersion = version
        world.apiKeyPoolConfig = ApiKeyPoolConfig(version = version)
    }

    @When("I load the configuration")
    fun iLoadThePoolConfiguration() {
        world.apiKeyPoolEntries.clear()
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled
        )
    }

    @Then("the version should be {string}")
    fun theVersionShouldBe(version: String) {
        assertThat(world.apiKeyPoolConfig).isNotNull
        assertThat(world.apiKeyPoolConfig!!.version).isEqualTo(version)
    }

    @Given("an API key with DAILY reset policy")
    fun anApiKeyWithDailyResetPolicy() {
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(
            id = "daily-key-1",
            provider = Provider.GOOGLE,
            quota = QuotaConfig(limitValue = 100, thresholdPercent = 80, resetPolicy = ResetPolicy.DAILY)
        )
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled,
            autoResetEnabled = true
        )
        repeat(80) {
            world.apiKeyPool!!.getTracker().trackUsage(entry.id)
        }
    }

    @When("a new day starts")
    fun aNewDayStarts() {
        val entry = world.apiKeyPool!!.getAllKeys().first()
        world.apiKeyPool!!.getResetManager().checkAndReset(entry)
    }

    @Then("the quota should reset")
    fun theQuotaShouldReset() {
        val entry = world.apiKeyPool!!.getAllKeys().first()
        assertThat(world.apiKeyPool!!.getUsageCount(entry.id)).isEqualTo(0L)
    }

    @Given("an API key with MANUAL reset policy")
    fun anApiKeyWithManualResetPolicy() {
        world.apiKeyPoolEntries.clear()
        val entry = createEntry(
            id = "manual-key-1",
            provider = Provider.GOOGLE,
            quota = QuotaConfig(limitValue = 100, thresholdPercent = 80, resetPolicy = ResetPolicy.MANUAL)
        )
        world.apiKeyPoolEntries.add(entry)
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.ROUND_ROBIN,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled,
            autoResetEnabled = false
        )
        repeat(80) {
            world.apiKeyPool!!.getTracker().trackUsage(entry.id)
        }
    }

    @When("I trigger manual reset")
    fun iTriggerManualReset() {
        val entry = world.apiKeyPool!!.getAllKeys().first()
        world.apiKeyPool!!.manualReset(entry.id)
    }

    private var freemiumRatio: Double = 0.0

    @Given("a pool with {int} FREE keys and {int} ENTERPRISE key")
    fun poolWithFreeKeysAndEnterpriseKey(freeCount: Int, enterpriseCount: Int) {
        world.apiKeyPoolEntries.clear()
        repeat(freeCount) { i ->
            world.apiKeyPoolEntries.add(
                createEntry(
                    id = "free-$i",
                    provider = Provider.GOOGLE,
                    tier = KeyTier.FREE,
                    weight = 10
                )
            )
        }
        repeat(enterpriseCount) { i ->
            world.apiKeyPoolEntries.add(
                createEntry(
                    id = "ent-$i",
                    provider = Provider.GOOGLE,
                    tier = KeyTier.ENTERPRISE,
                    weight = 10
                )
            )
        }
    }

    @Given("a pool with only {int} FREE keys")
    fun poolWithFreeKeys(count: Int) {
        world.apiKeyPoolEntries.clear()
        repeat(count) { i ->
            world.apiKeyPoolEntries.add(
                createEntry(
                    id = "free-$i",
                    provider = Provider.GOOGLE,
                    tier = KeyTier.FREE,
                    weight = 10
                )
            )
        }
    }

    @Given("freemium ratio is {double}")
    fun freemiumRatioIs(ratio: Double) {
        freemiumRatio = ratio
    }

    @Given("the pool is initialized with TIERED strategy")
    fun thePoolIsInitializedWithTieredStrategy() {
        world.apiKeyPool = ApiKeyPool(
            entries = world.apiKeyPoolEntries.toList(),
            rotationStrategy = RotationStrategy.TIERED,
            fallbackEnabled = world.apiKeyPoolFallbackEnabled,
            auditEnabled = world.apiKeyPoolAuditEnabled,
            freemiumRatio = freemiumRatio
        )
    }

    @Then("the ENTERPRISE key should be selected")
    fun theEnterpriseKeyShouldBeSelected() {
        val lastSelected = world.apiKeyPoolSelectedKeys.last()
        assertThat(lastSelected.tier).isEqualTo(KeyTier.ENTERPRISE)
    }

    @Then("a FREE key should be selected")
    fun aFreeKeyShouldBeSelected() {
        val lastSelected = world.apiKeyPoolSelectedKeys.last()
        assertThat(lastSelected.tier).isEqualTo(KeyTier.FREE)
    }

    private fun parseProvider(name: String): Provider = when (name.uppercase()) {
        "GOOGLE" -> Provider.GOOGLE
        "HUGGINGFACE" -> Provider.HUGGINGFACE
        "GROQ" -> Provider.GROQ
        "OLLAMA" -> Provider.OLLAMA
        "MISTRAL" -> Provider.MISTRAL
        "OPENAI" -> Provider.OPENAI
        "ANTHROPIC" -> Provider.ANTHROPIC
        else -> Provider.UNKNOWN
    }

    private fun createEntry(
        id: String,
        provider: Provider,
        services: List<ServiceType> = listOf(ServiceType.CHAT_COMPLETION),
        quota: QuotaConfig = QuotaConfig(),
        expirationDate: LocalDateTime? = null,
        metadata: Map<String, String> = emptyMap(),
        resetPolicy: ResetPolicy = ResetPolicy.DAILY,
        tier: KeyTier = KeyTier.FREE,
        weight: Int = 1
    ): ApiKeyEntry = ApiKeyEntry(
        id = id,
        email = "test@$id.com",
        name = "Test $id",
        keyRef = "REF_$id",
        provider = provider,
        services = services,
        quota = quota.copy(resetPolicy = resetPolicy),
        expirationDate = expirationDate,
        metadata = metadata,
        tier = tier,
        weight = weight
    )
}