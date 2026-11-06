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

/** CI BDD shard C — i18n configuration, task descriptions, lifecycle, errors, boundary. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/16_i18n_task_descriptions.feature")
@SelectClasspathResource("features/17_i18n_lifecycle_messages.feature")
@SelectClasspathResource("features/18_i18n_error_messages.feature")
@SelectClasspathResource("features/21_i18n_translation_boundary.feature")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = CucumberShardFeatures.SHARD_C_FEATURES)
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = CucumberShardFeatures.DEFAULT_TAGS)
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "plantuml.scenarios")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-shard-c.html, json:build/reports/cucumber-shard-c.json",
)
class CucumberShardCRunner
