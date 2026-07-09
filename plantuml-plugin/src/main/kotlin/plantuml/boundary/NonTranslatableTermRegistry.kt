package plantuml.boundary

class NonTranslatableTermRegistry {

    private val terms: MutableSet<String> = mutableSetOf()

    fun register(term: String) {
        terms.add(term)
    }

    fun registerAll(terms: List<String>) {
        this.terms.addAll(terms)
    }

    fun contains(term: String): Boolean = terms.contains(term)
}