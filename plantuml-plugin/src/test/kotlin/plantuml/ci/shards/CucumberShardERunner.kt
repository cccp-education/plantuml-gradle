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

/** CI BDD shard E — block status/correction, notes, term protection, corpus, syntax. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/15_i18n_language_switch.feature")
@SelectClasspathResource("features/28_agent_loop.feature")
@SelectClasspathResource("features/32_plantuml_block_status.feature")
@SelectClasspathResource("features/33_plantuml_block_correction.feature")
@SelectClasspathResource("features/34_plantuml_notes.feature")
@SelectClasspathResource("features/35_plantuml_term_protection.feature")
@SelectClasspathResource("features/36_plantuml_corpus.feature")
@SelectClasspathResource("features/37_plantuml_language_guard.feature")
@SelectClasspathResource("features/3_syntax_validation.feature")
@SelectClasspathResource("features/4_attempt_history.feature")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = CucumberShardFeatures.SHARD_E_FEATURES)
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = CucumberShardFeatures.DEFAULT_TAGS)
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "plantuml.scenarios")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-shard-e.html, json:build/reports/cucumber-shard-e.json",
)
class CucumberShardERunner
