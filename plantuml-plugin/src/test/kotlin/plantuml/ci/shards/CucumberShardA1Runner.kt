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

/**
 * CI BDD shard A1 — File Edge Cases (content scenarios: UTF-8, large, special
 * chars). This feature is the slowest one (TestKit builds), so it is split by
 * tag into two parallel shards (A1/A2).
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/10_file_edge_cases.feature")
@ConfigurationParameter(
    key = FEATURES_PROPERTY_NAME,
    value = "src/test/features/10_file_edge_cases.feature",
)
@ConfigurationParameter(
    key = FILTER_TAGS_PROPERTY_NAME,
    value = "${CucumberShardFeatures.DEFAULT_TAGS} and ${CucumberShardFeatures.SHARD_A1_TAG}",
)
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "plantuml.scenarios")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-shard-a1.html, json:build/reports/cucumber-shard-a1.json",
)
class CucumberShardA1Runner
