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

/** CI BDD shard F — RAG, LLM providers, errors, configuration, incremental, preserve. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/5_rag_pipeline.feature")
@SelectClasspathResource("features/6_llm_providers.feature")
@SelectClasspathResource("features/7_error_handling.feature")
@SelectClasspathResource("features/8_configuration.feature")
@SelectClasspathResource("features/9_incremental_processing.feature")
@SelectClasspathResource("features/preserve_n0.feature")
@SelectClasspathResource("features/22_real_llm.feature")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = CucumberShardFeatures.SHARD_F_FEATURES)
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = CucumberShardFeatures.DEFAULT_TAGS)
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "plantuml.scenarios")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-shard-f.html, json:build/reports/cucumber-shard-f.json",
)
class CucumberShardFRunner
