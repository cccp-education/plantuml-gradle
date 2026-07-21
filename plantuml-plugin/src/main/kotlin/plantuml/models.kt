package plantuml

import java.io.File

/**
 * Main configuration model for the PlantUML plugin.
 *
 * Contains all configuration settings for input/output directories, LLM providers,
 * Git integration, and RAG (Retrieval-Augmented Generation) database settings.
 * Configuration can be loaded from YAML files or overridden via CLI parameters.
 *
 * @property input Input configuration for prompts directory and language
 * @property output Output configuration for diagrams, images, validations, and RAG
 * @property langchain4j LLM configuration including provider selection and API keys
 * @property git Git configuration for automated commits
 * @property rag RAG database configuration for vector embeddings storage
 */
data class PlantumlConfig(
    val input: InputConfig = InputConfig(),
    val output: OutputConfig = OutputConfig(),
    val langchain4j: LangchainConfig = LangchainConfig(),
    val git: GitConfig = GitConfig(),
    val rag: RagConfig = RagConfig(),
    val diagramDocs: DiagramDocsConfig = DiagramDocsConfig(),
    val incremental: plantuml.incremental.IncrementalConfig = plantuml.incremental.IncrementalConfig(),
    val language: String = "en",
    val supportedLanguages: List<String> = listOf("en")
) {
    companion object {
        val SUPPORTED_LANGS = setOf("en", "zh", "hi", "es", "fr", "ar", "bn", "pt", "ru", "ur")

        /**
         * Loads PlantUML configuration from a YAML file.
         *
         * @param configFile The YAML configuration file to load
         * @return A [PlantumlConfig] instance with loaded settings
         */
        fun load(configFile: File): PlantumlConfig {
            // In a real implementation, this would load from YAML
            return PlantumlConfig()
        }
    }
}

/**
 * Input configuration for the PlantUML plugin.
 *
 * @property prompts Directory containing `.prompt` files to process (default: "prompts")
 * @property defaultLang Default language for prompts (default: "en")
 */
data class InputConfig(
    val prompts: String = "prompts",
    val defaultLang: String = "en"
)

/**
 * Output configuration for generated artifacts.
 *
 * Defines directories and formats for generated diagrams, images, validation reports,
 * and RAG training data.
 *
 * @property diagrams Directory for generated PlantUML diagram files (default: "generated/diagrams")
 * @property images Directory for rendered diagram images (default: "generated/images")
 * @property validations Directory for LLM validation reports in JSON format (default: "generated/validations")
 * @property rag Directory for RAG training data and embeddings (default: "generated/rag")
 * @property format Image output format (default: "png")
 * @property theme PlantUML theme for diagram rendering (default: "default")
 */
data class OutputConfig(
    val diagrams: String = "generated/diagrams",
    val images: String = "generated/images",
    val validations: String = "generated/validations",
    val rag: String = "generated/rag",
    val format: String = "png",
    val theme: String = "default"
)

/**
 * LangChain4j LLM provider configuration.
 *
 * Supports multiple LLM providers: Ollama (local), OpenAI, Gemini, Mistral, Claude,
 * HuggingFace, and Groq. Includes validation settings and iteration limits.
 *
 * @property maxIterations Maximum LLM correction iterations (default: 5)
 * @property model Active LLM provider name (default: "ollama")
 * @property validation Enable LLM-based diagram validation (default: true)
 * @property validationPrompt Prompt template for LLM validation
 * @property ollama Ollama-specific configuration (baseUrl, modelName)
 * @property gemini Google Gemini API configuration
 * @property mistral Mistral AI API configuration
 * @property openai OpenAI API configuration
 * @property claude Anthropic Claude API configuration
 * @property huggingface HuggingFace API configuration
 * @property groq Groq API configuration
 */
data class LangchainConfig(
    val maxIterations: Int = 5,
    val model: String = "ollama",
    val validation: Boolean = true,
    val validationPrompt: String = "Rate this diagram on clarity, completeness, and best practices. Return a JSON with 'score' (1-10) and 'feedback' (string) and 'recommendations' (array).",
    val rotationStrategy: String = "ROUND_ROBIN",
    val fallbackEnabled: Boolean = true,
    val freemiumRatio: Double = 0.0,
    val ollama: OllamaConfig = OllamaConfig(),
    val gemini: ApiKeyConfig = ApiKeyConfig(),
    val mistral: ApiKeyConfig = ApiKeyConfig(),
    val openai: ApiKeyConfig = ApiKeyConfig(),
    val claude: ApiKeyConfig = ApiKeyConfig(),
    val huggingface: ApiKeyConfig = ApiKeyConfig(),
    val groq: ApiKeyConfig = ApiKeyConfig()
)

/**
 * Git integration configuration.
 *
 * Settings for automated Git commits when diagrams are updated.
 *
 * @property userName Git commit author name (default: "github-actions[bot]")
 * @property userEmail Git commit author email (default: "github-actions[bot]@users.noreply.github.com")
 * @property commitMessage Git commit message template (default: "chore: update PlantUML diagrams [skip ci]")
 * @property watchedBranches List of branches to watch for automated updates (default: ["main", "develop"])
 */
data class GitConfig(
    val userName: String = "github-actions[bot]",
    val userEmail: String = "github-actions[bot]@users.noreply.github.com",
    val commitMessage: String = "chore: update PlantUML diagrams [skip ci]",
    val watchedBranches: List<String> = listOf("main", "develop")
)

/**
 * Ollama LLM provider configuration.
 *
 * @property baseUrl Ollama server base URL (default: "http://localhost:11438")
 * @property modelName Ollama model name to use (default: "gpt-oss:120b-cloud")
 * @property pool List of Ollama instances for rotation (optional)
 */
data class OllamaConfig(
    val baseUrl: String = "http://localhost:11438",
    val modelName: String = "gpt-oss:120b-cloud",
    val pool: List<ApiKeyPoolEntry> = emptyList()
)

/**
 * API key configuration for cloud LLM providers.
 *
 * @property apiKey API key for the LLM provider (default: empty)
 * @property baseUrl Base URL for the API (optional, for custom endpoints)
 * @property modelName Model name to use (optional, for provider-specific models)
 * @property pool List of API keys for rotation (optional)
 */
data class ApiKeyConfig(
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val pool: List<ApiKeyPoolEntry> = emptyList()
)

/**
 * Entry in the API key rotation pool.
 *
 * @property id Unique identifier for this key entry
 * @property email Email associated with the API key account
 * @property name Friendly name for this key
 * @property keyRef Reference to the API key (e.g., env var name)
 * @property provider The API provider name
 * @property services List of services this key can access
 * @property baseUrl Base URL for the API (optional, overrides provider default)
 * @property quota Quota configuration for this key
 * @property tier Priority tier for tiered rotation (ENTERPRISE, PRO, FREE; default: FREE)
 * @property weight Relative weight for intra-tier selection (default: 1)
 */
data class ApiKeyPoolEntry(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val keyRef: String = "",
    val provider: String = "",
    val services: List<String> = emptyList(),
    val baseUrl: String = "",
    val quota: PoolQuotaConfig = PoolQuotaConfig(),
    val tier: String = "FREE",
    val weight: Int = 1
)

/**
 * Quota configuration for an API key in the pool.
 *
 * @property limitType Type of quota limit (REQUESTS, TOKENS, etc.)
 * @property limitValue Maximum allowed value
 * @property thresholdPercent Percentage at which rotation is triggered (default: 80)
 */
data class PoolQuotaConfig(
    val limitType: String = "REQUESTS",
    val limitValue: Long = 1000,
    val thresholdPercent: Int = 80
)

/**
 * RAG (Retrieval-Augmented Generation) database configuration.
 *
 * PostgreSQL connection settings for storing vector embeddings using pgvector extension.
 *
 * @property databaseUrl PostgreSQL host URL (default: empty)
 * @property port PostgreSQL port (default: 5432)
 * @property username PostgreSQL database username (default: empty)
 * @property password PostgreSQL database password (default: empty)
 * @property tableName Table name for embeddings storage (default: "embeddings")
 */
data class RagConfig(
    val databaseUrl: String = "",
    val port: Int = 5432,
    val username: String = "",
    val password: String = "",
    val tableName: String = "embeddings"
)

/**
 * Represents a complete PlantUML diagram with conversation history.
 *
 * Contains the full LLM conversation trail and the final PlantUML code.
 *
 * @property conversation List of conversation turns (prompt → response) showing the LLM interaction history
 * @property plantuml The final PlantUML code with description
 */
data class PlantumlDiagram(
    val conversation: List<String>,
    val plantuml: PlantumlCode
)

/**
 * Represents PlantUML code with a human-readable description.
 *
 * @property code The raw PlantUML source code
 * @property description Human-readable description of what the diagram represents
 */
data class PlantumlCode(
    val code: String,
    val description: String
)

/**
 * Represents LLM-based validation feedback for a diagram.
 *
 * @property score Quality score from 1 to 10 (10 = excellent)
 * @property feedback Detailed textual feedback about diagram quality
 * @property recommendations List of actionable improvement suggestions
 */
data class ValidationFeedback(
    val score: Int,
    val feedback: String,
    val recommendations: List<String>
)

/**
 * Configuration for automated diagram documentation generation.
 *
 * Controls the `generateDiagramDocs` task which creates prompt files
 * from graphify knowledge graph communities.
 *
 * @property enabled Whether diagram docs generation is active (default: true)
 * @property outputDir Directory for generated diagram documentation (default: "diagrams/auto")
 * @property subgraphs List of subgraph names to generate prompts for
 * @property model LLM model to use for diagram generation (default: "qwen3.5-cloud")
 */
data class DiagramDocsConfig(
    val enabled: Boolean = true,
    val outputDir: String = "diagrams/auto",
    val subgraphs: List<String> = listOf(
        "service layer", "config layer", "task layer", "model layer", "RAG"
    ),
    val model: String = "qwen3.5-cloud"
)