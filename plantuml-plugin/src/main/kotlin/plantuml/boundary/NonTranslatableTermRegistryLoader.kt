package plantuml.boundary

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

class NonTranslatableTermRegistryLoader {

    private val mapper = ObjectMapper(YAMLFactory())

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