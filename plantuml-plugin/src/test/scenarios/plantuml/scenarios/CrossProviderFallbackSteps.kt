package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.apikey.ApiKeyEntry
import plantuml.apikey.ApiKeyPool
import plantuml.apikey.CrossProviderFallbackOrchestrator
import plantuml.apikey.KeyTier
import plantuml.apikey.Provider
import plantuml.apikey.QuotaConfig
import plantuml.apikey.ResetPolicy
import plantuml.apikey.RotationStrategy
import plantuml.apikey.ServiceType

class CrossProviderFallbackSteps(private val world: PlantumlWorld) {

    private val pendingProviderPools = mutableMapOf<String, MutableList<ApiKeyEntry>>()
    private var fallbackOrder: List<String> = emptyList()

    @Given("a cross-provider orchestrator with fallback order {string}")
    fun aCrossProviderOrchestratorWithFallbackOrder(orderCsv: String) {
        pendingProviderPools.clear()
        world.crossProviderSelectedKeys.clear()
        world.crossProviderOrchestrator = null
        fallbackOrder = orderCsv.split(",").map { it.trim() }
    }

    @Given("the {string} provider pool contains a FREE key {string}")
    fun theProviderPoolContainsAFreeKey(providerName: String, keyId: String) {
        addEntry(providerName, keyId, KeyTier.FREE)
    }

    @Given("the {string} provider pool contains a FREE key {string} with quota limit {int} and threshold {int}")
    fun theProviderPoolContainsAFreeKeyWithQuota(
        providerName: String,
        keyId: String,
        limit: Int,
        threshold: Int
    ) {
        addEntry(providerName, keyId, KeyTier.FREE, quota(limit, threshold))
    }

    @When("I select the next cross-provider key")
    fun iSelectTheNextCrossProviderKey() {
        buildOrchestrator()
        val selected = world.crossProviderOrchestrator!!.selectKey()
        if (selected != null) {
            world.crossProviderSelectedKeys.add(selected)
        }
    }

    @When("I select the next cross-provider key {int} times")
    fun iSelectTheNextCrossProviderKeyTimes(times: Int) {
        buildOrchestrator()
        repeat(times) {
            val selected = world.crossProviderOrchestrator!!.selectKey()
            if (selected != null) {
                world.crossProviderSelectedKeys.add(selected)
            }
        }
    }

    @Then("the selected cross-provider key should be {string}")
    fun theSelectedCrossProviderKeyShouldBe(keyId: String) {
        assertThat(world.crossProviderSelectedKeys).isNotEmpty
        assertThat(world.crossProviderSelectedKeys.last().id).isEqualTo(keyId)
    }

    @Then("the {int}st cross-provider key should be {string}")
    fun theFirstCrossProviderKeyShouldBe(index: Int, keyId: String) {
        assertThat(world.crossProviderSelectedKeys).hasSizeGreaterThanOrEqualTo(index)
        assertThat(world.crossProviderSelectedKeys[index - 1].id).isEqualTo(keyId)
    }

    @Then("the {int}nd cross-provider key should be {string}")
    fun theSecondCrossProviderKeyShouldBe(index: Int, keyId: String) {
        assertThat(world.crossProviderSelectedKeys).hasSizeGreaterThanOrEqualTo(index)
        assertThat(world.crossProviderSelectedKeys[index - 1].id).isEqualTo(keyId)
    }

    @Then("the {int}rd cross-provider key should be {string}")
    fun theThirdCrossProviderKeyShouldBe(index: Int, keyId: String) {
        assertThat(world.crossProviderSelectedKeys).hasSizeGreaterThanOrEqualTo(index)
        assertThat(world.crossProviderSelectedKeys[index - 1].id).isEqualTo(keyId)
    }

    @Then("the {int}th cross-provider key should be {string}")
    fun theNthCrossProviderKeyShouldBe(index: Int, keyId: String) {
        assertThat(world.crossProviderSelectedKeys).hasSizeGreaterThanOrEqualTo(index)
        assertThat(world.crossProviderSelectedKeys[index - 1].id).isEqualTo(keyId)
    }

    @Then("the {int}th cross-provider key should be absent")
    fun theNthCrossProviderKeyShouldBeAbsent(index: Int) {
        assertThat(world.crossProviderSelectedKeys).hasSizeLessThan(index)
    }

    private fun buildOrchestrator() {
        if (world.crossProviderOrchestrator == null) {
            val pools = pendingProviderPools.mapValues { (_, entries) ->
                ApiKeyPool(entries.toList(), RotationStrategy.TIERED, autoResetEnabled = false)
            }
            world.crossProviderOrchestrator = CrossProviderFallbackOrchestrator(pools, fallbackOrder)
        }
    }

    private fun addEntry(
        providerName: String,
        keyId: String,
        tier: KeyTier,
        quota: QuotaConfig = QuotaConfig(limitValue = 100, thresholdPercent = 50)
    ) {
        val entries = pendingProviderPools.getOrPut(providerName) { mutableListOf() }
        entries.add(entry(keyId, resolveProvider(providerName), tier, quota))
    }

    private fun entry(
        id: String,
        provider: Provider,
        tier: KeyTier,
        quota: QuotaConfig
    ): ApiKeyEntry = ApiKeyEntry(
        id = id,
        email = "t@e.com",
        name = "Test $id",
        keyRef = "REF_$id",
        provider = provider,
        services = listOf(ServiceType.CHAT_COMPLETION),
        tier = tier,
        quota = quota
    )

    private fun quota(limit: Int, threshold: Int): QuotaConfig = QuotaConfig(
        limitValue = limit.toLong(),
        thresholdPercent = threshold,
        resetPolicy = ResetPolicy.MANUAL
    )

    private fun resolveProvider(name: String): Provider = when (name.lowercase()) {
        "gemini" -> Provider.GOOGLE
        "mistral" -> Provider.MISTRAL
        "ollama" -> Provider.OLLAMA
        "openai" -> Provider.OPENAI
        "claude" -> Provider.ANTHROPIC
        "huggingface" -> Provider.HUGGINGFACE
        "groq" -> Provider.GROQ
        else -> Provider.UNKNOWN
    }
}