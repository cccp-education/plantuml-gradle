= PlantUML Gradle Plugin — Developer Guide
:toc: left
:toclevels: 3
:source-highlighter: rouge
:icons: font
:lang: en
:hardbreaks-option:
:plugin-version: 0.0.2

++++
<p align="right">
  <a href="README_truth_fr.adoc">
    <img src=".github/workflows/readmes/images/lang-fr-red.svg" alt="Français" width="64"/>
  </a>
</p>
++++

image:https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin[Kotlin]
image:https://img.shields.io/badge/Gradle-9.x-02303A?logo=gradle[Gradle]
image:https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk[Java]
image:https://img.shields.io/badge/License-Apache%202.0-blue.svg[License]

== Description

`education.cccp.plantuml` is a Gradle plugin that encapsulates the full lifecycle of *PlantUML* diagram generation via LLMs.
It exposes a minimal DSL to the consumer and handles all repository, dependency, and task configuration internally.
It integrates a RAG (Retrieval-Augmented Generation) pipeline for automated PlantUML diagram generation via multiple LLM providers.
Supports 10 languages (EN, ZH, HI, ES, FR, AR, BN, PT, RU, UR) with intelligent translation boundary (presentation labels translated, semantic identity preserved, domain lexicon handled idiomatically).

== Current version: {plugin-version}

== Prerequisites

* JDK 25 (tested with Eclipse Temurin 25.0.2), 23+ supported
* Gradle 9.4.0+
* Docker (for the pgvector container used by the RAG pipeline)
* Node.js / npx (optional - for viewing generated diagrams)

== Project Structure

[source]
----
plantuml-gradle/
├── plantuml-plugin/
│   ├── src/
│   │   ├── main/kotlin/plantuml/
│   │   │   ├── PlantumlPlugin.kt         # Plugin entry point — thin orchestrator
│   │   │   ├── PlantumlExtension.kt      # Plugin extension for configuration
│   │   │   ├── PlantumlConfig.kt         # Configuration data classes
│   │   │   ├── models.kt                 # Data models (PromptContext, DiagramConfiguration, Git…)
│   │   │   ├── kgmodels.kt               # Knowledge Graph data models
│   │   │   ├── PlantumlManager.kt        # All plugin logic — nested objects
│   │   │   ├── PlantumlMessages.kt       # i18n message bundle (ResourceBundle + MessageFormat)
│   │   │   ├── ConfigLoader.kt           # YAML configuration loader
│   │   │   ├── ConfigMerger.kt           # 4-layer config merge (CLI > env > YAML > gradle.properties)
│   │   │   ├── apikey/                   # API key pool with rotation, tiers, cross-provider fallback
│   │   │   ├── boundary/                 # Translation boundary domain (3 natures, 3 strategies)
│   │   │   ├── incremental/              # Incremental processing (checksum-based skip, audit log)
│   │   │   ├── service/                  # Core services (LLM, PlantUML, RAG, KG, Graphify)
│   │   │   └── tasks/                    # Gradle tasks
│   │   │       ├── GeneratePlantumlDiagramsTask.kt  # Main task for processing prompts
│   │   │       ├── ValidatePlantumlSyntaxTask.kt   # Syntax validation task
│   │   │       ├── CollectPlantumlIndexTask.kt     # RAG reindexing task
│   │   │       ├── GenerateKnowledgeGraphDiagramTask.kt  # KG diagram (deterministic, no LLM)
│   │   │       └── GenerateDiagramDocsTask.kt      # Dogfooding documentation diagrams
│   │   ├── main/resources/i18n/          # 10 language message bundles
│   │   ├── test/kotlin/                  # Unit + Cucumber tests (2537 unit, 128 Cucumber)
│   │   └── functionalTest/kotlin/        # GradleTestKit functional tests
│   └── build.gradle.kts                  # Plugin build script
├── gradle/
│   ├── libs.versions.toml                # Dependency catalogue
│   └── wrapper/                          # Gradle Wrapper
├── settings.gradle.kts
├── README_truth.adoc                      # This file
└── README_truth_fr.adoc                   # French version
----

=== Internal Architecture

[plantuml, target=internal-architecture, format=svg]
----
@startuml
skinparam packageStyle rectangle

package "PlantUML Plugin" {
  [PlantumlPlugin] as plugin
  [PlantumlManager] as manager

  package "Tasks" {
    [GeneratePlantumlDiagramsTask] as processTask
    [ValidatePlantumlSyntaxTask] as validateTask
    [CollectPlantumlIndexTask] as reindexTask
    [GenerateKnowledgeGraphDiagramTask] as kgTask
    [GenerateDiagramDocsTask] as docsTask
  }

  package "Services" {
    [PlantumlService] as service
    [DiagramProcessor] as processor
    [LlmService] as llm
    [RagManager] as rag
    [KnowledgeGraphParser] as kgParser
    [KnowledgeGraphRenderer] as kgRenderer
    [GraphifyPromptAdapter] as gpa
  }

  package "API Key Pool" {
    [ApiKeyPool] as pool
    [TieredRotationStrategy] as tiered
    [CrossProviderFallbackOrchestrator] as cross
    [FreemiumWeightCalculator] as freemium
  }

  package "Translation Boundary" {
    [TranslationResolver] as resolver
    [TextClassifier] as classifier
    [IdiomaticGlossary] as glossary
    [NonTranslatableTermRegistry] as registry
  }

  package "Incremental Processing" {
    [IncrementalProcessor] as inc
    [ChecksumStore] as checksum
    [IncrementalAuditLogger] as audit
  }

  plugin --> manager : delegates logic
  manager --> processTask : registers
  manager --> validateTask : registers
  manager --> reindexTask : registers
  manager --> kgTask : registers
  manager --> docsTask : registers
  processTask --> processor : uses
  processTask --> inc : skip unchanged
  processor --> llm : uses
  processor --> service : uses
  processor --> resolver : i18n structural strings
  validateTask --> service : uses
  reindexTask --> rag : uses
  reindexTask --> inc : delta reindex
  kgTask --> kgParser : uses
  kgTask --> kgRenderer : uses
  kgTask --> service : uses
  kgRenderer --> resolver : i18n labels
  docsTask --> gpa : uses
  llm --> pool : uses
  pool --> tiered : uses
  pool --> cross : uses
  pool --> freemium : uses
}

collections "graphify-out/graph.json" as kgJson
kgParser --> kgJson : reads
gpa --> kgJson : reads

note top of plugin
  Main plugin class that applies
  the extension and registers tasks
end note

note right of kgTask
  Deterministic pipeline —
  no LLM required
end note

note right of inc
  SHA-256 checksum-based
  skip + cleanup + audit
end note

@enduml
----

== Key Technical Decisions

=== Configuration Pattern — YAML file
Instead of a complex Gradle DSL, the plugin uses a simple YAML configuration file (`plantuml-context.yml`) for all settings.
This keeps the consumer configuration minimal and easy to understand.

=== Multiple LLM Providers Support
The plugin supports multiple LLM providers:
- Ollama (local)
- OpenAI
- Gemini
- Mistral AI
- Claude
- HuggingFace (via OpenAI-compatible router)
- Groq

This is managed through LangChain4j integration.

=== RAG Pipeline Implementation
The plugin integrates a RAG (Retrieval-Augmented Generation) pipeline for improving diagram quality:
- Stores valid diagrams in a vector database (pgvector)
- Retrieves similar diagrams to guide new generations
- Improves consistency and quality over time

=== PlantUML Processing Loop
The plugin implements a feedback loop for diagram generation:
- Generate initial diagram with LLM
- Validate PlantUML syntax
- Iterate with corrections if needed (up to maxIterations)
- Save valid diagram for RAG indexing

=== Docker Integration for pgvector
The pgvector container is managed via Docker integration for the RAG pipeline:
- Dynamically assigned host port to avoid conflicts
- Automatic startup/shutdown with Gradle build lifecycle
- SSL disabled for simplicity with container

== PlantumlPlugin.apply() — Orchestration

[source,kotlin]
----
override fun apply(project: Project) {
    with(project) {
        // Configure plugin extension
        extensions.create<PlantumlExtension>("plantuml")
        
        // Register plugin tasks
        registerTasks()
        
        // Apply configuration after evaluation
        afterEvaluate {
            configurePlugin()
        }
    }
}
----

`configurePlugin()` validates the configuration and sets up required services.

== Data Model

=== PlantumlConfig

[source,kotlin]
----
data class PlantumlConfig(
    val input: InputConfig = InputConfig(),
    val output: OutputConfig = OutputConfig(),
    val langchain: LangchainConfig = LangchainConfig(),
    val git: GitConfig = GitConfig(),
    val rag: RagConfig = RagConfig()
)

data class InputConfig(
    val prompts: String = "prompts",
    val defaultLang: String = "en"
)

data class OutputConfig(
    val diagrams: String = "generated/diagrams",
    val images: String = "generated/images",
    val validations: String = "generated/validations",
    val rag: String = "generated/rag",
    val format: String = "png",
    val theme: String = "default"
)
----

=== PromptContext

[source,kotlin]
----
data class PromptContext(
    val promptFile: String,
    val subject: String,
    val language: String = "en",
    val diagramType: String = "uml",
    val author: AuthorContext,
    val validationRules: ValidationRules = ValidationRules()
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
./gradlew check
----

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
    configPath = "plantuml-context.yml"
}
----

== Registered Tasks

[cols="1,1,2"]
|===
| Task | Group | Description

| `generatePlantumlDiagrams`
| plantuml
| Processes PlantUML prompts and generates diagrams (incremental: skip unchanged via SHA-256 checksum)

| `validatePlantumlSyntax`
| plantuml
| Validates PlantUML syntax for debugging

| `collectPlantumlIndex`
| plantuml
| Rebuilds the RAG index with collected PlantUML diagrams (delta reindex via incremental processor)

| `generateKnowledgeGraphDiagram`
| plantuml
| Generates a knowledge graph diagram from `graphify-out/graph.json` (deterministic, no LLM)

| `generateDiagramDocs`
| plantuml
| Generates diagram documentation (dogfooding)

|===

== Knowledge Graph Diagram — Graphify Integration

The plugin integrates with https://github.com/nicholasgasior/graphify[Graphify] to generate knowledge graph diagrams from your codebase.
This is a **deterministic** task — no LLM is required.

=== Prerequisites

Install Graphify and generate the knowledge graph:

[source,bash]
----
# Install graphify
pip install graphify

# Run graphify on your project (at root)
graphify . --no-viz
----

This produces `graphify-out/graph.json` at the project root.

=== Usage

[source,bash]
----
# Generate the full knowledge graph diagram (all communities)
./gradlew generateKnowledgeGraphDiagram

# Filter by community
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.community=community_0

# Generate with limited nodes for readability
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.maxNodes=15

# Combine filters
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.community=community_0 -Pplantuml.kg.maxNodes=15

# Filter by edge type only
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.edgeTypes=EXTRACTED

# Filter by node type
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.nodeTypes=code

# Custom output directory
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.outputDir=docs/knowledge-graph
----

=== Properties

[cols="1,1,2"]
|===
| Property | Default | Description

| `plantuml.kg.community`
| _(none — all communities)_
| Filter communities by name (substring match)

| `plantuml.kg.edgeTypes`
| _(all)_
| Comma-separated edge types: `EXTRACTED`, `INFERRED`, `AMBIGUOUS`

| `plantuml.kg.minConfidence`
| `0.0`
| Minimum confidence threshold for edges

| `plantuml.kg.maxNodes`
| _(unlimited)_
| Maximum number of nodes to render

| `plantuml.kg.nodeTypes`
| _(all)_
| Comma-separated node types to include (e.g. `class`, `code`)

| `plantuml.kg.outputDir`
| `diagrams/knowledge-graph`
| Output directory for generated `.puml` and `.png` files
|===

=== Output

The task generates:

* `knowledge-graph-full.puml` + `knowledge-graph-full.png` (no community filter)
* `knowledge-graph-{filter}.puml` + `knowledge-graph-{filter}.png` (with community filter)

== AI Integration — LLM Pipeline

The plugin uses LangChain4j for LLM integration with a processing pipeline:

* `DiagramProcessor` — Handles LLM interaction and diagram iteration
* `PlantumlService` — Validates syntax and generates images
* `PlantumlManager` — Orchestrates the overall process

=== LLM Pipeline Lifecycle

[plantuml, target=pipeline-lifecycle, format=svg]
....
@startuml
start
:Read prompt file;
:Generate initial diagram\nwith LLM;
repeat
  :Validate PlantUML syntax;
  if (Is valid?) then (yes)
    :Save diagram;
    :Index for RAG;
    stop
  else (no)
    :Analyze errors;
    :Request correction\nfrom LLM;
  endif
repeat while (Attempts < maxIterations)
:Save best attempt\nwith error annotations;
stop
....

=== Supported providers

[cols="1,1,1"]
|===
| Provider | LangChain4j module | Config key

| Ollama (local)
| `langchain4j-ollama`
| `langchain.ollama`

| OpenAI
| `langchain4j-open-ai`
| `langchain.openai`

| Google Gemini
| `langchain4j-google-ai-gemini`
| `langchain.gemini`

| Mistral AI
| `langchain4j-mistral-ai`
| `langchain.mistral`

| Claude
| `langchain4j-anthropic`
| `langchain.claude`

| HuggingFace
| `langchain4j-hugging-face`
| `langchain.huggingface`

| Groq
| `langchain4j-groq`
| `langchain.groq`
|===

=== Prompt strategy

The plugin uses a focused prompt strategy for PlantUML generation:

* Clear instructions to generate valid PlantUML syntax
* Emphasis on correctness over completeness
* Iterative improvement through syntax validation
* Context preservation in iteration prompts

== Dependencies

=== Key runtime dependencies

[cols="1,1"]
|===
| Dependency | Purpose

| `net.sourceforge.plantuml:plantuml`
| PlantUML diagram processing

| `dev.langchain4j:langchain4j-core`
| Core LangChain4j functionality

| `dev.langchain4j:langchain4j-ollama`
| Ollama integration

| `dev.langchain4j:langchain4j-open-ai`
| OpenAI/Groq/HuggingFace integration

| `dev.langchain4j:langchain4j-google-ai-gemini`
| Gemini integration

| `dev.langchain4j:langchain4j-mistral-ai`
| Mistral AI integration

| `dev.langchain4j:langchain4j-anthropic`
| Claude integration

| `dev.langchain4j:langchain4j-pgvector`
| pgvector embedding store

| `dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2`
| In-process ONNX embedding model

| `org.eclipse.jgit`
| Git operations for diagram publishing

| `com.fasterxml.jackson` (yaml + kotlin)
| YAML configuration parsing

| `io.arrow-kt:arrow-core`
| Functional programming utilities

| `com.github.docker-java:docker-java-core`
| Docker management for PgVectorService

| `com.github.docker-java:docker-java-transport-httpclient5`
| HTTP transport for docker-java

| `org.postgresql:postgresql`
| PostgreSQL JDBC driver

| `org.jetbrains.kotlinx:kotlinx-coroutines-core`
| Async operations
|===

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

rectangle "Plugin Internal Components" {
  component "PlantumlManager"
  component "LlmService"
  component " DiagramProcessor"
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
plugins { 
    id("education.cccp.plantuml") version "0.0.2"
}

plantuml {
    configPath = "plantuml-context.yml"
}
----

=== plantuml-context.yml
[source,yaml]
----
input:
  prompts: "prompts"
  defaultLang: "en"

output:
  diagrams: "generated/diagrams"
  images: "generated/images"
  validations: "generated/validations"
  rag: "generated/rag"
  format: "png"
  theme: "default"

langchain:
  maxIterations: 5
  model: "ollama"
  validation: true
  
  ollama:
    baseUrl: "http://localhost:11434"
    modelName: "smollm:135m"
    
  # Other provider configurations...

git:
  userName: "github-actions[bot]"
  userEmail: "github-actions[bot]@users.noreply.github.com"
  # ... other git config

rag:
  databaseUrl: "jdbc:postgresql://localhost:5432/plantuml_rag"
  username: "plantuml_user"
  password: "plantumm_password"
  tableName: "plantuml_embeddings"
----

== Gradle Feature Compatibility

[source,kotlin]
----
gradlePlugin {
    plugins {
        create("plantuml") {
            compatibility {
                features {
                    // Some features may not be compatible with Configuration Cache
                    configurationCache = false
                }
            }
        }
    }
}
----

Declaring `configurationCache = false` ensures compatibility and honest reporting of limitations.

== Roadmap
* Enhanced diagram validation and error reporting
* Additional LLM providers support
* Configuration Cache support
* Web UI for viewing and managing generated diagrams
* Advanced RAG features for better diagram suggestions

== Internationalization (i18n) — 10 Languages

The plugin supports 10 languages via `PlantumlMessages` (ResourceBundle + MessageFormat).
Language is resolved through the 4-layer ConfigMerger cascade: CLI > env vars > YAML > gradle.properties.

[cols="1,2"]
|===
| Language | Code
| English | `en`
| Chinese Mandarin | `zh`
| Hindi | `hi`
| Spanish | `es`
| French | `fr`
| Arabic Standard | `ar`
| Bengali | `bn`
| Portuguese | `pt`
| Russian | `ru`
| Urdu | `ur`
|===

[source,bash]
----
# Override language via CLI
./gradlew generatePlantumlDiagrams -Pplantuml.language=fr

# Override via environment variable
export PLANTUML_LANGUAGE=zh
./gradlew generatePlantumlDiagrams
----

=== Translation Boundary

The plugin distinguishes 3 text natures with 3 strategies:

[cols="1,3,2"]
|===
| Nature | Examples | Strategy
| Presentation | `"Classes"`, `"Files"`, `"Empty Knowledge Graph"` | TRANSLATE (via Messages_*.properties)
| Semantic Identity | `LlmService`, `community_0`, formulas | PRESERVE (as-is)
| Domain Lexicon | `pipeline`, `rollback`, `dependency injection` | TRANSLATE or BORROW (idiomatic glossary)
|===

Configurable via YAML: `idiomatic-glossary.yml` and `non-translatable-terms.yml`.

== Incremental Processing

The plugin avoids redundant LLM calls by tracking SHA-256 checksums of prompts.
Only the delta is processed — never the entire set.

[source,bash]
----
# Force reprocess all prompts (bypass checksum)
./gradlew generatePlantumlDiagrams --rerun-tasks
----

Components:
* `ChecksumStore` — persists SHA-256 checksums
* `IncrementalProcessor` — compare, skip, cleanup, emit domain events
* `IncrementalConfig` — YAML: `checksumsDir`, `auditLog`, `auditEnabled`
* `IncrementalAuditLogger` — timestamped decision trace
* Domain events — `PromptSkipped`, `PromptProcessed`, `OutputsCleaned`

RAG reindex is also incremental: `collectPlantumlIndex` only reindexes diagrams whose prompt changed.

== Multi-Provider API Key Pool

The plugin manages API keys with tiered rotation and cross-provider fallback:

* **KeyTier** — ENTERPRISE (3), PRO (2), FREE (1)
* **TieredRotationStrategy** — Enterprise > Pro > Free, intra-tier by weight
* **CrossProviderFallbackOrchestrator** — Gemini Pro → Mistral Free → Ollama
* **FreemiumWeightCalculator** — dynamic weights based on real quota ratios
* **QuotaTracker** + **QuotaResetManager** — per-key quota tracking with auto-reset
* **QuotaAuditLogger** — rotation audit trail

7 providers supported: Ollama, OpenAI, Gemini, Mistral AI, Claude, HuggingFace, Groq.

== License
This project is licensed under the Apache 2.0 License – see the `LICENSE` file.