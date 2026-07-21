package plantuml.boundary

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

/**
 * Loads a [NonTranslatableTermRegistry] from a YAML string.
 *
 * Expected YAML structure:
 * ```
 * terms:
 *   - REAC
 *   - AFNOR
 *   - FPA
 * ```
 */
class NonTranslatableTermRegistryLoader {

    private val mapper = ObjectMapper(YAMLFactory())

    /**
     * Parses a YAML string into a [NonTranslatableTermRegistry].
     *
     * @param yaml The YAML content; blank input returns an empty registry
     * @return The populated registry
     */
    fun load(yaml: String): NonTranslatableTermRegistry {
        val registry = NonTranslatableTermRegistry()
        if (yaml.isBlank()) return registry

        val parsed: Map<String, List<String>> =
            mapper.readValue(yaml, object : TypeReference<Map<String, List<String>>>() {})
        val terms = parsed["terms"] ?: emptyList()
        registry.registerAll(terms)
        return registry
    }
}