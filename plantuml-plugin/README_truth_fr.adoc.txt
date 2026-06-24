= Plugin PlantUML — Guide Développeur
:toc: left
:toclevels: 3
:source-highlighter: rouge
:icons: font
:lang: fr
:hardbreaks-option:
:plugin-version: 0.0.5

++++
<p align="right">
  <a href="README_truth.adoc">
    <img src="../.github/workflows/readmes/images/lang-en-blue.svg" alt="English" width="64"/>
  </a>
</p>
++++

image:https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin[Kotlin]
image:https://img.shields.io/badge/Gradle-9.x-02303A?logo=gradle[Gradle]
image:https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk[Java]
image:https://img.shields.io/badge/License-Apache%202.0-blue.svg[License]

== Description

`com.cheroliv.plantuml` est un plugin Gradle qui génère des diagrammes PlantUML via IA (LangChain4j) à partir de fichiers `.prompt`.
Il expose une DSL minimale au consommateur et gère en interne toute la configuration des dépôts, dépendances et tâches.
Il intègre un pipeline RAG (Retrieval-Augmented Generation) pour la génération automatisée de diagrammes PlantUML via plusieurs fournisseurs LLM.

== Version actuelle : {plugin-version}

== Prérequis

* JDK 25 (testé avec Eclipse Temurin 25.0.2), 23+ supporté
* Gradle 9.4.0+
* Docker (pour le conteneur pgvector utilisé par le pipeline RAG)

== Structure du Projet

[source]
----
plantuml-plugin/
├── plantuml-plugin/
│   ├── src/
│   │   ├── main/kotlin/plantuml/
│   │   │   ├── PlantumlPlugin.kt         # Point d'entrée du plugin — orchestrateur fin
│   │   │   ├── PlantumlManager.kt        # Toute la logique du plugin — objets imbriqués
│   │   │   └── models.kt                 # Modèles de données (PlantumlConfig, PlantumlDiagram, PlantumlCode…)
│   │   │   ├── service/
│   │   │   │   ├── PlantumlService.kt    # Validation syntaxe + génération images
│   │   │   │   ├── DiagramProcessor.kt   # Interaction LLM + logique retry
│   │   │   │   └── LlmService.kt         # Fournisseurs LLM (Ollama, OpenAI, Gemini…)
│   │   │   └── tasks/
│   │   │       ├── ProcessPlantumlPromptsTask.kt    # Traite les fichiers .prompt
│   │   │       ├── ValidatePlantumlSyntaxTask.kt    # Valide la syntaxe PlantUML
│   │   │       └── ReindexPlantumlRagTask.kt        # Réindexation RAG
│   │   ├── test/kotlin/                # Tests unitaires + Cucumber
│   │   └── functionalTest/kotlin/      # Tests fonctionnels GradleTestKit
│   └── build.gradle.kts                # Script de build du plugin
├── gradle/
│   ├── libs.versions.toml              # Catalogue de dépendances
│   └── wrapper/                        # Gradle Wrapper
├── settings.gradle.kts
├── README.adoc                          # Ce fichier
└── README_fr.adoc                       # Version française
----

=== Architecture Interne

[plantuml]
----
@startuml
skinparam componentStyle rectangle

package "plantuml" {
  component "PlantumlPlugin\n(orchestrateur)" as Plugin
  component "PlantumlManager\n(logique métier)" as Manager
  component "models.kt\n(PlantumlConfig, PlantumlDiagram…)" as Models
}

package "service" {
  component "PlantumlService\n(syntaxe + images)" as PS
  component "DiagramProcessor\n(LLL + retry)" as DP
  component "LlmService\n(fournisseurs)" as LS
  component "KnowledgeGraphParser\n(graph.json → KG)" as KGP
  component "KnowledgeGraphRenderer\n(KG → PlantUML)" as KGR
  component "GraphifyPromptAdapter\n(KG → .prompt)" as GPA
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
  component "AllMiniLmL6V2\n(ONNX en-process)" as Embed
  component "Fournisseurs LLM\n(Ollama/OpenAI/Gemini…)" as LLM
  collections "graphify-out/graph.json" as KGJSON
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
KGDT --> KGP
KGDT --> KGR
KGDT --> PS
GDDT --> GPA
KGP --> KGJSON
GPA --> KGJSON
@enduml
----

== Décisions Techniques Clés

=== ID du Plugin

L'ID du plugin est `com.cheroliv.plantuml`, appliqué via :

[source,kotlin]
----
plugins {
    id("com.cheroliv.plantuml") version "0.0.5"
}
----

=== Mode d'Exécution — `OUT_OF_PROCESS`

Les tâches RAG s'exécutent hors-processus pour isoler le chargement des bibliothèques ONNX natives (`libtokenizers.so`).
Cela prévient les `UnsatisfiedLinkError` lors des builds suivants.

=== RAG avec PostgreSQL + pgvector

Le pipeline RAG utilise PostgreSQL avec l'extension pgvector pour le stockage des embeddings.
Les embeddings sont générés avec `AllMiniLmL6V2` (ONNX en-process, dim=384).

=== PgVectorService — Gradle BuildService + testcontainers

Le conteneur pgvector est géré par testcontainers dans les tests, et par Docker en production.
Les tâches RAG déclarent la dépendance via `@ServiceReference` (Gradle 7.4+) — la seule annotation
qui garantit que Gradle garde le service actif pendant toute la durée de la tâche.

[source,kotlin]
----
// ReindexPlantumlRagTask.kt — @ServiceReference garantit le cycle de vie
abstract class ReindexPlantumlRagTask : DefaultTask() {
    @get:ServiceReference
    abstract val pgVectorService: Property<PgVectorService>

    protected fun service(): PgVectorService =
        pgVectorService.get().also { it.start() }
}
----

Le port hôte est assigné dynamiquement (binding `0:5432`) pour éviter les collisions avec tout PostgreSQL existant.
Le service est arrêté automatiquement par Gradle à la fin du build.

=== SSL pgvector — `sslmode=disable`

Le pilote JDBC PostgreSQL tente une négociation SSL par défaut, causant une `EOFException`
contre un conteneur Docker simple sans SSL configuré. Le store d'embeddings est créé
avec `sslmode=disable` pour prévenir cette erreur.

=== `--no-daemon` requis pour les tâches RAG

Le daemon Gradle réutilise le processus JVM entre les builds, empêchant le rechargement
des bibliothèques ONNX natives (`libtokenizers.so`) et causant un `UnsatisfiedLinkError`
lors du deuxième build. Toutes les tâches RAG doivent être exécutées avec `--no-daemon`,
ou le projet doit déclarer `org.gradle.daemon=false` dans `gradle.properties`.

=== Convention de nommage des fichiers de diagrammes

[cols="1,2,2"]
|===
| Fichier | Pattern | Exemple

| Fichier prompt
| `*.prompt`
| `architecture.prompt`

| PlantUML généré
| `*.puml`
| `architecture.puml`

| Image générée
| `*.png`
| `architecture.png`
|===

Le nom du fichier de sortie est dérivé du nom du fichier prompt (même nom de base).

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
            createChatTasks()  // enregistre toutes les tâches IA
        }
    }
}
----

`createChatTasks()` est défini dans `PlantumlManager` et enregistre :
- `PgVectorService` via `gradle.sharedServices.registerIfAbsent()`
- `processPlantumlPrompts`, `validatePlantumlSyntax`, `reindexPlantumlRag` (tâches typées)
- tâches de smoke-test pour les fournisseurs LLM

== Modèle de Données

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

== Build & Publication

=== Publication en Maven Local (pour tests locaux)

[source,bash]
----
./gradlew publishToMavenLocal
----

=== Exécuter les tests

[source,bash]
----
# Tests unitaires
./gradlew test

# Tests fonctionnels (GradleTestKit)
./gradlew functionalTest

# Tests Cucumber BDD
./gradlew cucumberTest

# Tous les tests
./gradlew check
----

== Tests Cucumber BDD

Le plugin inclut une suite de tests BDD complète utilisant Cucumber-JVM. Les scénarios sont dans `src/test/features/` avec les implémentations de steps dans `src/test/scenarios/`.

=== Tags de Test

[cols="1,3"]
|===
| Tag | Objectif

| `@wip`
| Scénarios en cours de développement — exclus des exécutions par défaut

| `@integration`
| Scénarios nécessitant un vrai LLM (Ollama) — exclus des exécutions par défaut
|===

=== Exécuter les Tests Cucumber

[source,bash]
----
# Lancer tous les tests Cucumber (exclut @wip et @integration)
./gradlew cucumberTest

# Lancer un scénario spécifique
./gradlew cucumberTest --tests "*ScenarioName*"

# Lancer uniquement les scénarios @wip
./gradlew cucumberTest -Pcucumber.filter.tags="@wip"

# Lancer les tests d'intégration avec un vrai LLM
./gradlew cucumberTest -Pcucumber.filter.tags="@integration"
----

=== Rapports de Test

Cucumber génère des rapports HTML et JSON :

* **HTML** : `build/reports/cucumber.html`
* **JSON** : `build/reports/cucumber.json`

Ouvrir `build/reports/cucumber.html` dans un navigateur pour voir les résultats détaillés.

=== Steps Disponibles

==== Initialisation

[source,gherkin]
----
Given a prompt file "test.prompt" with content "Generate a diagram"
Given a valid PlantUML file "test.puml" with content "@startuml\nactor User\n@enduml"
Given an invalid PlantUML file "test.puml" with content "@startumlnactor User\n@endulm"
----

==== Mock LLM

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

==== Exécution de Tâches

[source,gherkin]
----
When I run processPlantumlPrompts task
When I run processPlantumlPrompts task with max 5 iterations
When I run validatePlantumlSyntax task with file "test.puml"
----

==== Vérifications

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

==== Historique des Tentatives

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

=== Architecture des Tests

Les tests Cucumber utilisent un objet d'état partagé `PlantumlWorld` qui :

* Crée des projets Gradle temporaires isolés pour chaque scénario
* Gère des serveurs HTTP mock LLM
* Gère l'exécution asynchrone de Gradle via coroutines
* Nettoie automatiquement les fichiers temporaires après chaque scénario (via hook `@After`)

Les tests utilisent `includeBuild()` pour référencer le plugin en développement directement depuis le classpath, évitant ainsi d'avoir à faire un `publishToMavenLocal`.

=== Publication sur Gradle Plugin Portal

[source,bash]
----
./gradlew publishPlugins
----
Nécessite `gradle.publish.key` et `gradle.publish.secret` dans `~/.gradle/gradle.properties`.

== DSL du Plugin

[source,kotlin]
----
plantuml {
    // Chemin vers le fichier de configuration YAML (requis)
    configPath = file("plantuml-config.yml").absolutePath
}
----

== Tâches Enregistrées

[cols="1,1,2"]
|===
| Tâche | Groupe | Description

| `processPlantumlPrompts`
| plantuml
| Traite les fichiers `.prompt` et génère des diagrammes PlantUML via LLM

| `validatePlantumlSyntax`
| plantuml
| Valide la syntaxe PlantUML des fichiers `.puml` générés

| `reindexPlantumlRag`
| plantuml-rag
| Supprime et reconstruit entièrement l'index d'embeddings pgvector

| `generateKnowledgeGraphDiagram`
| plantuml
| Génère un diagramme de graphe de connaissance depuis `graphify-out/graph.json` (déterministe, sans LLM)

| `generateDiagramDocs`
| plantuml
| Génère la documentation des diagrammes (dogfooding)

| `help`
| help
| Affiche les informations d'aide du plugin

| `tasks`
| help
| Liste toutes les tâches disponibles
|===

== Intégration IA — Pipeline RAG

`PlantumlManager.createChatTasks()` est appelé dans `afterEvaluate` et enregistre toutes les tâches IA.
Le pipeline RAG repose sur trois composants :

* `PgVectorService` — `BuildService` Gradle gérant le cycle de vie Docker via docker-java
* `RagManager` — indexation incrémentale par SHA-256, recherche par similarité cosinus
* `LlmService` — résolution du modèle, construction du prompt, appel LLM

=== Cycle de Vie du Pipeline RAG

[plantuml]
----
@startuml
participant "Gradle" as G
participant "ReindexPlantumlRagTask" as RT
participant "PgVectorService\n(BuildService)" as PGS
participant "RagManager" as RM
participant "LlmService" as LS
participant "Fournisseur LLM" as LLM
collections "prompts/" as Files
collections "generated/rag/" as RAG

G -> RT : exécution tâche
RT -> PGS : start() — idempotent
activate PGS
PGS --> RT : port Docker assigné

RT -> RM : reindex()
RM -> PGS : port / identifiants
RM --> RT : chunks RAG

RT -> LS : resolveModel(provider)
LS --> RT : ChatModel

RT -> LLM : systemPrompt + userMessage + chunks RAG
LLM --> RT : réponse

RT -> RAG : stocker embeddings

G -> PGS : close() — fin du build
deactivate PGS
@enduml
----

=== Fournisseurs Supportés

[cols="1,1,1"]
|===
| Fournisseur | Module LangChain4j | Clé dans plantuml-config.yml

| Ollama (local)
| `langchain4j-ollama`
| — (aucune clé requise)

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
| `langchain4j-open-ai` (routeur)
| `apiKey.huggingface`

| Groq
| `langchain4j-open-ai` (routeur)
| `apiKey.groq`

| Claude (Anthropic)
| `langchain4j-anthropic`
| `apiKey.claude`
|===

=== Tâches RAG — utilisation

[source,bash]
----
# Reconstruire l'index (après ajout/suppression de prompts)
./gradlew reindexPlantumlRag --no-daemon

# Traiter les prompts
./gradlew processPlantumlPrompts \
  -Pplantuml.config=file("plantuml-config.yml").absolutePath \
  -Pai.provider=ollama \
  --no-daemon

# Valider la syntaxe
./gradlew validatePlantumlSyntax
----

Propriétés disponibles pour les tâches :

[cols="1,1,2"]
|===
| Propriété | Défaut | Description

| `-Pplantuml.config`
| `plantuml-config.yml`
| Chemin vers la configuration YAML

| `-Pai.provider`
| `ollama`
| Fournisseur LLM

| `-Pplantuml.output.diagrams`
| `generated/diagrams`
| Répertoire de sortie pour les fichiers `.puml`

| `-Pplantuml.output.images`
| `generated/images`
| Répertoire de sortie pour les fichiers `.png`

| `-Pplantuml.output.rag`
| `generated/rag`
| Répertoire de sortie pour les embeddings RAG
|===

=== Stratégie de Prompt

Le `DiagramProcessor` gère le cycle de vie des prompts :

* `systemPrompt` — Définit les règles de génération PlantUML
* `userMessage` — Contient le contenu du fichier `.prompt` + chunks RAG
* `correctionPrompt` — Utilisé quand la validation syntaxe échoue (boucle retry)

Règles clés appliquées dans `systemPrompt` :

* Syntaxe PlantUML valide uniquement
* Commence par `@startuml` et finit par `@enduml`
* Utilise le type de diagramme approprié (class, component, sequence, etc.)
* Pas de blocs de code markdown (PlantUML brut uniquement)

=== Modèles Disponibles par Fournisseur

[cols="1,2"]
|===
| Fournisseur | Modèles (catalogue)

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

Vérifie si le projet consommateur a les répertoires requis. Sinon, les crée :

* `prompts/` — Fichiers `.prompt` d'entrée
* `generated/diagrams/` — Fichiers `.puml` de sortie
* `generated/images/` — Fichiers `.png` de sortie
* `generated/rag/` — Embeddings RAG

Tous les répertoires sont créés si absents — le contenu existant n'est jamais écrasé.

=== `scaffoldConfigIfAbsent()`

Si `plantuml-config.yml` est absent, en génère un automatiquement en sérialisant une instance
par défaut de `PlantumlConfig` via `yamlMapper` — pas de chaînes YAML en dur.

== Dépendances

=== Dépendances runtime clés

[cols="1,1"]
|===
| Dépendance | Usage

| `dev.langchain4j:langchain4j`
| Abstraction LLM de base

| `dev.langchain4j:langchain4j-ollama`
| Intégration modèles locaux Ollama

| `dev.langchain4j:langchain4j-open-ai`
| Intégration OpenAI + HuggingFace + Groq

| `dev.langchain4j:langchain4j-google-ai-gemini`
| Intégration LLM Gemini

| `dev.langchain4j:langchain4j-mistral-ai`
| Intégration Mistral AI

| `dev.langchain4j:langchain4j-anthropic`
| Intégration Claude (Anthropic)

| `dev.langchain4j:langchain4j-pgvector`
| Store d'embeddings pgvector

| `dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2`
| Modèle d'embedding ONNX en-process (dim=384)

| `org.postgresql:postgresql`
| Pilote JDBC PostgreSQL

| `com.github.docker-java:docker-java-core`
| Gestion Docker pour PgVectorService

| `com.fasterxml.jackson:jackson-module-kotlin`
| Parsing configuration YAML/JSON

| `net.sourceforge.plantuml:plantuml`
| Validation syntaxe PlantUML + génération images
|===

== Exigences Consommateur

La configuration du consommateur est minimale — le plugin gère en interne toute la configuration des dépôts et dépendances.

=== settings.gradle.kts
[source,kotlin]
----
pluginManagement.repositories {
    mavenLocal()
    gradlePluginPortal()
}
rootProject.name = "ton-projet"
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

=== gradle.properties (recommandé)
[source,properties]
----
# Requis pour les tâches RAG — prévient UnsatisfiedLinkError sur libtokenizers.so
org.gradle.daemon=false
----

== Compatibilité Fonctionnalités Gradle

[source,kotlin]
----
gradlePlugin {
    plugins {
        create("plantuml") {
            compatibility {
                features {
                    // Les tâches RAG s'exécutent OUT_OF_PROCESS avec bibliothèques ONNX natives —
                    // pas compatibles avec le Configuration Cache.
                    configurationCache = false
                }
            }
        }
    }
}
----

Déclarer `configurationCache = false` est honnête et recommandé — cela informe clairement les utilisateurs
et n'a pas de conséquences négatives sur le classement du plugin sur le Portal au-delà du badge Configuration Cache.

== Détails d'Architecture

=== Vue C4 — Contexte du Plugin

[plantuml]
----
@startuml
actor Developpeur

rectangle "Environnement Développeur" {
  rectangle "Système de Build Gradle" {
    component "Plugin Gradle PlantUML"
  }
}

rectangle "Composants Internes PlantUML" {
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

rectangle "Pipeline RAG" {
  component "AllMiniLmL6V2\n(ONNX en-process)"
  component "pgvector\n(Docker)"
}

rectangle "Pipeline Knowledge Graph" {
  collections "graphify-out/graph.json" as KGJSON
}

rectangle "Systèmes Externes" {
  component "Fournisseurs LLM\n(Ollama / OpenAI / Gemini / Mistral / HF / Groq / Claude)"
  component "PlantUML\n(validation syntaxe + génération images)"
  component "Graphify\n(pip install graphify)"
}

Developpeur --> "Système de Build Gradle"
"Système de Build Gradle" --> "Plugin Gradle PlantUML"

"Plugin Gradle PlantUML" --> "PlantumlManager"
"PlantumlManager" --> "LlmService"
"PlantumlManager" --> "DiagramProcessor"
"PlantumlManager" --> "PlantumlService"
"PlantumlManager" --> "RagManager"
"PlantumlManager" --> "PgVectorService"
"PlantumlManager" --> "KnowledgeGraphParser"
"PlantumlManager" --> "KnowledgeGraphRenderer"
"PlantumlManager" --> "GraphifyPromptAdapter"

"RagManager" --> "AllMiniLmL6V2\n(ONNX en-process)"
"RagManager" --> "pgvector\n(Docker)"
"PgVectorService" --> "pgvector\n(Docker)"

"KnowledgeGraphParser" --> KGJSON
"KnowledgeGraphRenderer" --> "PlantumlService"
"GraphifyPromptAdapter" --> KGJSON

"Graphify\n(pip install graphify)" --> KGJSON

"LlmService" --> "Fournisseurs LLM\n(Ollama / OpenAI / Gemini / Mistral / HF / Groq / Claude)"
"PlantumlService" --> "PlantUML\n(validation syntaxe + génération images)"
@enduml
----

=== Architecture Hexagonale (Ports & Adapters)

Le plugin suit une architecture hexagonale qui sépare la logique métier,
les interfaces externes et les technologies d'infrastructure.
Cette conception garantit l'indépendance aux fournisseurs LLM et une testabilité élevée.

[plantuml]
----
@startuml
skinparam componentStyle rectangle

package "Domaine Cœur" {
  component "Logique de Génération Diagrammes"
  component "Récupération RAG"
  component "Rendu Knowledge Graph\n(KnowledgeGraphRenderer)"
  component "Contexte PlantUML\n(PlantumlDiagram)"
}

package "Couche Application" {
  component "LlmService"
  component "DiagramProcessor"
  component "PlantumlService"
  component "RagManager"
  component "KnowledgeGraphParser"
  component "GraphifyPromptAdapter"
}

package "Ports" {
  interface "Port LLM"
  interface "Port Store Embeddings"
  interface "Port Service Docker"
  interface "Port Validateur PlantUML"
  interface "Port Données Knowledge Graph"
}

package "Adapters" {
  component "Adaptateur Ollama"
  component "Adaptateur OpenAI"
  component "Adaptateur Gemini"
  component "Adaptateur Mistral"
  component "Adaptateur HuggingFace"
  component "Adaptateur Groq"
  component "Adaptateur Claude"
  component "PgVectorEmbeddingStore"
  component "PgVectorService\n(docker-java)"
  component "Validateur PlantUML"
  component "Adaptateur JSON Graphify\n(KnowledgeGraphParser)"
  component "Adaptateur Prompt Graphify\n(GraphifyPromptAdapter)"
  component "Adaptateur Tâche Gradle\n(ProcessPlantumlPromptsTask)"
}

"LlmService" --> "Port LLM"
"RagManager" --> "Port Store Embeddings"
"RagManager" --> "Port Service Docker"
"PlantumlService" --> "Port Validateur PlantUML"
"KnowledgeGraphParser" --> "Port Données Knowledge Graph"

"Port LLM" --> "Adaptateur Ollama"
"Port LLM" --> "Adaptateur OpenAI"
"Port LLM" --> "Adaptateur Gemini"
"Port LLM" --> "Adaptateur Mistral"
"Port LLM" --> "Adaptateur HuggingFace"
"Port LLM" --> "Adaptateur Groq"
"Port LLM" --> "Adaptateur Claude"

"Port Store Embeddings" --> "PgVectorEmbeddingStore"
"Port Service Docker" --> "PgVectorService\n(docker-java)"
"Port Validateur PlantUML" --> "Validateur PlantUML"
"Port Données Knowledge Graph" --> "Adaptateur JSON Graphify\n(KnowledgeGraphParser)"

"Adaptateur Tâche Gradle\n(ProcessPlantumlPromptsTask)" --> "DiagramProcessor"
"Adaptateur Tâche Gradle\n(ProcessPlantumlPromptsTask)" --> "LlmService"
"Adaptateur Tâche Gradle\n(ValidatePlantumlSyntaxTask)" --> "PlantumlService"
"Adaptateur Tâche Gradle\n(ReindexPlantumlRagTask)" --> "RagManager"
"Adaptateur Tâche Gradle\n(GenerateKnowledgeGraphDiagramTask)" --> "KnowledgeGraphParser"
"Adaptateur Tâche Gradle\n(GenerateKnowledgeGraphDiagramTask)" --> "Rendu Knowledge Graph\n(KnowledgeGraphRenderer)"
"Adaptateur Tâche Gradle\n(GenerateKnowledgeGraphDiagramTask)" --> "PlantumlService"
"Adaptateur Tâche Gradle\n(GenerateDiagramDocsTask)" --> "Adaptateur Prompt Graphify\n(GraphifyPromptAdapter)"
@enduml
----

Cette architecture délivre :

* indépendance des fournisseurs LLM (7 supportés)
* store RAG interchangeable (pgvector aujourd'hui, n'importe quel backend demain)
* capacité à remplacer le validateur PlantUML
* pipeline Knowledge Graph déterministe (aucun LLM requis)
* testabilité élevée grâce à l'injection de dépendances
* découplage complet entre Gradle et la logique métier

== Workflows Typiques

.Traiter les prompts avec RAG
[source,bash]
----
# Étape 1 — Réindexer RAG (après ajout de prompts)
./gradlew reindexPlantumlRag --no-daemon

# Étape 2 — Traiter les prompts
./gradlew processPlantumlPrompts \
  -Pai.provider=ollama \
  --no-daemon

# Étape 3 — Valider la syntaxe
./gradlew validatePlantumlSyntax
----

.Build et visualisation des diagrammes
[source,bash]
----
# Voir les diagrammes générés
ls -la generated/diagrams/
ls -la generated/images/
----

.Nettoyage et reconstruction
[source,bash]
----
./gradlew clean processPlantumlPrompts
----

== Roadmap

* Support du Configuration Cache — bloqué sur les contraintes de rechargement des bibliothèques ONNX natives.
* Tests Cucumber pour le pipeline RAG complet.
* Support de types de diagrammes PlantUML additionnels.
* Configuration DSL étendue (répertoires de sortie personnalisés, includes/excludes).
* Traitement incrémental en temps réel lors de l'édition des prompts.

NOTE: Le plugin déclare explicitement `configurationCache = false` sur le Gradle Plugin Portal.
Ne pas activer le Configuration Cache Gradle avec ce plugin — les tâches RAG s'exécutent `OUT_OF_PROCESS`
avec des bibliothèques ONNX natives et ne sont pas compatibles dans leur état actuel.

== Licence
Ce projet est licencié sous la Licence Apache 2.0 – voir le fichier `LICENSE`.
