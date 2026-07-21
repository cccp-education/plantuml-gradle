package plantuml.boundary

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule

/**
 * Loads an [IdiomaticGlossary] from a YAML string.
 *
 * Expected YAML structure:
 * ```
 * term:
 *   fr:
 *     translation: "pipeline"
 *     strategy: BORROW
 *   zh:
 *     translation: "管道"
 *     strategy: TRANSLATE
 * ```
 */
class IdiomaticGlossaryLoader {

    private val mapper = ObjectMapper(YAMLFactory()).apply {
        registerModule(kotlinModule())
    }

    /**
     * Parses a YAML string into an [IdiomaticGlossary].
     *
     * @param yaml The YAML content; blank input returns an empty glossary
     * @return The populated glossary
     */
    fun load(yaml: String): IdiomaticGlossary {
        val glossary = IdiomaticGlossary()
        if (yaml.isBlank()) return glossary

        val parsed: Map<String, Map<String, Map<String, String>>> =
            mapper.readValue(yaml, object : TypeReference<Map<String, Map<String, Map<String, String>>>>() {})

        parsed.forEach { (term, byLanguage) ->
            byLanguage.forEach { (language, fields) ->
                val translation = fields["translation"] ?: term
                val strategyName = fields["strategy"] ?: "PRESERVE"
                val strategy = runCatching { TranslationStrategy.valueOf(strategyName) }
                    .getOrDefault(TranslationStrategy.PRESERVE)
                glossary.register(term, language, GlossaryEntry(translation, strategy))
            }
        }
        return glossary
    }
}