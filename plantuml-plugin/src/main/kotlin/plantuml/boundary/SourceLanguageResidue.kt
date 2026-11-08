package plantuml.boundary

/**
 * Verdict of the source-language residue detector (EPIC PLT-I18N-OUTPUT, PLT-OUT-3).
 *
 * @property detected true when the text still carries source-language markers
 * @property evidence stable, human-readable markers (`word:…`, `diacritic:…`,
 *   `ratio:n/m`) used by reports and tests
 */
data class LanguageResidue(
    val detected: Boolean,
    val evidence: List<String> = emptyList(),
) {
    companion object {
        val NONE = LanguageResidue(false)
    }
}

/**
 * Deterministic **source-language residue** detector (PLT-OUT-3).
 *
 * A translation provider sometimes leaves a short label in the source language
 * (a punctuation tweak, a capitalized noun, a frozen prefix). The port must never
 * publish such a text as `TRANSLATED` — a false green (measured on cheroliv.com:
 * French residue in `alt`/labels). This object answers one question without any
 * LLM: *does [text] still look like [sourceLanguage]?*
 *
 * The check only runs when the source has a marker profile and the target differs
 * (a French target is legitimate French prose). The bias is to **prefer
 * preserving the source** over a false `TRANSLATED`: a false positive keeps the
 * source text, which is safe; a false negative would silently ship a French label
 * in an English diagram.
 *
 * Pure: no I/O, no provider call, no mutable state.
 */
object SourceLanguageResidue {
    /**
     * A language marker profile.
     *
     * @property diacritics characters that are a strong signal of the language
     * @property strongWords words whose form is not an English word
     * @property stopWords high-frequency function words (ratio gate)
     * @property ratioThreshold share of function words that flags a sentence
     */
    private data class LanguageProfile(
        val diacritics: Set<Char>,
        val strongWords: Set<String>,
        val stopWords: Set<String>,
        val ratioThreshold: Double,
    )

    private val FRENCH =
        LanguageProfile(
            diacritics = setOf('à', 'â', 'ä', 'ç', 'é', 'è', 'ê', 'ë', 'î', 'ï', 'ô', 'ö', 'ù', 'û', 'ü', 'ÿ', 'œ', 'æ'),
            strongWords =
                setOf(
                    "utilisateur",
                    "utilisateurs",
                    "utilisatrice",
                    "utiliser",
                    "utilise",
                    "fichier",
                    "fichiers",
                    "donnee",
                    "donnees",
                    "parametre",
                    "parametres",
                    "gestion",
                    "connexion",
                    "recherche",
                    "requete",
                    "requetes",
                    "reponse",
                    "reponses",
                    "erreur",
                    "erreurs",
                    "systeme",
                    "systemes",
                    "sauvegarde",
                    "chargement",
                    "enregistrement",
                    "traitement",
                    "ajout",
                    "affichage",
                    "afficher",
                    "supprimer",
                    "valider",
                    "envoyer",
                    "recevoir",
                ),
            stopWords =
                setOf(
                    "le",
                    "la",
                    "les",
                    "un",
                    "une",
                    "des",
                    "du",
                    "de",
                    "et",
                    "ou",
                    "où",
                    "avec",
                    "dans",
                    "sur",
                    "sous",
                    "aux",
                    "ce",
                    "cet",
                    "cette",
                    "ces",
                    "qui",
                    "que",
                    "quoi",
                    "dont",
                    "est",
                    "sont",
                    "être",
                    "fait",
                    "faire",
                    "moins",
                    "très",
                    "votre",
                    "notre",
                    "vous",
                    "nous",
                    "il",
                    "elle",
                    "ils",
                    "elles",
                    "se",
                    "ses",
                    "leur",
                    "leurs",
                    "mais",
                    "donc",
                    "ni",
                    "comme",
                    "tout",
                    "tous",
                    "toute",
                    "toutes",
                    "chaque",
                    "sans",
                    "entre",
                    "vers",
                    "chez",
                    "avant",
                    "après",
                    "pendant",
                    "depuis",
                    "lors",
                    "alors",
                    "ainsi",
                    "puis",
                    "quand",
                ),
            // Deliberately absent (ambiguous with English): service, document,
            // configuration, validation, comment, pour, plus, on, or, car, si,
            // son, sa, a, but, pas.
            ratioThreshold = 0.30,
        )

    /** Marker profiles indexed by a normalized language code. */
    private val PROFILES: Map<String, LanguageProfile> = mapOf("fr" to FRENCH)

    private val PLACEHOLDER = Regex("""__PLT\d+__""")
    private val TOKEN = Regex("""[\p{L}\p{M}]+""")

    /**
     * Detects whether [text] still looks like [sourceLanguage].
     *
     * @param text the translated text to inspect (placeholders are ignored)
     * @param sourceLanguage the language the label was written in (`fr`, `fr-FR`…)
     * @param targetLanguage the language the provider was asked to produce
     */
    fun detect(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): LanguageResidue {
        val source = normalize(sourceLanguage)
        val target = normalize(targetLanguage)
        if (source == target) return LanguageResidue.NONE
        val profile = PROFILES[source] ?: return LanguageResidue.NONE

        val clean = PLACEHOLDER.replace(text, " ")
        if (clean.isBlank()) return LanguageResidue.NONE
        val tokens = TOKEN.findAll(clean).map { it.value.lowercase() }.toList()
        if (tokens.isEmpty()) {
            val diacritics = diacriticEvidence(clean, profile)
            return LanguageResidue(diacritics.isNotEmpty(), diacritics)
        }

        val evidence = mutableListOf<String>()
        tokens
            .filter { it in profile.strongWords }
            .map { "word:$it" }
            .distinct()
            .forEach { evidence += it }
        val matches = tokens.count { it in profile.stopWords }
        if (matches.toDouble() / tokens.size >= profile.ratioThreshold) {
            evidence += "ratio:$matches/${tokens.size}"
        }
        evidence += diacriticEvidence(clean, profile)
        val distinct = evidence.distinct()
        return LanguageResidue(distinct.isNotEmpty(), distinct)
    }

    private fun diacriticEvidence(
        text: String,
        profile: LanguageProfile,
    ): List<String> =
        text
            .filter { it.lowercaseChar() in profile.diacritics }
            .map { "diacritic:${it.lowercaseChar()}" }
            .distinct()

    private fun normalize(language: String): String {
        val trimmed = language.trim().lowercase()
        return trimmed.substringBefore('-').substringBefore('_')
    }
}
