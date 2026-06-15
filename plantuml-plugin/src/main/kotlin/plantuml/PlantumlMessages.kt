package plantuml

import java.io.InputStreamReader
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.util.Locale
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

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

    fun forLanguage(code: String): ResourceBundle {
        val locale = localeFor(code)
        return ResourceBundle.getBundle(baseName, locale, utf8Control)
    }

    fun get(key: String, language: String = "en"): String {
        val bundle = forLanguage(language)
        return bundle.getString(key)
    }

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
