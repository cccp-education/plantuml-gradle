package plantuml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.util.regex.Pattern

/**
 * Loads and processes PlantUML plugin configuration from YAML files.
 *
 * Supports environment variable substitution using ${VAR_NAME} syntax.
 */
object ConfigLoader {
    private val ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}")
    private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    /**
     * Loads configuration from a YAML file with environment variable resolution.
     *
     * @param configFile The YAML configuration file
     * @return The loaded PlantumlConfig with resolved environment variables
     */
    fun load(configFile: File): PlantumlConfig {
        val yamlContent = configFile.readText()
        val resolvedYaml = resolveEnvironmentVariables(yamlContent)
        return MAPPER.readValue(resolvedYaml, PlantumlConfig::class.java)
    }

    /**
     * Resolves environment variables in YAML content.
     *
     * Replaces all occurrences of ${VAR_NAME} with the corresponding
     * environment variable value. If the variable is not found, the
     * original ${VAR_NAME} syntax is preserved.
     *
     * @param yamlContent The raw YAML content
     * @return The YAML content with resolved environment variables
     */
    fun resolveEnvironmentVariables(yamlContent: String): String {
        val matcher = ENV_VAR_PATTERN.matcher(yamlContent)
        val result = StringBuffer()

        while (matcher.find()) {
            val varName = matcher.group(1)
            val envValue = System.getenv(varName)
            val replacement = envValue ?: "\${$varName}"
            matcher.appendReplacement(result, Regex.escapeReplacement(replacement))
        }
        matcher.appendTail(result)

        return result.toString()
    }
}
