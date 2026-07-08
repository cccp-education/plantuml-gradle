package plantuml.boundary

class IdiomaticGlossary {

    private val entries: MutableMap<String, MutableMap<String, GlossaryEntry>> = mutableMapOf()

    fun register(term: String, language: String, entry: GlossaryEntry) {
        entries.getOrPut(term) { mutableMapOf() }[language] = entry
    }

    fun registerAll(entries: Map<String, Map<String, GlossaryEntry>>) {
        entries.forEach { (term, byLanguage) ->
            byLanguage.forEach { (language, entry) ->
                register(term, language, entry)
            }
        }
    }

    fun lookup(term: String, language: String): GlossaryEntry? =
        entries[term]?.get(language)

    fun contains(term: String, language: String): Boolean =
        entries[term]?.containsKey(language) == true
}