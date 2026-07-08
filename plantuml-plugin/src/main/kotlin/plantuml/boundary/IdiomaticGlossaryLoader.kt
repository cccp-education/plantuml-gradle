package plantuml.boundary

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule

class IdiomaticGlossaryLoader {

    private val mapper = ObjectMapper(YAMLFactory()).apply {
        registerModule(kotlinModule())
    }

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