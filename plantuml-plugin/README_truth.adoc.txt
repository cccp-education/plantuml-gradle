= PlantUML Plugin — Developer Guide
:toc: left
:toclevels: 3
:source-highlighter: rouge
:icons: font
:lang: en
:hardbreaks-option:
:plugin-version: 0.0.5

++++
<p align="right">
  <a href="README_truth_fr.adoc">
    <img src="../.github/workflows/readmes/images/lang-fr-red.svg" alt="Français" width="64"/>
  </a>
</p>
++++

image:https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin[Kotlin]
image:https://img.shields.io/badge/Gradle-9.x-02303A?logo=gradle[Gradle]
image:https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk[Java]
image:https://img.shields.io/badge/License-Apache%202.0-blue.svg[License]

== Description

`com.cheroliv.plantuml` is a Gradle plugin that generates PlantUML diagrams via AI (LangChain4j) from `.prompt` files.
It exposes a minimal DSL to the consumer and handles all repository, dependency, and task configuration internally.
It integrates a RAG (Retrieval-Augmented Generation) pipeline for automated PlantUML diagram generation via multiple LLM providers.

== Current version: {plugin-version}

== Prerequisites

* JDK 25 (tested with Eclipse Temurin 25.0.2), 23+ supported
* Gradle 9.4.0+
* Docker (for the pgvector container used by the RAG pipeline)

== Project Structure

[source]
----
plantuml-plugin/
├── plantuml-plugin/
│   ├── src/
│   │   ├── main/kotlin/plantuml/
│   │   │   ├── PlantumlPlugin.kt         # Plugin entry point — thin orchestrator
│   │   │   ├── PlantumlManager.kt        # All plugin logic — nested objects
│   │   │   └── models.kt                 # Data models (PlantumlConfig, PlantumlDiagram, PlantumlCode…)
│   │   │   ├── service/
│   │   │   │   ├── PlantumlService.kt    # Syntax validation + image generation
│   │   │   │   ├── DiagramProcessor.kt   # LLM interaction + retry logic
│   │   │   │   └── LlmService.kt         # LLM providers (Ollama, OpenAI, Gemini…)
│   │   │   └── tasks/
│   │   │       ├── ProcessPlantumlPromptsTask.kt    # Process .prompt files
│   │   │       ├── ValidatePlantumlSyntaxTask.kt    # Validate PlantUML syntax
│   │   │       └── ReindexPlantumlRagTask.kt        # RAG reindexing
│   │   ├── test/kotlin/                # Unit + Cucumber tests
│   │   └── functionalTest/kotlin/      # GradleTestKit functional tests
│   └── build.gradle.kts                # Plugin build script
├── gradle/
│   ├── libs.versions.toml              # Dependency catalogue
│   └── wrapper/                        # Gradle Wrapper
├── settings.gradle.kts
├── README.adoc                          # This file
└── README_fr.adoc                       # French version
----

=== Internal Architecture

[plantuml]
----
@startuml
skinparam componentStyle rectangle

package "plantuml" {
  component "PlantumlPlugin\n(orchestrator)" as Plugin
  component "PlantumlManager\n(core logic)" as Manager
  component "models.kt\n(PlantumlConfig, PlantumlDiagram…)" as Models
}

package "service" {
  component "PlantumlService\n(syntax + images)" as PS
  component "DiagramProcessor\n(LLM + retry)" as DP
  component "LlmService\n(providers)" as LS
}

package "tasks" {
  component "ProcessPlantumlPromptsTask" as PPT
  component "ValidatePlantumlSyntaxTask" as VST
  component "ReindexPlantumlRagTask" as RRT
  component "GenerateKnowledgeGraphDiagramTask" as KGDT
  component "GenerateDiagramDocsTask" as GDDT
}

package "Infrastructure" {
  component "pgvector\n(Docker)" as PG
  component "AllMiniLmL6V2\n(ONNX in-process)" as Embed
  component "LLM Providers\n(Ollama/OpenAI/Gemini…)" as LLM
}

Plugin --> Manager
Plugin --> PPT
Plugin --> VST
Plugin --> RRT
Plugin --> KGDT
Plugin --> GDDT
Manager --> Models
PPT --> DP
DP --> LS
LS --> LLM
RRT --> PG
RRT --> Embed
@enduml
----

== Key Technical Decisions

=== Plugin ID

The plugin ID is `com.cheroliv.plantuml`, applied via:

[source,kotlin]
----
plugins {
    id("com.cheroliv.plantuml") version "0.0.5"
}
----

=== Execution Mode — `OUT_OF_PROCESS`

RAG tasks run out-of-process to isolate native ONNX library loading (`libtokenizers.so`).
This prevents `UnsatisfiedLinkError` on subsequent builds.

=== RAG with PostgreSQL + pgvector

The RAG pipeline uses PostgreSQL with the pgvector extension for embedding storage.
Embeddings are generated using `AllMiniLmL6V2` (ONNX in-process, dim=384).

=== PgVectorService — Gradle BuildService + testcontainers

The pgvector container is managed by testcontainers in tests, and by Docker in production.
RAG tasks declare the dependency via `@ServiceReference` (Gradle 7.4+) — the only annotation
that guarantees Gradle keeps the service alive for the full duration of the task.

[source,kotlin]
----
// ReindexPlantumlRagTask.kt — @ServiceReference guarantees lifecycle
abstract class ReindexPlantumlRagTask : DefaultTask() {
    @get:ServiceReference
    abstract val pgVectorService: Property<PgVectorService>

    protected fun service(): PgVectorService =
        pgVectorService.get().also { it.start() }
}
----

The host port is assigned dynamically (binding `0:5432`) to avoid collision with any existing PostgreSQL.
The service is stopped automatically by Gradle at the end of the build.

=== SSL pgvector — `sslmode=disable`

The PostgreSQL JDBC driver attempts SSL negotiation by default, causing an `EOFException`
against a plain Docker container with no SSL configured. The embedding store is created
with `sslmode=disable` to prevent this error.

=== `--no-daemon` required for RAG tasks

The Gradle daemon reuses the JVM process between builds, preventing native ONNX library
reload (`libtokenizers.so`) and causing `UnsatisfiedLinkError` on the second build.
All RAG tasks must be run with `--no-daemon`, or the project should declare
`org.gradle.daemon=false` in `gradle.properties`.

=== Diagram file naming convention

[cols="1,2,2"]
|===
| File | Pattern | Example

| Prompt file
| `*.prompt`
| `architecture.prompt`

| Generated PlantUML
| `*.puml`
| `architecture.puml`

| Generated image
| `*.png`
| `architecture.png`
|===

The output file name is derived from the prompt file name (same base name).

== PlantumlPlugin.apply() — Orchestration

[source,kotlin]
----
override fun apply(project: Project) {
    with(project) {
        checkJavaVersion()
        scaffoldDirectoriesIfAbsent()
        configureRepositories()
        applyPlugins()
        configureDependencies()
        configureExtensions()
        registerTasks()
        afterEvaluate {
            createChatTasks()  // registers all AI tasks
        }
    }
}
----

`createChatTasks()` is defined in `PlantumlManager` and registers:
- `PgVectorService` via `gradle.sharedServices.registerIfAbsent()`
- `processPlantumlPrompts`, `validatePlantumlSyntax`, `reindexPlantumlRag` (typed tasks)
- smoke-test tasks for LLM providers

== Data Model

=== PlantumlConfig

[source,kotlin]
----
data class PlantumlConfig(
    val input: InputConfig = InputConfig(),
    val output: OutputConfig = OutputConfig(),
    val langchain: LangchainConfig = LangchainConfig(),
    val git: GitConfig = GitConfig(),
    val ollama: OllamaConfig = OllamaConfig(),
    val apiKey: ApiKeyConfig = ApiKeyConfig(),
    val rag: RagConfig = RagConfig(),
)
----

=== InputConfig

[source,kotlin]
----
data class InputConfig(
    val directory: String = "prompts",
    val includes: List<String> = listOf("**/*.prompt"),
    val excludes: List<String> = emptyList(),
)
----

=== OutputConfig

[source,kotlin]
----
data class OutputConfig(
    val directory: String = "generated",
    val diagramsDir: String = "diagrams",
    val imagesDir: String = "images",
    val ragDir: String = "rag",
)
----

=== PlantumlDiagram

[source,kotlin]
----
data class PlantumlDiagram(
    val code: String,
    val sourceFile: File,
    val outputFile: File,
    val imageFile: File,
    val validationFeedback: ValidationFeedback? = null,
)
----

== Build & Publish

=== Publish to Maven Local (for local testing)

[source,bash]
----
./gradlew publishToMavenLocal
----

=== Run tests

[source,bash]
----
# Unit tests
./gradlew test

# Functional tests (GradleTestKit)
./gradlew functionalTest

# Cucumber BDD tests
./gradlew cucumberTest

# All tests
./gradlew check
----

== Cucumber BDD Tests

The plugin includes a comprehensive BDD test suite using Cucumber-JVM. Tests are located in `src/test/features/` with step definitions in `src/test/scenarios/`.

=== Test Tags

[cols="1,3"]
|===
| Tag | Purpose

| `@wip`
| Scenarios under development — excluded from default test runs

| `@integration`
| Scenarios requiring a real LLM (Ollama) — excluded from default test runs
|===

=== Running Cucumber Tests

[source,bash]
----
# Run all Cucumber tests (excludes @wip and @integration)
./gradlew cucumberTest

# Run specific scenario
./gradlew cucumberTest --tests "*ScenarioName*"

# Run only @wip scenarios
./gradlew cucumberTest -Pcucumber.filter.tags="@wip"

# Run integration tests with real LLM
./gradlew cucumberTest -Pcucumber.filter.tags="@integration"
----

=== Test Reports

Cucumber generates HTML and JSON reports:

* **HTML**: `build/reports/cucumber.html`
* **JSON**: `build/reports/cucumber.json`

Open `build/reports/cucumber.html` in a browser for detailed results.

=== Available Steps

==== Initialization Steps

[source,gherkin]
----
Given a prompt file "test.prompt" with content "Generate a diagram"
Given a valid PlantUML file "test.puml" with content "@startuml\nactor User\n@enduml"
Given an invalid PlantUML file "test.puml" with content "@startumlnactor User\n@endulm"
----

==== Mock LLM Steps

[source,gherkin]
----
Given a mock LLM that returns a valid PlantUML diagram
Given a mock LLM that returns an invalid PlantUML diagram on first attempt
Given a mock LLM that returns a valid PlantUML diagram on second attempt
Given a mock LLM that always returns invalid PlantUML diagrams
Given a mock LLM that returns invalid PlantUML diagrams for first 3 attempts
Given a mock LLM that returns a sequence of responses: invalid then valid
Given a mock LLM that returns a sequence of 4 responses: 3 invalid then valid
----

==== Task Execution Steps

[source,gherkin]
----
When I run processPlantumlPrompts task
When I run processPlantumlPrompts task with max 5 iterations
When I run validatePlantumlSyntax task with file "test.puml"
----

==== Verification Steps

[source,gherkin]
----
Then a PlantUML diagram should be generated
Then a PNG image should be created
Then the prompt file should be deleted
Then the syntax should be reported as valid
Then the syntax should be reported as invalid
Then error details should be provided
Then the LLM should correct the syntax after iteration
Then a valid diagram should be generated
Then validation feedback should be saved
----

==== Attempt History Steps

[source,gherkin]
----
Then attempt history should be tracked with 5 entries
Then the first entry should indicate syntax error
Then the second entry should indicate success
Then attempt history should be archived with 6 entries
Then the first three entries should indicate syntax errors
Then the fourth entry should indicate success
Then no diagram should be generated
----

=== Test Architecture

Cucumber tests use a shared `PlantumlWorld` state object that:

* Creates isolated temporary Gradle projects for each scenario
* Manages mock LLM HTTP servers
* Handles async Gradle execution via coroutines
* Automatically cleans up temporary files after each scenario (via `@After` hook)

Tests use `includeBuild()` to reference the plugin under development directly from the classpath, avoiding the need for `publishToMavenLocal`.

=== Publish to Gradle Plugin Portal

[source,bash]
----
./gradlew publishPlugins
----
Requires `gradle.publish.key` and `gradle.publish.secret` in `~/.gradle/gradle.properties`.

== Plugin DSL

[source,kotlin]
----
plantuml {
    // Path to the YAML configuration file (required)
    configPath = file("plantuml-config.yml").absolutePath
}
----

== Registered Tasks

[cols="1,1,2"]
|===
| Task | Group | Description

| `processPlantumlPrompts`
| plantuml
| Processes `.prompt` files and generates PlantUML diagrams via LLM

| `validatePlantumlSyntax`
| plantuml
| Validates PlantUML syntax of generated `.puml` files

| `reindexPlantumlRag`
| plantuml-rag
| Drops and fully rebuilds the pgvector embedding index

| `generateKnowledgeGraphDiagram`
| plantuml
| Generates a knowledge graph diagram from `graphify-out/graph.json` (deterministic, no LLM)

| `generateDiagramDocs`
| plantuml
| Generates diagram documentation (dogfooding)

| `help`
| help
| Displays plugin help information

| `tasks`
| help
| Lists all available tasks
|===

== AI Integration — RAG Pipeline

`PlantumlManager.createChatTasks()` is called in `afterEvaluate` and registers all AI tasks.
The RAG pipeline relies on three components:

* `PgVectorService` — Gradle `BuildService` managing the Docker lifecycle via docker-java
* `RagManager` — incremental indexing by SHA-256, cosine-similarity search
* `LlmService` — model resolution, prompt construction, LLM call

=== RAG Pipeline Lifecycle

[plantuml]
----
@startuml
participant "Gradle" as G
participant "ReindexPlantumlRagTask" as RT
participant "PgVectorService\n(BuildService)" as PGS
participant "RagManager" as RM
participant "LlmService" as LS
participant "LLM Provider" as LLM
collections "prompts/" as Files
collections "generated/rag/" as RAG

G -> RT : task execution
RT -> PGS : start() — idempotent
activate PGS
PGS --> RT : Docker port assigned

RT -> RM : reindex()
RM -> PGS : port / credentials
RM --> RT : RAG chunks

RT -> LS : resolveModel(provider)
LS --> RT : ChatModel

RT -> LLM : systemPrompt + userMessage + RAG chunks
LLM --> RT : response

RT -> RAG : store embeddings

G -> PGS : close() — end of build
deactivate PGS
@enduml
----

=== Supported providers

[cols="1,1,1"]
|===
| Provider | LangChain4j module | Key in plantuml-config.yml

| Ollama (local)
| `langchain4j-ollama`
| — (no key required)

| OpenAI
| `langchain4j-open-ai`
| `apiKey.openai`

| Google Gemini
| `langchain4j-google-ai-gemini`
| `apiKey.gemini`

| Mistral AI
| `langchain4j-mistral-ai`
| `apiKey.mistral`

| HuggingFace
| `langchain4j-open-ai` (router)
| `apiKey.huggingface`

| Groq
| `langchain4j-open-ai` (router)
| `apiKey.groq`

| Claude (Anthropic)
| `langchain4j-anthropic`
| `apiKey.claude`
|===

=== RAG tasks — usage

[source,bash]
----
# Rebuild index (after adding/removing prompts)
./gradlew reindexPlantumlRag --no-daemon

# Process prompts
./gradlew processPlantumlPrompts \
  -Pplantuml.config=file("plantuml-config.yml").absolutePath \
  -Pai.provider=ollama \
  --no-daemon

# Validate syntax
./gradlew validatePlantumlSyntax
----

Properties available for tasks:

[cols="1,1,2"]
|===
| Property | Default | Description

| `-Pplantuml.config`
| `plantuml-config.yml`
| Path to YAML configuration

| `-Pai.provider`
| `ollama`
| LLM provider

| `-Pplantuml.output.diagrams`
| `generated/diagrams`
| Output directory for `.puml` files

| `-Pplantuml.output.images`
| `generated/images`
| Output directory for `.png` files

| `-Pplantuml.output.rag`
| `generated/rag`
| Output directory for RAG embeddings
|===

=== Knowledge Graph Integration — Graphify

The `generateKnowledgeGraphDiagram` task is **deterministic** (no LLM) and reads `graphify-out/graph.json`.

==== Components

[cols="1,2"]
|===
| Component | Role

| `KnowledgeGraphParser`
| Parses `graph.json` — supports 3 formats: graphify native (`nodes`+`links`), legacy (`communities` array), flat

| `KnowledgeGraphRenderer`
| Renders `KnowledgeGraph` to PlantUML — groups nodes by type (Classes/Files), filters, clean labels

| `GenerateKnowledgeGraphDiagramTask`
| Gradle task — orchestrates parse → render → validate → PNG

| `kgmodels.kt`
| `KnowledgeGraph`, `KnowledgeGraphNode`, `KnowledgeGraphEdge`, `KnowledgeGraphCommunity`, `EdgeType`

| `GraphifyPromptAdapter`
| Generates `.prompt` files from graph.json communities
|===

==== Knowledge Graph data flow

[plantuml]
----
@startuml
participant "Gradle" as G
participant "GenerateKnowledgeGraphDiagramTask" as Task
participant "KnowledgeGraphParser" as Parser
participant "KnowledgeGraphRenderer" as Renderer
participant "PlantumlService" as PS

collections "graphify-out/graph.json" as JSON

G -> Task : execute
Task -> JSON : read
Task -> Parser : parse(graph.json)
Parser --> Task : KnowledgeGraph
Task -> Renderer : render(graph, filters)
Renderer --> Task : PlantUML code
Task -> PS : validateSyntax(plantumlCode)
alt valid
  Task -> PS : generateImage → .png
else invalid
  Task -> Task : save .puml with warning
end
----

==== Graphify native format

The parser auto-detects the graphify native format:

[source,json]
----
{
  "nodes": [
    {"id": "0", "label": "MyService", "file_type": "code", "community": 0}
  ],
  "links": [
    {"source": "0", "target": "1", "relation": "calls", "confidence": "EXTRACTED", "weight": 0.9}
  ]
}
----

Key points:

* `community` is an integer → rendered as `community_0`, `community_1`, etc.
* `links` (not `edges`) is the graphify native key
* `confidence` can be a string (`EXTRACTED`/`INFERRED`) or a numeric score
* Numeric IDs are resolved to labels via `idToLabel` mapping

==== Gradle properties for KG

[cols="1,1,2"]
|===
| Property | Default | Description

| `-Pplantuml.kg.community`
| _(all)_
| Filter communities by name (substring match)

| `-Pplantuml.kg.edgeTypes`
| _(all)_
| Comma-separated: `EXTRACTED`, `INFERRED`, `AMBIGUOUS`

| `-Pplantuml.kg.minConfidence`
| `0.0`
| Minimum confidence for edges

| `-Pplantuml.kg.maxNodes`
| _(unlimited)_
| Max nodes to render (useful for large graphs)

| `-Pplantuml.kg.nodeTypes`
| _(all)_
| Comma-separated node types (e.g. `class`, `code`)

| `-Pplantuml.kg.outputDir`
| `diagrams/knowledge-graph`
| Output directory for `.puml` and `.png` files
|===

==== Example usage

[source,bash]
----
# Full diagram
./gradlew generateKnowledgeGraphDiagram

# Single community with limited nodes
./gradlew generateKnowledgeGraphDiagram \
  -Pplantuml.kg.community=community_0 \
  -Pplantuml.kg.maxNodes=15
----

=== Prompt strategy

The `DiagramProcessor` manages the prompt lifecycle:

* `systemPrompt` — Defines PlantUML generation rules
* `userMessage` — Contains the `.prompt` content + RAG chunks
* `correctionPrompt` — Used when syntax validation fails (retry loop)

Key rules enforced in `systemPrompt`:

* Valid PlantUML syntax only
* Start with `@startuml` and end with `@enduml`
* Use appropriate diagram type (class, component, sequence, etc.)
* No markdown code blocks (raw PlantUML only)

=== Available models by provider

[cols="1,2"]
|===
| Provider | Models (catalogue)

| Ollama
| `llama3.2:3b`, `smollm:135m`, `gemma3:1b`, `mistral:7b`

| OpenAI
| `gpt-4o`, `gpt-4o-mini`, `gpt-3.5-turbo`

| Gemini
| `gemini-2.5-flash`, `gemini-2.0-flash`

| Mistral
| `mistral-large-latest`, `mistral-small-latest`, `open-mistral-nemo`

| HuggingFace
| `meta-llama/Llama-3.1-8B-Instruct`, `Qwen/Qwen2.5-Coder-32B-Instruct`

| Groq
| `llama-3.3-70b-versatile`, `mixtral-8x7b-32768`

| Claude
| `claude-3-5-sonnet-latest`, `claude-3-haiku-20240307`
|===

== Scaffold — Auto-Initialisation

=== `scaffoldDirectoriesIfAbsent()`

Checks whether the consumer project has the required directories. If not, creates them:

* `prompts/` — Input `.prompt` files
* `generated/diagrams/` — Output `.puml` files
* `generated/images/` — Output `.png` files
* `generated/rag/` — RAG embeddings

All directories are created if absent — existing content is never overwritten.

=== `scaffoldConfigIfAbsent()`

If `plantuml-config.yml` is absent, generates one automatically by serialising a default
`PlantumlConfig` instance via `yamlMapper` — no hardcoded YAML strings.

== Dependencies

=== Key runtime dependencies

[cols="1,1"]
|===
| Dependency | Purpose

| `dev.langchain4j:langchain4j`
| Core LLM abstraction

| `dev.langchain4j:langchain4j-ollama`
| Local Ollama models integration

| `dev.langchain4j:langchain4j-open-ai`
| OpenAI + HuggingFace + Groq integration

| `dev.langchain4j:langchain4j-google-ai-gemini`
| Gemini LLM integration

| `dev.langchain4j:langchain4j-mistral-ai`
| Mistral AI integration

| `dev.langchain4j:langchain4j-anthropic`
| Claude (Anthropic) integration

| `dev.langchain4j:langchain4j-pgvector`
| pgvector embedding store

| `dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2`
| In-process ONNX embedding model (dim=384)

| `org.postgresql:postgresql`
| PostgreSQL JDBC driver

| `com.github.docker-java:docker-java-core`
| Docker management for PgVectorService

| `com.fasterxml.jackson:jackson-module-kotlin`
| YAML/JSON configuration parsing

| `net.sourceforge.plantuml:plantuml`
| PlantUML syntax validation + image generation
|===

== Consumer Requirements

The consumer configuration is minimal — the plugin handles all repository and dependency setup internally.

=== settings.gradle.kts
[source,kotlin]
----
pluginManagement.repositories {
    mavenLocal()
    gradlePluginPortal()
}
rootProject.name = "your-project-name"
----

=== build.gradle.kts
[source,kotlin]
----
plugins { alias(libs.plugins.plantuml) }

plantuml {
    configPath = file("plantuml-config.yml").absolutePath
}
----

=== gradle/libs.versions.toml
[source,toml,subs="attributes+"]
----
[versions]
plantuml = "{plugin-version}"

[plugins]
plantuml = { id = "com.cheroliv.plantuml", version.ref = "plantuml" }
----

=== gradle.properties (recommended)
[source,properties]
----
# Required for RAG tasks — prevents UnsatisfiedLinkError on libtokenizers.so
org.gradle.daemon=false
----

== Gradle Feature Compatibility

[source,kotlin]
----
gradlePlugin {
    plugins {
        create("plantuml") {
            compatibility {
                features {
                    // RAG tasks run OUT_OF_PROCESS with native ONNX libraries —
                    // not compatible with Configuration Cache.
                    configurationCache = false
                }
            }
        }
    }
}
----

Declaring `configurationCache = false` is honest and recommended — it informs users clearly
and has no negative consequences for the plugin's Portal ranking beyond the Configuration Cache badge.

== Architecture Details

=== C4 View — Plugin Context

[plantuml]
----
@startuml
actor Developer

rectangle "Developer Environment" {
  rectangle "Gradle Build System" {
    component "PlantUML Gradle Plugin"
  }
}

rectangle "PlantUML Internal Components" {
  component "PlantumlManager"
  component "LlmService"
  component "DiagramProcessor"
  component "PlantumlService"
  component "RagManager"
  component "PgVectorService"
  component "KnowledgeGraphParser"
  component "KnowledgeGraphRenderer"
  component "GraphifyPromptAdapter"
}

rectangle "RAG Pipeline" {
  component "AllMiniLmL6V2\n(ONNX in-process)"
  component "pgvector\n(Docker)"
}

rectangle "Knowledge Graph Pipeline" {
  collections "graphify-out/graph.json" as KGJSON
}

rectangle "External Systems" {
  component "LLM Providers\n(Ollama / OpenAI / Gemini / Mistral / HF / Groq / Claude)"
  component "PlantUML\n(syntax validation + image generation)"
  component "Graphify\n(pip install graphify)"
}

Developer --> "Gradle Build System"
"Gradle Build System" --> "PlantUML Gradle Plugin"

"PlantUML Gradle Plugin" --> "PlantumlManager"
"PlantumlManager" --> "LlmService"
"PlantumlManager" --> "DiagramProcessor"
"PlantumlManager" --> "PlantumlService"
"PlantumlManager" --> "RagManager"
"PlantumlManager" --> "PgVectorService"
"PlantumlManager" --> "KnowledgeGraphParser"
"PlantumlManager" --> "KnowledgeGraphRenderer"
"PlantumlManager" --> "GraphifyPromptAdapter"

"RagManager" --> "AllMiniLmL6V2\n(ONNX in-process)"
"RagManager" --> "pgvector\n(Docker)"
"PgVectorService" --> "pgvector\n(Docker)"

"KnowledgeGraphParser" --> KGJSON
"KnowledgeGraphRenderer" --> "PlantumlService"
"GraphifyPromptAdapter" --> KGJSON

"Graphify\n(pip install graphify)" --> KGJSON

"LlmService" --> "LLM Providers\n(Ollama / OpenAI / Gemini / Mistral / HF / Groq / Claude)"
"PlantumlService" --> "PlantUML\n(syntax validation + image generation)"
@enduml
----

=== Hexagonal Architecture (Ports & Adapters)

The plugin follows a hexagonal architecture that separates business logic,
external interfaces and infrastructure technologies.
This design guarantees LLM provider independence and high testability.

[plantuml]
----
@startuml
skinparam componentStyle rectangle

package "Domain Core" {
  component "Diagram Generation Logic"
  component "RAG Retrieval"
  component "Knowledge Graph Rendering\n(KnowledgeGraphRenderer)"
  component "PlantUML Context\n(PlantumlDiagram)"
}

package "Application Layer" {
  component "LlmService"
  component "DiagramProcessor"
  component "PlantumlService"
  component "RagManager"
  component "KnowledgeGraphParser"
  component "GraphifyPromptAdapter"
}

package "Ports" {
  interface "LLM Port"
  interface "Embedding Store Port"
  interface "Docker Service Port"
  interface "PlantUML Validator Port"
  interface "Knowledge Graph Data Port"
}

package "Adapters" {
  component "Ollama Adapter"
  component "OpenAI Adapter"
  component "Gemini Adapter"
  component "Mistral Adapter"
  component "HuggingFace Adapter"
  component "Groq Adapter"
  component "Claude Adapter"
  component "PgVectorEmbeddingStore"
  component "PgVectorService\n(docker-java)"
  component "PlantUML Validator"
  component "Graphify JSON Adapter\n(KnowledgeGraphParser)"
  component "Graphify Prompt Adapter\n(GraphifyPromptAdapter)"
  component "Gradle Task Adapter\n(ProcessPlantumlPromptsTask)"
}

"LlmService" --> "LLM Port"
"RagManager" --> "Embedding Store Port"
"RagManager" --> "Docker Service Port"
"PlantumlService" --> "PlantUML Validator Port"
"KnowledgeGraphParser" --> "Knowledge Graph Data Port"

"LLM Port" --> "Ollama Adapter"
"LLM Port" --> "OpenAI Adapter"
"LLM Port" --> "Gemini Adapter"
"LLM Port" --> "Mistral Adapter"
"LLM Port" --> "HuggingFace Adapter"
"LLM Port" --> "Groq Adapter"
"LLM Port" --> "Claude Adapter"

"Embedding Store Port" --> "PgVectorEmbeddingStore"
"Docker Service Port" --> "PgVectorService\n(docker-java)"
"PlantUML Validator Port" --> "PlantUML Validator"
"Knowledge Graph Data Port" --> "Graphify JSON Adapter\n(KnowledgeGraphParser)"

"Gradle Task Adapter\n(ProcessPlantumlPromptsTask)" --> "DiagramProcessor"
"Gradle Task Adapter\n(ProcessPlantumlPromptsTask)" --> "LlmService"
"Gradle Task Adapter\n(ValidatePlantumlSyntaxTask)" --> "PlantumlService"
"Gradle Task Adapter\n(ReindexPlantumlRagTask)" --> "RagManager"
"Gradle Task Adapter\n(GenerateKnowledgeGraphDiagramTask)" --> "KnowledgeGraphParser"
"Gradle Task Adapter\n(GenerateKnowledgeGraphDiagramTask)" --> "Knowledge Graph Rendering\n(KnowledgeGraphRenderer)"
"Gradle Task Adapter\n(GenerateKnowledgeGraphDiagramTask)" --> "PlantumlService"
"Gradle Task Adapter\n(GenerateDiagramDocsTask)" --> "Graphify Prompt Adapter\n(GraphifyPromptAdapter)"
@enduml
----

This architecture delivers:

* independence from LLM providers (7 supported)
* swappable RAG store (pgvector today, any backend tomorrow)
* ability to replace PlantUML validator
* deterministic Knowledge Graph pipeline (no LLM required)
* high testability through dependency injection
* complete decoupling between Gradle and business logic

== Typical Workflows

.Process prompts with RAG
[source,bash]
----
# Step 1 — Reindex RAG (after adding prompts)
./gradlew reindexPlantumlRag --no-daemon

# Step 2 — Process prompts
./gradlew processPlantumlPrompts \
  -Pai.provider=ollama \
  --no-daemon

# Step 3 — Validate syntax
./gradlew validatePlantumlSyntax
----

.Build and view diagrams
[source,bash]
----
# View generated diagrams
ls -la generated/diagrams/
ls -la generated/images/
----

.Clean and rebuild
[source,bash]
----
./gradlew clean processPlantumlPrompts
----

== Roadmap

* Configuration Cache support — blocked on native ONNX library reload constraints.
* Cucumber tests for the full RAG pipeline.
* Support for additional PlantUML diagram types.
* Extended DSL configuration (custom output directories, includes/excludes).
* Real-time incremental processing when editing prompts.

NOTE: The plugin explicitly declares `configurationCache = false` on the Gradle Plugin Portal.
Do not enable the Gradle Configuration Cache with this plugin — RAG tasks run `OUT_OF_PROCESS`
with native ONNX libraries and are not compatible in their current state.

== License
This project is licensed under the Apache 2.0 License – see the `LICENSE` file.
