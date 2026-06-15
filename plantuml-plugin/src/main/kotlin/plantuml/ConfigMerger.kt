package plantuml

import org.gradle.api.Project
import java.io.File

/**
 * Merges configuration from three sources with priority:
 * gradle.properties < YAML file < CLI parameters
 */
object ConfigMerger {

    fun merge(project: Project, yamlConfig: PlantumlConfig, cliParams: Map<String, Any?>): PlantumlConfig {
        val propertiesConfig = loadFromGradleProperties(project.projectDir)
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
                model = env["PLANTUML_LLM_PROVIDER"] ?: sysProps["PLANTUML_LLM_PROVIDER"] ?: "ollama",
                validation = env["PLANTUML_VALIDATION"]?.toBoolean() ?: sysProps["PLANTUML_VALIDATION"]?.toBoolean() ?: true,
                validationPrompt = env["PLANTUML_VALIDATION_PROMPT"] ?: sysProps["PLANTUML_VALIDATION_PROMPT"] ?: "Rate this diagram on clarity, completeness, and best practices. Return a JSON with 'score' (1-10) and 'feedback' (string) and 'recommendations' (array).",
                ollama = OllamaConfig(
                    baseUrl = env["PLANTUML_OLLAMA_BASE_URL"] ?: sysProps["PLANTUML_OLLAMA_BASE_URL"] ?: "http://localhost:11434",
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
                model = props["plantuml.langchain4j.model"] ?: "ollama",
                validation = props["plantuml.langchain4j.validation"]?.toBoolean() ?: true,
                validationPrompt = props["plantuml.langchain4j.validationPrompt"] ?: "Rate this diagram on clarity, completeness, and best practices. Return a JSON with 'score' (1-10) and 'feedback' (string) and 'recommendations' (array).",
                ollama = OllamaConfig(
                    baseUrl = props["plantuml.langchain4j.ollama.baseUrl"] ?: "http://localhost:11434",
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
            prompts = cli["input.prompts"]?.toString() ?: (if (env.prompts != "prompts") env.prompts else (if (yaml.prompts != "prompts") yaml.prompts else props.prompts)),
            defaultLang = cli["input.defaultLang"]?.toString() ?: (if (env.defaultLang != "en") env.defaultLang else (if (yaml.defaultLang != "en") yaml.defaultLang else props.defaultLang))
        )
    }

    private fun mergeOutputConfig(env: OutputConfig, props: OutputConfig, yaml: OutputConfig, cli: Map<String, Any?>): OutputConfig {
        return OutputConfig(
            diagrams = cli["output.diagrams"]?.toString() ?: (if (env.diagrams != "generated/diagrams") env.diagrams else (if (yaml.diagrams != "generated/diagrams") yaml.diagrams else props.diagrams)),
            images = cli["output.images"]?.toString() ?: (if (env.images != "generated/images") env.images else (if (yaml.images != "generated/images") yaml.images else props.images)),
            validations = cli["output.validations"]?.toString() ?: (if (env.validations != "generated/validations") env.validations else (if (yaml.validations != "generated/validations") yaml.validations else props.validations)),
            rag = cli["output.rag"]?.toString() ?: (if (env.rag != "generated/rag") env.rag else (if (yaml.rag != "generated/rag") yaml.rag else props.rag)),
            format = cli["output.format"]?.toString() ?: (if (env.format != "png") env.format else (if (yaml.format != "png") yaml.format else props.format)),
            theme = cli["output.theme"]?.toString() ?: (if (env.theme != "default") env.theme else (if (yaml.theme != "default") yaml.theme else props.theme))
        )
    }

    private fun mergeLangchain4jConfig(env: LangchainConfig, props: LangchainConfig, yaml: LangchainConfig, cli: Map<String, Any?>): LangchainConfig {
        @Suppress("KotlinConstantConditions")
        return LangchainConfig(
            maxIterations = cli["langchain4j.maxIterations"] as? Int ?: (if (env.maxIterations != 5) env.maxIterations else (if (yaml.maxIterations != 5) yaml.maxIterations else props.maxIterations)),
            model = cli["langchain4j.model"]?.toString() ?: (if (env.model != "ollama") env.model else (if (yaml.model != "ollama") yaml.model else props.model)),
            validation = cli["langchain4j.validation"] as? Boolean ?: (if (!yaml.validation) yaml.validation else props.validation),
            validationPrompt = cli["langchain4j.validationPrompt"]?.toString() ?: yaml.validationPrompt,
            ollama = OllamaConfig(
                baseUrl = cli["langchain4j.ollama.baseUrl"]?.toString() ?: (if (env.ollama.baseUrl != "http://localhost:11434") env.ollama.baseUrl else (if (yaml.ollama.baseUrl != "http://localhost:11434") yaml.ollama.baseUrl else props.ollama.baseUrl)),
                modelName = cli["langchain4j.ollama.modelName"]?.toString() ?: (if (env.ollama.modelName != "smollm:135m") env.ollama.modelName else (if (yaml.ollama.modelName != "smollm:135m") yaml.ollama.modelName else props.ollama.modelName))
            ),
            gemini = ApiKeyConfig(
                apiKey = cli["langchain4j.gemini.apiKey"]?.toString() ?: (if (env.gemini.apiKey.isNotEmpty()) env.gemini.apiKey else (if (yaml.gemini.apiKey.isNotEmpty()) yaml.gemini.apiKey else props.gemini.apiKey)),
                baseUrl = cli["langchain4j.gemini.baseUrl"]?.toString() ?: (if (env.gemini.baseUrl.isNotEmpty()) env.gemini.baseUrl else (if (yaml.gemini.baseUrl.isNotEmpty()) yaml.gemini.baseUrl else props.gemini.baseUrl)),
                modelName = cli["langchain4j.gemini.modelName"]?.toString() ?: (if (env.gemini.modelName.isNotEmpty()) env.gemini.modelName else (if (yaml.gemini.modelName.isNotEmpty()) yaml.gemini.modelName else props.gemini.modelName))
            ),
            mistral = ApiKeyConfig(
                apiKey = cli["langchain4j.mistral.apiKey"]?.toString() ?: (if (env.mistral.apiKey.isNotEmpty()) env.mistral.apiKey else (if (yaml.mistral.apiKey.isNotEmpty()) yaml.mistral.apiKey else props.mistral.apiKey)),
                baseUrl = cli["langchain4j.mistral.baseUrl"]?.toString() ?: (if (env.mistral.baseUrl.isNotEmpty()) env.mistral.baseUrl else (if (yaml.mistral.baseUrl.isNotEmpty()) yaml.mistral.baseUrl else props.mistral.baseUrl)),
                modelName = cli["langchain4j.mistral.modelName"]?.toString() ?: (if (env.mistral.modelName.isNotEmpty()) env.mistral.modelName else (if (yaml.mistral.modelName.isNotEmpty()) yaml.mistral.modelName else props.mistral.modelName))
            ),
            openai = ApiKeyConfig(
                apiKey = cli["langchain4j.openai.apiKey"]?.toString() ?: (if (env.openai.apiKey.isNotEmpty()) env.openai.apiKey else (if (yaml.openai.apiKey.isNotEmpty()) yaml.openai.apiKey else props.openai.apiKey)),
                baseUrl = cli["langchain4j.openai.baseUrl"]?.toString() ?: (if (env.openai.baseUrl.isNotEmpty()) env.openai.baseUrl else (if (yaml.openai.baseUrl.isNotEmpty()) yaml.openai.baseUrl else props.openai.baseUrl)),
                modelName = cli["langchain4j.openai.modelName"]?.toString() ?: (if (env.openai.modelName.isNotEmpty()) env.openai.modelName else (if (yaml.openai.modelName.isNotEmpty()) yaml.openai.modelName else props.openai.modelName))
            ),
            claude = ApiKeyConfig(
                apiKey = cli["langchain4j.claude.apiKey"]?.toString() ?: (if (env.claude.apiKey.isNotEmpty()) env.claude.apiKey else (if (yaml.claude.apiKey.isNotEmpty()) yaml.claude.apiKey else props.claude.apiKey)),
                baseUrl = cli["langchain4j.claude.baseUrl"]?.toString() ?: (if (env.claude.baseUrl.isNotEmpty()) env.claude.baseUrl else (if (yaml.claude.baseUrl.isNotEmpty()) yaml.claude.baseUrl else props.claude.baseUrl)),
                modelName = cli["langchain4j.claude.modelName"]?.toString() ?: (if (env.claude.modelName.isNotEmpty()) env.claude.modelName else (if (yaml.claude.modelName.isNotEmpty()) yaml.claude.modelName else props.claude.modelName))
            ),
            huggingface = ApiKeyConfig(
                apiKey = cli["langchain4j.huggingface.apiKey"]?.toString() ?: (if (env.huggingface.apiKey.isNotEmpty()) env.huggingface.apiKey else (if (yaml.huggingface.apiKey.isNotEmpty()) yaml.huggingface.apiKey else props.huggingface.apiKey)),
                baseUrl = cli["langchain4j.huggingface.baseUrl"]?.toString() ?: (if (env.huggingface.baseUrl.isNotEmpty()) env.huggingface.baseUrl else (if (yaml.huggingface.baseUrl.isNotEmpty()) yaml.huggingface.baseUrl else props.huggingface.baseUrl)),
                modelName = cli["langchain4j.huggingface.modelName"]?.toString() ?: (if (env.huggingface.modelName.isNotEmpty()) env.huggingface.modelName else (if (yaml.huggingface.modelName.isNotEmpty()) yaml.huggingface.modelName else props.huggingface.modelName))
            ),
            groq = ApiKeyConfig(
                apiKey = cli["langchain4j.groq.apiKey"]?.toString() ?: (if (env.groq.apiKey.isNotEmpty()) env.groq.apiKey else (if (yaml.groq.apiKey.isNotEmpty()) yaml.groq.apiKey else props.groq.apiKey)),
                baseUrl = cli["langchain4j.groq.baseUrl"]?.toString() ?: (if (env.groq.baseUrl.isNotEmpty()) env.groq.baseUrl else (if (yaml.groq.baseUrl.isNotEmpty()) yaml.groq.baseUrl else props.groq.baseUrl)),
                modelName = cli["langchain4j.groq.modelName"]?.toString() ?: (if (env.groq.modelName.isNotEmpty()) env.groq.modelName else (if (yaml.groq.modelName.isNotEmpty()) yaml.groq.modelName else props.groq.modelName))
            )
        )
    }

    private fun mergeGitConfig(env: GitConfig, props: GitConfig, yaml: GitConfig, cli: Map<String, Any?>): GitConfig {
        @Suppress("UNCHECKED_CAST")
        return GitConfig(
            userName = cli["git.userName"]?.toString() ?: (if (env.userName != "github-actions[bot]") env.userName else (if (yaml.userName != "github-actions[bot]") yaml.userName else props.userName)),
            userEmail = cli["git.userEmail"]?.toString() ?: (if (env.userEmail != "github-actions[bot]@users.noreply.github.com") env.userEmail else (if (yaml.userEmail != "github-actions[bot]@users.noreply.github.com") yaml.userEmail else props.userEmail)),
            commitMessage = cli["git.commitMessage"]?.toString() ?: (if (env.commitMessage != "chore: update PlantUML diagrams [skip ci]") env.commitMessage else (if (yaml.commitMessage != "chore: update PlantUML diagrams [skip ci]") yaml.commitMessage else props.commitMessage)),
            watchedBranches = cli["git.watchedBranches"] as? List<String> ?: (if (env.watchedBranches != listOf("main", "develop")) env.watchedBranches else (if (yaml.watchedBranches != listOf("main", "develop")) yaml.watchedBranches else props.watchedBranches))
        )
    }

    private fun mergeRagConfig(env: RagConfig, props: RagConfig, yaml: RagConfig, cli: Map<String, Any?>): RagConfig {
        return RagConfig(
            databaseUrl = cli["rag.databaseUrl"]?.toString() ?: (env.databaseUrl.ifEmpty { yaml.databaseUrl.ifEmpty { props.databaseUrl } }),
            port = cli["rag.port"] as? Int ?: (if (env.port != 5432) env.port else (if (yaml.port != 5432) yaml.port else props.port)),
            username = cli["rag.username"]?.toString() ?: (env.username.ifEmpty { yaml.username.ifEmpty { props.username } }),
            password = cli["rag.password"]?.toString() ?: (env.password.ifEmpty { yaml.password.ifEmpty { props.password } }),
            tableName = cli["rag.tableName"]?.toString() ?: (if (env.tableName != "embeddings") env.tableName else (if (yaml.tableName != "embeddings") yaml.tableName else props.tableName))
        )
    }

    private val defaultSubgraphs = listOf("service layer", "config layer", "task layer", "model layer", "RAG")

    private fun mergeDiagramDocsConfig(env: DiagramDocsConfig, props: DiagramDocsConfig, yaml: DiagramDocsConfig, cli: Map<String, Any?>): DiagramDocsConfig {
        return DiagramDocsConfig(
            enabled = cli["diagramDocs.enabled"]?.toString()?.toBoolean() ?: (if (!env.enabled) env.enabled else (if (!yaml.enabled) yaml.enabled else props.enabled)),
            outputDir = cli["diagramDocs.outputDir"]?.toString() ?: (if (env.outputDir != "diagrams/auto") env.outputDir else (if (yaml.outputDir != "diagrams/auto") yaml.outputDir else props.outputDir)),
            subgraphs = (cli["diagramDocs.subgraphs"] as? List<String>) ?: (if (env.subgraphs != defaultSubgraphs) env.subgraphs else (if (yaml.subgraphs != defaultSubgraphs) yaml.subgraphs else props.subgraphs)),
            model = cli["diagramDocs.model"]?.toString() ?: (if (env.model != "qwen3.5-cloud") env.model else (if (yaml.model != "qwen3.5-cloud") yaml.model else props.model))
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
