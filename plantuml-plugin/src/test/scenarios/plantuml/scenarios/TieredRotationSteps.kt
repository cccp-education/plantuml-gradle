package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.apikey.ApiKeyEntry
import plantuml.apikey.ApiKeyPool
import plantuml.apikey.KeyTier
import plantuml.apikey.Provider
import plantuml.apikey.QuotaConfig
import plantuml.apikey.ResetPolicy
import plantuml.apikey.RotationStrategy
import plantuml.apikey.ServiceType

class TieredRotationSteps(private val world: PlantumlWorld) {

    private val pendingEntries = mutableListOf<ApiKeyEntry>()
    private var fallbackEnabled = true

    @Given("a pool with TIERED rotation strategy")
    fun aPoolWithTieredRotationStrategy() {
        pendingEntries.clear()
        fallbackEnabled = true
    }

    @Given("a pool with TIERED rotation strategy with fallback enabled")
    fun aPoolWithTieredRotationStrategyWithFallback() {
        pendingEntries.clear()
        fallbackEnabled = true
    }

    @Given("a pool with TIERED rotation strategy with fallback disabled")
    fun aPoolWithTieredRotationStrategyWithFallbackDisabled() {
        pendingEntries.clear()
        fallbackEnabled = false
    }

    @Given("the pool contains an ENTERPRISE key {string}")
    fun thePoolContainsAnEnterpriseKey(id: String) {
        pendingEntries.add(entry(id, KeyTier.ENTERPRISE))
    }

    @Given("the pool contains a PRO key {string}")
    fun thePoolContainsAProKey(id: String) {
        pendingEntries.add(entry(id, KeyTier.PRO))
    }

    @Given("the pool contains a FREE key {string}")
    fun thePoolContainsAFreeKey(id: String) {
        pendingEntries.add(entry(id, KeyTier.FREE))
    }

    @Given("the pool contains an ENTERPRISE key {string} with weight {int}")
    fun thePoolContainsAnEnterpriseKeyWithWeight(id: String, weight: Int) {
        pendingEntries.add(entry(id, KeyTier.ENTERPRISE, weight = weight))
    }

    @Given("the pool contains an ENTERPRISE key {string} with quota limit {int} and threshold {int}")
    fun thePoolContainsAnEnterpriseKeyWithQuota(id: String, limit: Int, threshold: Int) {
        pendingEntries.add(entry(id, KeyTier.ENTERPRISE, quota = quota(limit, threshold)))
    }

    @Given("the pool contains a PRO key {string} with quota limit {int} and threshold {int}")
    fun thePoolContainsAProKeyWithQuota(id: String, limit: Int, threshold: Int) {
        pendingEntries.add(entry(id, KeyTier.PRO, quota = quota(limit, threshold)))
    }

    @Given("the pool contains a FREE key {string} with quota limit {int} and threshold {int}")
    fun thePoolContainsAFreeKeyWithQuota(id: String, limit: Int, threshold: Int) {
        pendingEntries.add(entry(id, KeyTier.FREE, quota = quota(limit, threshold)))
    }

    @When("I select the next key")
    fun iSelectTheNextKey() {
        buildPool()
        world.tieredSelectedKeys.add(world.tieredPool!!.getNextKey())
    }

    @When("I select the next key {int} times")
    fun iSelectTheNextKeyTimes(times: Int) {
        buildPool()
        repeat(times) {
            world.tieredSelectedKeys.add(world.tieredPool!!.getNextKey())
        }
    }

    @Then("the selected key should be {string}")
    fun theSelectedKeyShouldBe(id: String) {
        assertThat(world.tieredSelectedKeys).isNotEmpty
        assertThat(world.tieredSelectedKeys.last().id).isEqualTo(id)
    }

    @Then("the {int}st selected key should be {string}")
    fun theFirstSelectedKeyShouldBe(index: Int, id: String) {
        assertThat(world.tieredSelectedKeys).hasSizeGreaterThanOrEqualTo(index)
        assertThat(world.tieredSelectedKeys[index - 1].id).isEqualTo(id)
    }

    @Then("the {int}nd selected key should be {string}")
    fun theSecondSelectedKeyShouldBe(index: Int, id: String) {
        assertThat(world.tieredSelectedKeys).hasSizeGreaterThanOrEqualTo(index)
        assertThat(world.tieredSelectedKeys[index - 1].id).isEqualTo(id)
    }

    @Then("the {int}rd selected key should be {string}")
    fun theThirdSelectedKeyShouldBe(index: Int, id: String) {
        assertThat(world.tieredSelectedKeys).hasSizeGreaterThanOrEqualTo(index)
        assertThat(world.tieredSelectedKeys[index - 1].id).isEqualTo(id)
    }

    private fun buildPool() {
        if (world.tieredPool == null) {
            world.tieredPool = ApiKeyPool(
                entries = pendingEntries.toList(),
                rotationStrategy = RotationStrategy.TIERED,
                fallbackEnabled = fallbackEnabled,
                autoResetEnabled = false
            )
        }
    }

    private fun entry(
        id: String,
        tier: KeyTier,
        weight: Int = 1,
        quota: QuotaConfig = QuotaConfig()
    ): ApiKeyEntry = ApiKeyEntry(
        id = id,
        email = "t@e.com",
        name = "Test $id",
        keyRef = "REF_$id",
        provider = Provider.GOOGLE,
        services = listOf(ServiceType.CHAT_COMPLETION),
        tier = tier,
        weight = weight,
        quota = quota
    )

    private fun quota(limit: Int, threshold: Int): QuotaConfig = QuotaConfig(
        limitValue = limit.toLong(),
        thresholdPercent = threshold,
        resetPolicy = ResetPolicy.MANUAL
    )
}