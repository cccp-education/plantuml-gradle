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

/** CI BDD shard D — round-trip, toolchain, provider, probe, repair, agent, context. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/23_plantuml_roundtrip.feature")
@SelectClasspathResource("features/24_toolchain_governance.feature")
@SelectClasspathResource("features/25_provider_model_config.feature")
@SelectClasspathResource("features/26_diagram_output_probe.feature")
@SelectClasspathResource("features/27_plantuml_repair.feature")
@SelectClasspathResource("features/28_agent_loop.feature")
@SelectClasspathResource("features/29_plantuml_context.feature")
@SelectClasspathResource("features/2_plantuml_processing.feature")
@SelectClasspathResource("features/30_plantuml_provenance.feature")
@SelectClasspathResource("features/31_plantuml_context_wiring.feature")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = CucumberShardFeatures.SHARD_D_FEATURES)
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = CucumberShardFeatures.DEFAULT_TAGS)
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "plantuml.scenarios")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-shard-d.html, json:build/reports/cucumber-shard-d.json",
)
class CucumberShardDRunner
