package plantuml

import java.io.InputStreamReader
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.util.Locale
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

/**
 * Internationalization (i18n) message resolver for the PlantUML plugin.
 *
 * Loads messages from `i18n/Messages_{code}.properties` resource bundles
 * using UTF-8 encoding. Supports 10 languages: en, zh, hi, es, fr, ar, bn,
 * pt, ru, ur.
 *
 * Usage:
 * ```
 * PlantumlMessages.get("task.generate.group", "fr")
 * PlantumlMessages.format("generate.processing", "fr", 5)
 * ```
 */
object PlantumlMessages {

    private val baseName = "i18n/Messages"

    private val utf8Control = object : ResourceBundle.Control() {
        override fun newBundle(
            baseName: String,
            locale: Locale,
            format: String,
            loader: ClassLoader,
            reload: Boolean
        ): ResourceBundle? {
            if (format != "java.properties") return super.newBundle(baseName, locale, format, loader, reload)
            val bundleName = toBundleName(baseName, locale)
            val resourceName = toResourceName(bundleName, "properties")
            val url = loader.getResource(resourceName) ?: return null
            val connection = url.openConnection()
            if (reload) connection.useCaches = false
            connection.connect()
            return InputStreamReader(connection.inputStream, StandardCharsets.UTF_8).use { reader ->
                PropertyResourceBundle(reader)
            }
        }
    }

    /**
     * Loads the [ResourceBundle] for a given language code.
     *
     * @param code Language code (e.g., "en", "fr", "zh")
     * @return The resource bundle for the requested language
     */
    fun forLanguage(code: String): ResourceBundle {
        val locale = localeFor(code)
        return ResourceBundle.getBundle(baseName, locale, utf8Control)
    }

    /**
     * Retrieves a simple message by key.
     *
     * @param key Message key in the properties file
     * @param language Language code (default: "en")
     * @return The resolved message string
     */
    fun get(key: String, language: String = "en"): String {
        val bundle = forLanguage(language)
        return bundle.getString(key)
    }

    /**
     * Retrieves and formats a parameterized message.
     *
     * Uses [MessageFormat] for placeholder substitution (e.g., `{0}`, `{1}`).
     *
     * @param key Message key in the properties file
     * @param language Language code (default: "en")
     * @param args Positional arguments for [MessageFormat]
     * @return The formatted message string
     */
    fun format(key: String, language: String = "en", vararg args: Any): String {
        val pattern = get(key, language)
        return MessageFormat.format(pattern, *args)
    }

    private fun localeFor(code: String): Locale = when (code) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "hi" -> Locale.forLanguageTag("hi")
        "es" -> Locale.forLanguageTag("es")
        "fr" -> Locale.FRENCH
        "ar" -> Locale.forLanguageTag("ar")
        "bn" -> Locale.forLanguageTag("bn")
        "pt" -> Locale.forLanguageTag("pt")
        "ru" -> Locale.forLanguageTag("ru")
        "ur" -> Locale.forLanguageTag("ur")
        else -> Locale.ENGLISH
    }
}
