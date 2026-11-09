package plantuml.ci

/**
 * CI BDD shard partition (CNV-13.6 — keep the wall-clock under the 5 min budget).
 *
 * The feature files are split into exhaustive, disjoint shards; each shard runs
 * on its own runner machine. `10_file_edge_cases.feature` is the slowest feature
 * (six TestKit scenarios) and is split by tag into two shards. Guarded by
 * [PlantumlCucumberShardCoverageTest].
 */
object CucumberShardFeatures {
    const val DEFAULT_TAGS = "not @wip and not @integration and not @realllm and not @skip"

    const val SHARD_A1_FILE = "10_file_edge_cases.feature"
    const val SHARD_A1_TAG = "@bdd-content"
    const val SHARD_A2_TAG = "@bdd-empty"

    const val SHARD_B_FEATURES =
        "src/test/features/11_diagram_types.feature," +
            "src/test/features/12_performance.feature," +
            "src/test/features/13_integration_e2e.feature," +
            "src/test/features/14_api_key_pool.feature," +
            "src/test/features/19_tiered_rotation.feature," +
            "src/test/features/20_cross_provider_fallback.feature," +
            "src/test/features/1_minimal.feature"

    const val SHARD_C_FEATURES =
        "src/test/features/16_i18n_task_descriptions.feature," +
            "src/test/features/17_i18n_lifecycle_messages.feature," +
            "src/test/features/18_i18n_error_messages.feature," +
            "src/test/features/21_i18n_translation_boundary.feature"

    const val SHARD_D_FEATURES =
        "src/test/features/23_plantuml_roundtrip.feature," +
            "src/test/features/24_toolchain_governance.feature," +
            "src/test/features/25_provider_model_config.feature," +
            "src/test/features/26_diagram_output_probe.feature," +
            "src/test/features/27_plantuml_repair.feature," +
            "src/test/features/29_plantuml_context.feature," +
            "src/test/features/2_plantuml_processing.feature," +
            "src/test/features/30_plantuml_provenance.feature," +
            "src/test/features/31_plantuml_context_wiring.feature"

    const val SHARD_E_FEATURES =
        "src/test/features/15_i18n_language_switch.feature," +
            "src/test/features/28_agent_loop.feature," +
            "src/test/features/32_plantuml_block_status.feature," +
            "src/test/features/33_plantuml_block_correction.feature," +
            "src/test/features/34_plantuml_notes.feature," +
            "src/test/features/35_plantuml_term_protection.feature," +
            "src/test/features/36_plantuml_corpus.feature," +
            "src/test/features/37_plantuml_language_guard.feature," +
            "src/test/features/38_plantuml_corpus_language.feature," +
            "src/test/features/3_syntax_validation.feature," +
            "src/test/features/4_attempt_history.feature"

    const val SHARD_F_FEATURES =
        "src/test/features/5_rag_pipeline.feature," +
            "src/test/features/6_llm_providers.feature," +
            "src/test/features/7_error_handling.feature," +
            "src/test/features/8_configuration.feature," +
            "src/test/features/9_incremental_processing.feature," +
            "src/test/features/preserve_n0.feature," +
            "src/test/features/22_real_llm.feature"

    /** File-level shards (the tagged File Edge Cases split excluded). */
    val FILE_SHARDS: Map<String, String> =
        mapOf(
            "B" to SHARD_B_FEATURES,
            "C" to SHARD_C_FEATURES,
            "D" to SHARD_D_FEATURES,
            "E" to SHARD_E_FEATURES,
            "F" to SHARD_F_FEATURES,
        )
}
