package plantuml

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LifecycleMessagesI18nTest {

    companion object {
        val SUPPORTED_LANGS = listOf("en", "fr", "zh", "hi", "es", "ar", "bn", "pt", "ru", "ur")

        val LIFECYCLE_KEYS = listOf(
            "validate.no_file",
            "validate.file_not_found",
            "validate.validating",
            "validate.valid",
            "validate.invalid",
            "validate.error_line",
            "validate.stack_trace",
            "diagramdocs.graph_not_found",
            "diagramdocs.reading",
            "diagramdocs.all_communities",
            "diagramdocs.subgraph",
            "diagramdocs.generated_prompt",
            "diagramdocs.summary",
            "kgparser.file_not_found",
            "kg.reading",
            "kg.stats",
            "kg.generated_puml",
            "kg.saved_despite_issues",
            "kg.generated_png",
            "kg.success",
            "generate.prompts_dir_missing",
            "generate.no_prompt_files",
            "generate.processing",
            "generate.debug",
            "generate.processing_prompt",
            "generate.error",
            "generate.validating",
            "generate.validation_errors",
            "generate.validation_error",
            "generate.validation_stack",
            "generate.generating_image",
            "generate.image_warning",
            "generate.llm_validation",
            "generate.collecting_rag",
            "generate.rag_warning",
            "generate.max_iterations",
            "generate.completed",
            "generate.cleaning",
            "generate.cleanup_complete",
            "generate.cleanup_failed",
            "generate.failed_max_attempts",
            "collect.rebuilding",
            "collect.no_rag_dir",
            "collect.created_rag_dir",
            "collect.no_data",
            "collect.found_data",
            "collect.rag_mode_cli",
            "collect.rag_mode_env",
            "collect.rag_mode_prop",
            "collect.rag_mode_test",
            "collect.rag_mode_db",
            "collect.rag_mode_sim",
            "collect.using_db",
            "collect.db_url",
            "collect.reindex_complete_db",
            "collect.embeddings_stored_db",
            "collect.using_testcontainers",
            "collect.container_started",
            "collect.jdbc_url",
            "collect.container_stopped",
            "collect.reindex_complete_tc",
            "collect.embeddings_stored_tc",
            "collect.indexing_diagram",
            "collect.split_segments",
            "collect.stored_embedding",
            "collect.indexing_history",
            "collect.generated_embedding",
            "collect.reindex_complete_sim",
            "collect.note_production",
            "collect.note_pgvector",
            "collect.note_config",
            "collect.cleaning",
            "collect.cleanup_complete",
            "collect.cleanup_failed"
        )

        @JvmStatic
        fun keyAndLangProvider(): Stream<Arguments> {
            return SUPPORTED_LANGS.flatMap { lang ->
                LIFECYCLE_KEYS.map { key -> Arguments.of(key, lang) }
            }.stream()
        }
    }

    @ParameterizedTest
    @MethodSource("keyAndLangProvider")
    fun `should resolve lifecycle message key in all supported languages`(key: String, lang: String) {
        val message = PlantumlMessages.get(key, lang)
        assertNotNull(message, "Key '$key' should exist for language '$lang'")
        assertTrue(message.isNotEmpty(), "Key '$key' should not be empty for language '$lang'")
    }

    @ParameterizedTest
    @ValueSource(strings = ["en", "fr", "zh", "hi", "es", "ar", "bn", "pt", "ru", "ur"])
    fun `should format lifecycle message with args in all languages`(lang: String) {
        val formatted = PlantumlMessages.format("generate.processing", lang, 5)
        assertTrue(formatted.isNotEmpty(), "Formatted message should not be empty for $lang")
        assertTrue(formatted.contains("5"), "Formatted message should contain the argument value for $lang")
    }

    @ParameterizedTest
    @ValueSource(strings = ["en", "fr", "zh", "hi", "es", "ar", "bn", "pt", "ru", "ur"])
    fun `should format multi-arg lifecycle message in all languages`(lang: String) {
        val formatted = PlantumlMessages.format("kg.stats", lang, 42, 17, 3)
        assertTrue(formatted.isNotEmpty(), "Multi-arg formatted message should not be empty for $lang")
        assertTrue(formatted.contains("42"), "Should contain first arg for $lang")
        assertTrue(formatted.contains("17"), "Should contain second arg for $lang")
        assertTrue(formatted.contains("3"), "Should contain third arg for $lang")
    }
}
