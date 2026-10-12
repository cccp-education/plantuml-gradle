package plantuml

import org.gradle.api.Project
import java.io.File

/**
 * Merges configuration from three sources with priority:
 * gradle.properties < YAML file < CLI parameters
 */
object ConfigMerger {

    /**
     * Source precedence for a scalar field: CLI > env > YAML > gradle.properties.
     * A source whose value equals [default] is treated as "unset" (this is the
     * historical encoding of the plugin, kept verbatim to preserve behaviour).
     */
    private fun <T> firstNonDefault(cli: T?, env: T, yaml: T, props: T, default: T): T =
        cli ?: when {
            env != default -> env
            yaml != default -> yaml
            else -> props
        }

    /** String variant where an empty value means "unset" (no default sentinel). */
    private fun firstNonEmpty(cli: String?, env: String, yaml: String, props: String): String =
        cli ?: env.ifEmpty { yaml.ifEmpty { props } }

    /** Boolean enabling flags: the first `true` wins, CLI taking precedence. */
    private fun firstEnabled(cli: Boolean?, env: Boolean, yaml: Boolean, props: Boolean): Boolean =
        cli ?: (env || yaml || props)

    /** Boolean where a `false` from env or YAML is sticky (opt-in switches). */
    private fun firstDisabledSticky(cli: Boolean?, env: Boolean, yaml: Boolean, props: Boolean): Boolean =
        cli ?: when {
            !env -> env
            !yaml -> yaml
            else -> props
        }

    /** Precedence for one provider's API key triple (CLI > env > YAML > props). */
    private fun apiKeyConfig(
        cli: Map<String, Any?>,
        provider: String,
        env: ApiKeyConfig,
        yaml: ApiKeyConfig,
        props: ApiKeyConfig,
    ): ApiKeyConfig = ApiKeyConfig(
        apiKey = firstNonEmpty(cli["langchain4j.$provider.apiKey"]?.toString(), env.apiKey, yaml.apiKey, props.apiKey),
        baseUrl = firstNonEmpty(cli["langchain4j.$provider.baseUrl"]?.toString(), env.baseUrl, yaml.baseUrl, props.baseUrl),
        modelName = firstNonEmpty(cli["langchain4j.$provider.modelName"]?.toString(), env.modelName, yaml.modelName, props.modelName),
    )

    fun merge(project: Project, yamlConfig: PlantumlConfig, cliParams: Map<String, Any?>): PlantumlConfig =
        merge(project.projectDir, yamlConfig, cliParams)

    fun merge(projectDir: File, yamlConfig: PlantumlConfig, cliParams: Map<String, Any?>): PlantumlConfig {
        val propertiesConfig = loadFromGradleProperties(projectDir)
        val envConfig = loadFromEnvironment()
        
        return PlantumlConfig(
            input = mergeInputConfig(envConfig.input, propertiesConfig.input, yamlConfig.input, cliParams),
            output = mergeOutputConfig(envConfig.output, propertiesConfig.output, yamlConfig.output, cliParams),
            langchain4j = mergeLangchain4jConfig(envConfig.langchain4j, propertiesConfig.langchain4j, yamlConfig.langchain4j, cliParams),
            git = mergeGitConfig(envConfig.git, propertiesConfig.git, yamlConfig.git, cliParams),
            rag = mergeRagConfig(envConfig.rag, propertiesConfig.rag, yamlConfig.rag, cliParams),
            diagramDocs = mergeDiagramDocsConfig(envConfig.diagramDocs, propertiesConfig.diagramDocs, yamlConfig.diagramDocs, cliParams),
            language = mergeLanguage(envConfig.language, propertiesConfig.language, yamlConfig.language, cliParams),
            supportedLanguages = mergeSupportedLanguages(envConfig.supportedLanguages, propertiesConfig.supportedLanguages, yamlConfig.supportedLanguages, cliParams)
        )
    }

    internal fun loadFromGradleProperties(projectDir: File): PlantumlConfig {
        val props = mutableMapOf<String, String>()
        
        val propertiesFile = File(projectDir, "gradle.properties")
        if (propertiesFile.exists()) {
            propertiesFile.reader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("plantuml.") && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("=", limit = 2)
                        if (parts.size == 2) {
                            props[parts[0].trim()] = parts[1].trim()
                        }
                    }
                }
            }
        }
        
        return buildConfigFromProperties(props)
    }

    internal fun loadFromEnvironment(): PlantumlConfig {
        val env = System.getenv()
        val sysProps = System.getProperties().orEmpty() as Map<String, String>
        
        return PlantumlConfig(
            input = InputConfig(
                prompts = env["PLANTUML_INPUT_PROMPTS"] ?: sysProps["PLANTUML_INPUT_PROMPTS"] ?: "prompts",
                defaultLang = env["PLANTUML_INPUT_DEFAULT_LANG"] ?: sysProps["PLANTUML_INPUT_DEFAULT_LANG"] ?: "en"
            ),
            output = OutputConfig(
                diagrams = env["PLANTUML_OUTPUT_DIAGRAMS"] ?: sysProps["PLANTUML_OUTPUT_DIAGRAMS"] ?: "generated/diagrams",
                images = env["PLANTUML_OUTPUT_IMAGES"] ?: sysProps["PLANTUML_OUTPUT_IMAGES"] ?: "generated/images",
                validations = env["PLANTUML_OUTPUT_VALIDATIONS"] ?: sysProps["PLANTUML_OUTPUT_VALIDATIONS"] ?: "generated/validations",
                rag = env["PLANTUML_OUTPUT_RAG"] ?: sysProps["PLANTUML_OUTPUT_RAG"] ?: "generated/rag",
                format = env["PLANTUML_OUTPUT_FORMAT"] ?: sysProps["PLANTUML_OUTPUT_FORMAT"] ?: "png",
                theme = env["PLANTUML_OUTPUT_THEME"] ?: sysProps["PLANTUML_OUTPUT_THEME"] ?: "default"
            ),
            langchain4j = LangchainConfig(
                maxIterations = env["PLANTUML_MAX_ITERATIONS"]?.toIntOrNull() ?: sysProps["PLANTUML_MAX_ITERATIONS"]?.toIntOrNull() ?: 5,
                maxSeconds = env["PLANTUML_MAX_SECONDS"]?.toLongOrNull() ?: sysProps["PLANTUML_MAX_SECONDS"]?.toLongOrNull() ?: 0,
                preflight = env["PLANTUML_PREFLIGHT"]?.toBoolean() ?: sysProps["PLANTUML_PREFLIGHT"]?.toBoolean() ?: false,
                examples = env["PLANTUML_EXAMPLES"]?.toBoolean() ?: sysProps["PLANTUML_EXAMPLES"]?.toBoolean() ?: false,
                designValidation = env["PLANTUML_DESIGN_VALIDATION"]?.toBoolean() ?: sysProps["PLANTUML_DESIGN_VALIDATION"]?.toBoolean() ?: false,
                model = env["PLANTUML_LLM_PROVIDER"] ?: sysProps["PLANTUML_LLM_PROVIDER"] ?: "ollama",
                validation = env["PLANTUML_VALIDATION"]?.toBoolean() ?: sysProps["PLANTUML_VALIDATION"]?.toBoolean() ?: true,
                validationPrompt = env["PLANTUML_VALIDATION_PROMPT"] ?: sysProps["PLANTUML_VALIDATION_PROMPT"] ?: "Rate this diagram on clarity, completeness, and best practices. Return a JSON with 'score' (1-10) and 'feedback' (string) and 'recommendations' (array).",
                ollama = OllamaConfig(
                    baseUrl = env["PLANTUML_OLLAMA_BASE_URL"] ?: sysProps["PLANTUML_OLLAMA_BASE_URL"] ?: "http://localhost:11438",
                    modelName = env["PLANTUML_OLLAMA_MODEL_NAME"] ?: sysProps["PLANTUML_OLLAMA_MODEL_NAME"] ?: "smollm:135m"
                ),
                gemini = ApiKeyConfig(
                    apiKey = env["PLANTUML_GEMINI_API_KEY"] ?: sysProps["PLANTUML_GEMINI_API_KEY"] ?: "",
                    baseUrl = env["PLANTUML_GEMINI_BASE_URL"] ?: sysProps["PLANTUML_GEMINI_BASE_URL"] ?: "",
                    modelName = env["PLANTUML_GEMINI_MODEL_NAME"] ?: sysProps["PLANTUML_GEMINI_MODEL_NAME"] ?: ""
                ),
                mistral = ApiKeyConfig(
                    apiKey = env["PLANTUML_MISTRAL_API_KEY"] ?: sysProps["PLANTUML_MISTRAL_API_KEY"] ?: "",
                    baseUrl = env["PLANTUML_MISTRAL_BASE_URL"] ?: sysProps["PLANTUML_MISTRAL_BASE_URL"] ?: "",
                    modelName = env["PLANTUML_MISTRAL_MODEL_NAME"] ?: sysProps["PLANTUML_MISTRAL_MODEL_NAME"] ?: ""
                ),
                openai = ApiKeyConfig(
                    apiKey = env["PLANTUML_OPENAI_API_KEY"] ?: sysProps["PLANTUML_OPENAI_API_KEY"] ?: "",
                    baseUrl = env["PLANTUML_OPENAI_BASE_URL"] ?: sysProps["PLANTUML_OPENAI_BASE_URL"] ?: "",
                    modelName = env["PLANTUML_OPENAI_MODEL_NAME"] ?: sysProps["PLANTUML_OPENAI_MODEL_NAME"] ?: ""
                ),
                claude = ApiKeyConfig(
                    apiKey = env["PLANTUML_CLAUDE_API_KEY"] ?: sysProps["PLANTUML_CLAUDE_API_KEY"] ?: "",
                    baseUrl = env["PLANTUML_CLAUDE_BASE_URL"] ?: sysProps["PLANTUML_CLAUDE_BASE_URL"] ?: "",
                    modelName = env["PLANTUML_CLAUDE_MODEL_NAME"] ?: sysProps["PLANTUML_CLAUDE_MODEL_NAME"] ?: ""
                ),
                huggingface = ApiKeyConfig(
                    apiKey = env["PLANTUML_HUGGINGFACE_API_KEY"] ?: sysProps["PLANTUML_HUGGINGFACE_API_KEY"] ?: "",
                    baseUrl = env["PLANTUML_HUGGINGFACE_BASE_URL"] ?: sysProps["PLANTUML_HUGGINGFACE_BASE_URL"] ?: "",
                    modelName = env["PLANTUML_HUGGINGFACE_MODEL_NAME"] ?: sysProps["PLANTUML_HUGGINGFACE_MODEL_NAME"] ?: ""
                ),
                groq = ApiKeyConfig(
                    apiKey = env["PLANTUML_GROQ_API_KEY"] ?: sysProps["PLANTUML_GROQ_API_KEY"] ?: "",
                    baseUrl = env["PLANTUML_GROQ_BASE_URL"] ?: sysProps["PLANTUML_GROQ_BASE_URL"] ?: "",
                    modelName = env["PLANTUML_GROQ_MODEL_NAME"] ?: sysProps["PLANTUML_GROQ_MODEL_NAME"] ?: ""
                )
            ),
            git = GitConfig(
                userName = env["PLANTUML_GIT_USER_NAME"] ?: sysProps["PLANTUML_GIT_USER_NAME"] ?: "github-actions[bot]",
                userEmail = env["PLANTUML_GIT_USER_EMAIL"] ?: sysProps["PLANTUML_GIT_USER_EMAIL"] ?: "github-actions[bot]@users.noreply.github.com",
                commitMessage = env["PLANTUML_GIT_COMMIT_MESSAGE"] ?: sysProps["PLANTUML_GIT_COMMIT_MESSAGE"] ?: "chore: update PlantUML diagrams [skip ci]",
                watchedBranches = env["PLANTUML_GIT_WATCHED_BRANCHES"]?.split(",")?.map { it.trim() }
                    ?: sysProps["PLANTUML_GIT_WATCHED_BRANCHES"]?.split(",")?.map { it.trim() }
                    ?: listOf("main", "develop")
            ),
            rag = RagConfig(
                databaseUrl = env["PLANTUML_RAG_DATABASE_URL"] ?: sysProps["PLANTUML_RAG_DATABASE_URL"] ?: "",
                port = env["PLANTUML_RAG_PORT"]?.toIntOrNull() ?: sysProps["PLANTUML_RAG_PORT"]?.toIntOrNull() ?: 5432,
                username = env["PLANTUML_RAG_USERNAME"] ?: sysProps["PLANTUML_RAG_USERNAME"] ?: "",
                password = env["PLANTUML_RAG_PASSWORD"] ?: sysProps["PLANTUML_RAG_PASSWORD"] ?: "",
                tableName = env["PLANTUML_RAG_TABLE_NAME"] ?: sysProps["PLANTUML_RAG_TABLE_NAME"] ?: "embeddings"
            ),
            diagramDocs = DiagramDocsConfig(
                enabled = env["PLANTUML_DIAGRAM_DOCS_ENABLED"]?.toBoolean() ?: sysProps["PLANTUML_DIAGRAM_DOCS_ENABLED"]?.toBoolean() ?: true,
                outputDir = env["PLANTUML_DIAGRAM_DOCS_OUTPUT_DIR"] ?: sysProps["PLANTUML_DIAGRAM_DOCS_OUTPUT_DIR"] ?: "diagrams/auto",
                subgraphs = env["PLANTUML_DIAGRAM_DOCS_SUBGRAPHS"]?.split(",")?.map { it.trim() }
                    ?: sysProps["PLANTUML_DIAGRAM_DOCS_SUBGRAPHS"]?.split(",")?.map { it.trim() }
                    ?: listOf("service layer", "config layer", "task layer", "model layer", "RAG"),
                model = env["PLANTUML_DIAGRAM_DOCS_MODEL"] ?: sysProps["PLANTUML_DIAGRAM_DOCS_MODEL"] ?: "qwen3.5-cloud"
            ),
            language = env["PLANTUML_LANGUAGE"] ?: sysProps["PLANTUML_LANGUAGE"] ?: "en",
            supportedLanguages = env["PLANTUML_SUPPORTED_LANGUAGES"]?.split(",")?.map { it.trim() }
                ?: sysProps["PLANTUML_SUPPORTED_LANGUAGES"]?.split(",")?.map { it.trim() }
                ?: listOf("en")
        )
    }

    private fun buildConfigFromProperties(props: Map<String, String>): PlantumlConfig {
        return PlantumlConfig(
            input = InputConfig(
                prompts = props["plantuml.input.prompts"] ?: "prompts",
                defaultLang = props["plantuml.input.defaultLang"] ?: "en"
            ),
            output = OutputConfig(
                diagrams = props["plantuml.output.diagrams"] ?: "generated/diagrams",
                images = props["plantuml.output.images"] ?: "generated/images",
                validations = props["plantuml.output.validations"] ?: "generated/validations",
                rag = props["plantuml.output.rag"] ?: "generated/rag",
                format = props["plantuml.output.format"] ?: "png",
                theme = props["plantuml.output.theme"] ?: "default"
            ),
            langchain4j = LangchainConfig(
                maxIterations = props["plantuml.langchain4j.maxIterations"]?.toIntOrNull() ?: 5,
                maxSeconds = props["plantuml.langchain4j.maxSeconds"]?.toLongOrNull() ?: 0,
                preflight = props["plantuml.langchain4j.preflight"]?.toBoolean() ?: false,
                examples = props["plantuml.langchain4j.examples"]?.toBoolean() ?: false,
                designValidation = props["plantuml.langchain4j.designValidation"]?.toBoolean() ?: false,
                model = props["plantuml.langchain4j.model"] ?: "ollama",
                validation = props["plantuml.langchain4j.validation"]?.toBoolean() ?: true,
                validationPrompt = props["plantuml.langchain4j.validationPrompt"] ?: "Rate this diagram on clarity, completeness, and best practices. Return a JSON with 'score' (1-10) and 'feedback' (string) and 'recommendations' (array).",
                ollama = OllamaConfig(
                    baseUrl = props["plantuml.langchain4j.ollama.baseUrl"] ?: "http://localhost:11438",
                    modelName = props["plantuml.langchain4j.ollama.modelName"] ?: "smollm:135m"
                ),
                gemini = ApiKeyConfig(
                    apiKey = props["plantuml.langchain4j.gemini.apiKey"] ?: "",
                    baseUrl = props["plantuml.langchain4j.gemini.baseUrl"] ?: "",
                    modelName = props["plantuml.langchain4j.gemini.modelName"] ?: ""
                ),
                mistral = ApiKeyConfig(
                    apiKey = props["plantuml.langchain4j.mistral.apiKey"] ?: "",
                    baseUrl = props["plantuml.langchain4j.mistral.baseUrl"] ?: "",
                    modelName = props["plantuml.langchain4j.mistral.modelName"] ?: ""
                ),
                openai = ApiKeyConfig(
                    apiKey = props["plantuml.langchain4j.openai.apiKey"] ?: "",
                    baseUrl = props["plantuml.langchain4j.openai.baseUrl"] ?: "",
                    modelName = props["plantuml.langchain4j.openai.modelName"] ?: ""
                ),
                claude = ApiKeyConfig(
                    apiKey = props["plantuml.langchain4j.claude.apiKey"] ?: "",
                    baseUrl = props["plantuml.langchain4j.claude.baseUrl"] ?: "",
                    modelName = props["plantuml.langchain4j.claude.modelName"] ?: ""
                ),
                huggingface = ApiKeyConfig(
                    apiKey = props["plantuml.langchain4j.huggingface.apiKey"] ?: "",
                    baseUrl = props["plantuml.langchain4j.huggingface.baseUrl"] ?: "",
                    modelName = props["plantuml.langchain4j.huggingface.modelName"] ?: ""
                ),
                groq = ApiKeyConfig(
                    apiKey = props["plantuml.langchain4j.groq.apiKey"] ?: "",
                    baseUrl = props["plantuml.langchain4j.groq.baseUrl"] ?: "",
                    modelName = props["plantuml.langchain4j.groq.modelName"] ?: ""
                )
            ),
            git = GitConfig(
                userName = props["plantuml.git.userName"] ?: "github-actions[bot]",
                userEmail = props["plantuml.git.userEmail"] ?: "github-actions[bot]@users.noreply.github.com",
                commitMessage = props["plantuml.git.commitMessage"] ?: "chore: update PlantUML diagrams [skip ci]",
                watchedBranches = props["plantuml.git.watchedBranches"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: listOf("main", "develop")
            ),
            rag = RagConfig(
                databaseUrl = props["plantuml.rag.databaseUrl"] ?: "",
                port = props["plantuml.rag.port"]?.toIntOrNull() ?: 5432,
                username = props["plantuml.rag.username"] ?: "",
                password = props["plantuml.rag.password"] ?: "",
                tableName = props["plantuml.rag.tableName"] ?: "embeddings"
            ),
            diagramDocs = DiagramDocsConfig(
                enabled = props["plantuml.diagramDocs.enabled"]?.toBoolean() ?: true,
                outputDir = props["plantuml.diagramDocs.outputDir"] ?: "diagrams/auto",
                subgraphs = props["plantuml.diagramDocs.subgraphs"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: listOf("service layer", "config layer", "task layer", "model layer", "RAG"),
                model = props["plantuml.diagramDocs.model"] ?: "qwen3.5-cloud"
            ),
            language = props["plantuml.language"] ?: "en",
            supportedLanguages = props["plantuml.supportedLanguages"]
                ?.split(",")
                ?.map { it.trim() }
                ?: listOf("en")
        )
    }

    private fun mergeInputConfig(env: InputConfig, props: InputConfig, yaml: InputConfig, cli: Map<String, Any?>): InputConfig {
        return InputConfig(
            prompts = firstNonDefault(cli["input.prompts"]?.toString(), env.prompts, yaml.prompts, props.prompts, "prompts"),
            defaultLang = firstNonDefault(cli["input.defaultLang"]?.toString(), env.defaultLang, yaml.defaultLang, props.defaultLang, "en")
        )
    }

    private fun mergeOutputConfig(env: OutputConfig, props: OutputConfig, yaml: OutputConfig, cli: Map<String, Any?>): OutputConfig {
        return OutputConfig(
            diagrams = firstNonDefault(cli["output.diagrams"]?.toString(), env.diagrams, yaml.diagrams, props.diagrams, "generated/diagrams"),
            images = firstNonDefault(cli["output.images"]?.toString(), env.images, yaml.images, props.images, "generated/images"),
            validations = firstNonDefault(cli["output.validations"]?.toString(), env.validations, yaml.validations, props.validations, "generated/validations"),
            rag = firstNonDefault(cli["output.rag"]?.toString(), env.rag, yaml.rag, props.rag, "generated/rag"),
            format = firstNonDefault(cli["output.format"]?.toString(), env.format, yaml.format, props.format, "png"),
            theme = firstNonDefault(cli["output.theme"]?.toString(), env.theme, yaml.theme, props.theme, "default")
        )
    }

    private fun mergeLangchain4jConfig(env: LangchainConfig, props: LangchainConfig, yaml: LangchainConfig, cli: Map<String, Any?>): LangchainConfig {
        return LangchainConfig(
            maxIterations = firstNonDefault(cli["langchain4j.maxIterations"] as? Int, env.maxIterations, yaml.maxIterations, props.maxIterations, 5),
            maxSeconds = firstNonDefault(cli["langchain4j.maxSeconds"] as? Long, env.maxSeconds, yaml.maxSeconds, props.maxSeconds, 0L),
            preflight = firstEnabled(cli["langchain4j.preflight"] as? Boolean, env.preflight, yaml.preflight, props.preflight),
            examples = firstEnabled(cli["langchain4j.examples"] as? Boolean, env.examples, yaml.examples, props.examples),
            designValidation = firstEnabled(cli["langchain4j.designValidation"] as? Boolean, env.designValidation, yaml.designValidation, props.designValidation),
            model = firstNonDefault(cli["langchain4j.model"]?.toString(), env.model, yaml.model, props.model, "ollama"),
            validation = cli["langchain4j.validation"] as? Boolean ?: (if (!yaml.validation) yaml.validation else props.validation),
            validationPrompt = cli["langchain4j.validationPrompt"]?.toString() ?: yaml.validationPrompt,
            ollama = OllamaConfig(
                baseUrl = firstNonDefault(cli["langchain4j.ollama.baseUrl"]?.toString(), env.ollama.baseUrl, yaml.ollama.baseUrl, props.ollama.baseUrl, "http://localhost:11438"),
                modelName = firstNonDefault(cli["langchain4j.ollama.modelName"]?.toString(), env.ollama.modelName, yaml.ollama.modelName, props.ollama.modelName, "smollm:135m")
            ),
            gemini = apiKeyConfig(cli, "gemini", env.gemini, yaml.gemini, props.gemini),
            mistral = apiKeyConfig(cli, "mistral", env.mistral, yaml.mistral, props.mistral),
            openai = apiKeyConfig(cli, "openai", env.openai, yaml.openai, props.openai),
            claude = apiKeyConfig(cli, "claude", env.claude, yaml.claude, props.claude),
            huggingface = apiKeyConfig(cli, "huggingface", env.huggingface, yaml.huggingface, props.huggingface),
            groq = apiKeyConfig(cli, "groq", env.groq, yaml.groq, props.groq)
        )
    }

    private fun mergeGitConfig(env: GitConfig, props: GitConfig, yaml: GitConfig, cli: Map<String, Any?>): GitConfig {
        @Suppress("UNCHECKED_CAST")
        return GitConfig(
            userName = firstNonDefault(cli["git.userName"]?.toString(), env.userName, yaml.userName, props.userName, "github-actions[bot]"),
            userEmail = firstNonDefault(cli["git.userEmail"]?.toString(), env.userEmail, yaml.userEmail, props.userEmail, "github-actions[bot]@users.noreply.github.com"),
            commitMessage = firstNonDefault(cli["git.commitMessage"]?.toString(), env.commitMessage, yaml.commitMessage, props.commitMessage, "chore: update PlantUML diagrams [skip ci]"),
            watchedBranches = firstNonDefault(cli["git.watchedBranches"] as? List<String>, env.watchedBranches, yaml.watchedBranches, props.watchedBranches, listOf("main", "develop"))
        )
    }

    private fun mergeRagConfig(env: RagConfig, props: RagConfig, yaml: RagConfig, cli: Map<String, Any?>): RagConfig {
        return RagConfig(
            databaseUrl = firstNonEmpty(cli["rag.databaseUrl"]?.toString(), env.databaseUrl, yaml.databaseUrl, props.databaseUrl),
            port = firstNonDefault(cli["rag.port"] as? Int, env.port, yaml.port, props.port, 5432),
            username = firstNonEmpty(cli["rag.username"]?.toString(), env.username, yaml.username, props.username),
            password = firstNonEmpty(cli["rag.password"]?.toString(), env.password, yaml.password, props.password),
            tableName = firstNonDefault(cli["rag.tableName"]?.toString(), env.tableName, yaml.tableName, props.tableName, "embeddings")
        )
    }

    private val defaultSubgraphs = listOf("service layer", "config layer", "task layer", "model layer", "RAG")

    private fun mergeDiagramDocsConfig(env: DiagramDocsConfig, props: DiagramDocsConfig, yaml: DiagramDocsConfig, cli: Map<String, Any?>): DiagramDocsConfig {
        @Suppress("UNCHECKED_CAST")
        return DiagramDocsConfig(
            enabled = firstDisabledSticky(cli["diagramDocs.enabled"]?.toString()?.toBoolean(), env.enabled, yaml.enabled, props.enabled),
            outputDir = firstNonDefault(cli["diagramDocs.outputDir"]?.toString(), env.outputDir, yaml.outputDir, props.outputDir, "diagrams/auto"),
            subgraphs = firstNonDefault(cli["diagramDocs.subgraphs"] as? List<String>, env.subgraphs, yaml.subgraphs, props.subgraphs, defaultSubgraphs),
            model = firstNonDefault(cli["diagramDocs.model"]?.toString(), env.model, yaml.model, props.model, "qwen3.5-cloud")
        )
    }

    private fun mergeLanguage(envLang: String, propsLang: String, yamlLang: String, cli: Map<String, Any?>): String {
        val resolved = cli["language"]?.toString()
            ?: (if (envLang != "en") envLang else (if (yamlLang != "en") yamlLang else propsLang))
        return if (resolved in PlantumlConfig.SUPPORTED_LANGS) resolved else "en"
    }

    private fun mergeSupportedLanguages(envLangs: List<String>, propsLangs: List<String>, yamlLangs: List<String>, cli: Map<String, Any?>): List<String> {
        @Suppress("UNCHECKED_CAST")
        val cliValue = cli["supportedLanguages"] as? List<String>
        if (cliValue != null && cliValue.isNotEmpty()) return cliValue
        if (envLangs != listOf("en")) return envLangs
        if (yamlLangs != listOf("en")) return yamlLangs
        return propsLangs
    }
}
