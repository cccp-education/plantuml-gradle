package plantuml.ci.shards

import io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite
import plantuml.ci.CucumberShardFeatures

/** CI BDD shard B — diagram types, performance, integration, API key, tiers. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/11_diagram_types.feature")
@SelectClasspathResource("features/12_performance.feature")
@SelectClasspathResource("features/13_integration_e2e.feature")
@SelectClasspathResource("features/14_api_key_pool.feature")
@SelectClasspathResource("features/19_tiered_rotation.feature")
@SelectClasspathResource("features/20_cross_provider_fallback.feature")
@SelectClasspathResource("features/1_minimal.feature")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = CucumberShardFeatures.SHARD_B_FEATURES)
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = CucumberShardFeatures.DEFAULT_TAGS)
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "plantuml.scenarios")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-shard-b.html, json:build/reports/cucumber-shard-b.json",
)
class CucumberShardBRunner
