package plantuml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class ConfigMergerTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `should read gradle properties file directly`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("""
            plantuml.input.prompts=custom-prompts
        """.trimIndent())

        val config = ConfigMerger.loadFromGradleProperties(testProjectDir)

        assertEquals("custom-prompts", config.input.prompts)
    }

    @Test
    fun `should use gradle properties as base configuration`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("""
            plantuml.input.prompts=custom-prompts
            plantuml.output.images=custom-images
            plantuml.langchain4j.maxIterations=3
        """.trimIndent())

        val yamlConfig = PlantumlConfig()
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("custom-prompts", result.input.prompts)
        assertEquals("custom-images", result.output.images)
        assertEquals(3, result.langchain4j.maxIterations)
    }

    @Test
    fun `should override gradle properties with YAML config`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("""
            plantuml.input.prompts=properties-prompts
            plantuml.output.images=properties-images
        """.trimIndent())

        val yamlConfig = PlantumlConfig(
            input = InputConfig(prompts = "yaml-prompts"),
            output = OutputConfig(images = "yaml-images")
        )
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("yaml-prompts", result.input.prompts)
        assertEquals("yaml-images", result.output.images)
    }

    @Test
    fun `should override YAML with CLI parameters`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("""
            plantuml.input.prompts=properties-prompts
        """.trimIndent())

        val yamlConfig = PlantumlConfig(
            input = InputConfig(prompts = "yaml-prompts")
        )
        val cliParams = mapOf("input.prompts" to "cli-prompts")

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("cli-prompts", result.input.prompts)
    }

    @Test
    fun `should use full priority chain properties less than yaml less than cli`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("""
            plantuml.input.prompts=properties-prompts
            plantuml.input.defaultLang=properties-lang
            plantuml.output.images=properties-images
            plantuml.output.format=properties-format
        """.trimIndent())

        val yamlConfig = PlantumlConfig(
            input = InputConfig(prompts = "yaml-prompts", defaultLang = "yaml-lang"),
            output = OutputConfig(images = "yaml-images")
        )
        val cliParams = mapOf("input.prompts" to "cli-prompts")

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("cli-prompts", result.input.prompts)
        assertEquals("yaml-lang", result.input.defaultLang)
        assertEquals("yaml-images", result.output.images)
        assertEquals("properties-format", result.output.format)
    }

    @Test
    fun `should use defaults when no configuration sources provided`() {
        val yamlConfig = PlantumlConfig()
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("prompts", result.input.prompts)
        assertEquals("generated/images", result.output.images)
        assertEquals(5, result.langchain4j.maxIterations)
        assertEquals("ollama", result.langchain4j.model)
    }

    @Test
    fun `should handle missing gradle properties file gracefully`() {
        val yamlConfig = PlantumlConfig(
            input = InputConfig(prompts = "yaml-prompts")
        )
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("yaml-prompts", result.input.prompts)
    }

    @Test
    fun `should load all configuration categories from gradle properties`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("""
            plantuml.input.prompts=my-prompts
            plantuml.output.diagrams=my-diagrams
            plantuml.output.images=my-images
            plantuml.output.rag=my-rag
            plantuml.langchain4j.model=gemini
            plantuml.langchain4j.maxIterations=10
            plantuml.git.userName=custom-user
            plantuml.rag.tableName=my_embeddings
        """.trimIndent())

        val yamlConfig = PlantumlConfig()
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("my-prompts", result.input.prompts)
        assertEquals("my-diagrams", result.output.diagrams)
        assertEquals("my-images", result.output.images)
        assertEquals("my-rag", result.output.rag)
        assertEquals("gemini", result.langchain4j.model)
        assertEquals(10, result.langchain4j.maxIterations)
        assertEquals("custom-user", result.git.userName)
        assertEquals("my_embeddings", result.rag.tableName)
    }

    @Test
    fun `should default language to en`() {
        val yamlConfig = PlantumlConfig()
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("en", result.language)
    }

    @Test
    fun `should default supportedLanguages to en only`() {
        val yamlConfig = PlantumlConfig()
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals(listOf("en"), result.supportedLanguages)
    }

    @Test
    fun `should resolve language from gradle properties`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("plantuml.language=fr")

        val yamlConfig = PlantumlConfig()
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("fr", result.language)
    }

    @Test
    fun `should resolve supportedLanguages from gradle properties`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("plantuml.supportedLanguages=fr,en,es")

        val yamlConfig = PlantumlConfig()
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals(listOf("fr", "en", "es"), result.supportedLanguages)
    }

    @Test
    fun `should override language with YAML config`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("plantuml.language=fr")

        val yamlConfig = PlantumlConfig(language = "zh")
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("zh", result.language)
    }

    @Test
    fun `should override language with CLI parameter`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("plantuml.language=fr")

        val yamlConfig = PlantumlConfig(language = "zh")
        val cliParams = mapOf("language" to "es")

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("es", result.language)
    }

    @Test
    fun `should fallback to en when language not in SUPPORTED_LANGS`() {
        val yamlConfig = PlantumlConfig(language = "de")
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("en", result.language)
    }

    @Test
    fun `should fallback to en when CLI language not in SUPPORTED_LANGS`() {
        val yamlConfig = PlantumlConfig()
        val cliParams = mapOf("language" to "it")

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("en", result.language)
    }

    @Test
    fun `should override supportedLanguages with YAML config`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("plantuml.supportedLanguages=fr,en")

        val yamlConfig = PlantumlConfig(supportedLanguages = listOf("zh", "hi"))
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals(listOf("zh", "hi"), result.supportedLanguages)
    }

    @Test
    fun `should override supportedLanguages with CLI parameter`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("plantuml.supportedLanguages=fr,en")

        val yamlConfig = PlantumlConfig(supportedLanguages = listOf("zh", "hi"))
        val cliParams = mapOf("supportedLanguages" to listOf("ar", "ur"))

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals(listOf("ar", "ur"), result.supportedLanguages)
    }

    @Test
    fun `should resolve language from gradle properties with full merge`() {
        val gradleProperties = File(testProjectDir, "gradle.properties")
        gradleProperties.writeText("""
            plantuml.language=fr
            plantuml.supportedLanguages=fr,en,es
        """.trimIndent())

        val yamlConfig = PlantumlConfig()
        val cliParams = emptyMap<String, Any?>()

        val result = ConfigMerger.merge(testProjectDir, yamlConfig, cliParams)

        assertEquals("fr", result.language)
        assertEquals(listOf("fr", "en", "es"), result.supportedLanguages)
    }
}
