= Plugin PlantUML Gradle — Guide Développeur
:toc: left
:toclevels: 3
:source-highlighter: rouge
:icons: font
:lang: fr
:hardbreaks-option:
:plugin-version: 0.0.0

++++
<p align="right">
  <a href="README_truth.adoc">
    <img src=".github/workflows/readmes/images/lang-en-blue.svg" alt="English" width="64"/>
  </a>
</p>
++++

image:https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin[Kotlin]
image:https://img.shields.io/badge/Gradle-9.x-02303A?logo=gradle[Gradle]
image:https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk[Java]
image:https://img.shields.io/badge/Licence-Apache%202.0-blue.svg[Licence]

== Description

`com.cheroliv.plantuml` est un plugin Gradle qui encapsule l'intégralité du cycle de vie de génération de diagrammes *PlantUML* via des LLM.
Il expose un DSL minimal au consommateur et gère en interne toute la configuration des dépôts, dépendances et tâches.
Il intègre un pipeline RAG (Retrieval-Augmented Generation) pour la génération automatisée de diagrammes PlantUML via plusieurs fournisseurs LLM.

== Version actuelle : {plugin-version}

== Prérequis

* JDK 25 (testé avec Eclipse Temurin 25.0.2), support 23+
* Gradle 9.4.0+
* Docker (pour le container pgvector utilisé par le pipeline RAG)
* Node.js / npx (optionnel - pour visualiser les diagrammes générés)

== Structure du projet

[source]
----
plantuml-gradle/
├── plantuml-plugin/
│   ├── src/
│   │   ├── main/kotlin/plantuml/
│   │   │   ├── PlantumlPlugin.kt         # Point d'entrée du plugin — orchestrateur léger
│   │   │   ├── PlantumlExtension.kt      # Extension du plugin pour la configuration
│   │   │   ├── PlantumlConfig.kt         # Classes de données de configuration
│   │   │   ├── models.kt                 # Modèles de données (PromptContext, DiagramConfiguration, Git…)
│   │   │   ├── PlantumlManager.kt        # Toute la logique du plugin — objets imbriqués
│   │   │   └── tasks/
│   │   │       ├── ProcessPlantumlPromptsTask.kt  # Tâche principale pour le traitement des prompts
│   │   │       ├── ValidatePlantumlSyntaxTask.kt   # Tâche de validation de syntaxe
│   │   │       └── ReindexPlantumlRagTask.kt       # Tâche de réindexation RAG
│   │   ├── main/kotlin/plantuml/service/
│   │   │   ├── PlantumlService.kt        # Service de traitement et validation PlantUML
│   │   │   └── DiagramProcessor.kt       # Processeur central pour l'interaction LLM et raffinement des diagrammes
│   │   ├── test/kotlin/                  # Tests unitaires + Cucumber
│   │   └── functionalTest/kotlin/        # Tests fonctionnels GradleTestKit
│   └── build.gradle.kts                  # Script de build du plugin
├── gradle/
│   ├── libs.versions.toml                # Catalogue de dépendances
│   └── wrapper/                          # Gradle Wrapper
├── settings.gradle.kts
├── README_truth_fr.adoc                  # Ce fichier
└── README_truth.adoc                     # Version anglaise
----

=== Architecture interne

[plantuml, target=internal-architecture, format=svg]
----
@startuml
skinparam packageStyle rectangle

package "Plugin PlantUML" {
  [PlantumlPlugin] as plugin
  [PlantumlManager] as manager

  package "Tâches" {
    [ProcessPlantumlPromptsTask] as processTask
    [ValidatePlantumlSyntaxTask] as validateTask
    [ReindexPlantumlRagTask] as reindexTask
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

  plugin --> manager : délègue la logique
  manager --> processTask : enregistre
  manager --> validateTask : enregistre
  manager --> reindexTask : enregistre
  manager --> kgTask : enregistre
  manager --> docsTask : enregistre
  processTask --> processor : utilise
  processor --> llm : utilise
  processor --> service : utilise
  validateTask --> service : utilise
  reindexTask --> rag : utilise
  kgTask --> kgParser : utilise
  kgTask --> kgRenderer : utilise
  kgTask --> service : utilise
  docsTask --> gpa : utilise
}

collections "graphify-out/graph.json" as kgJson
kgParser --> kgJson : lit
gpa --> kgJson : lit

note top of plugin
  Classe principale du plugin qui applique
  l'extension et enregistre les tâches
end note

note right of kgTask
  Pipeline déterministe —
  aucun LLM requis
end note

@enduml
----
@startuml
skinparam packageStyle rectangle

package "Plugin PlantUML" {
  [PlantumlPlugin] as plugin
  [PlantumlExtension] as extension
  [PlantumlManager] as manager

  package "Tâches" {
    [ProcessPlantumlPromptsTask] as processTask
    [ValidatePlantumlSyntaxTask] as validateTask
    [ReindexPlantumlRagTask] as reindexTask
  }

  package "Services" {
    [PlantumlService] as service
    [DiagramProcessor] as processor
  }

  plugin --> extension : configure
  plugin --> manager : délègue la logique
  manager --> processTask : enregistre
  manager --> validateTask : enregistre
  manager --> reindexTask : enregistre
  processTask --> processor : utilise
  validateTask --> service : utilise
  reindexTask --> service : utilise
  processor --> service : utilise
}

note top of plugin
  Classe principale du plugin qui applique
  l’extension et enregistre les tâches
end note

note bottom of processor
  Logique centrale de traitement pour
  l’interaction avec le LLM et l’amélioration des diagrammes
end note

@enduml
----

== Décisions techniques clés

=== Modèle de configuration — Fichier YAML
Au lieu d'un DSL Gradle complexe, le plugin utilise un fichier de configuration YAML simple (`plantuml-context.yml`) pour tous les paramètres.
Cela permet de garder la configuration du consommateur minimale et facile à comprendre.

=== Support de plusieurs fournisseurs LLM
Le plugin supporte plusieurs fournisseurs LLM :
- Ollama (local)
- OpenAI
- Gemini
- Mistral AI
- Claude
- HuggingFace (via routeur compatible OpenAI)
- Groq

Cette gestion se fait via l'intégration LangChain4j.

=== Implémentation du pipeline RAG
Le plugin intègre un pipeline RAG (Retrieval-Augmented Generation) pour améliorer la qualité des diagrammes :
- Stocke les diagrammes valides dans une base de données vectorielle (pgvector)
- Récupère des diagrammes similaires pour guider les nouvelles générations
- Améliore la cohérence et la qualité au fil du temps

=== Boucle de traitement PlantUML
Le plugin implémente une boucle de feedback pour la génération de diagrammes :
- Génère le diagramme initial avec l'LLM
- Valide la syntaxe PlantUML
- Itère avec des corrections si nécessaire (jusqu'à maxIterations)
- Sauvegarde le diagramme valide pour l'indexation RAG

=== Intégration Docker pour pgvector
Le container pgvector est géré via l'intégration Docker pour le pipeline RAG :
- Port hôte attribué dynamiquement pour éviter les conflits
- Démarrage/arrêt automatique avec le cycle de vie du build Gradle
- SSL désactivé pour simplifier avec le container

== PlantumlPlugin.apply() — Orchestration

[source,kotlin]
----
override fun apply(project: Project) {
    with(project) {
        // Configure l'extension du plugin
        extensions.create<PlantumlExtension>("plantuml")
        
        // Enregistre les tâches du plugin
        registerTasks()
        
        // Applique la configuration après évaluation
        afterEvaluate {
            configurePlugin()
        }
    }
}
----

`configurePlugin()` valide la configuration et configure les services requis.

== Modèle de données

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

== Build & Publication

=== Publier en local (pour tests locaux)

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

# Tests BDD Cucumber
./gradlew check
----

=== Publier sur le Gradle Plugin Portal

[source,bash]
----
./gradlew publishPlugins
----
Nécessite `gradle.publish.key` et `gradle.publish.secret` dans `~/.gradle/gradle.properties`.

== DSL du plugin

[source,kotlin]
----
plantuml {
    configPath = "plantuml-context.yml"
}
----

== Tâches enregistrées

[cols="1,1,2"]
|===
| Tâche | Groupe | Description

| `processPlantumlPrompts`
| plantuml
| Traite les prompts PlantUML et génère les diagrammes

| `validatePlantumlSyntax`
| plantuml
| Valide la syntaxe PlantUML à des fins de débogage

| `reindexPlantumlRag`
| plantuml
| Reconstruit l'index RAG avec les diagrammes PlantUML collectés

| `generateKnowledgeGraphDiagram`
| plantuml
| Génère un diagramme de graphe de connaissance à partir de `graphify-out/graph.json` (déterministe, sans LLM)

| `generateDiagramDocs`
| plantuml
| Génère la documentation des diagrammes (dogfooding)

|===

== Diagramme de Graphe de Connaissance — Intégration Graphify

Le plugin s'intègre avec https://github.com/nicholasgasior/graphify[Graphify] pour générer des diagrammes de graphe de connaissance à partir de votre codebase.
C'est une tâche **déterministe** — aucun LLM requis.

=== Prérequis

Installer Graphify et générer le graphe de connaissance :

[source,bash]
----
# Installer graphify
pip install graphify

# Exécuter graphify sur votre projet (à la racine)
graphify . --no-viz
----

Cela produit `graphify-out/graph.json` à la racine du projet.

=== Utilisation

[source,bash]
----
# Générer le diagramme complet (toutes les communautés)
./gradlew generateKnowledgeGraphDiagram

# Filtrer par communauté
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.community=community_0

# Limiter le nombre de nœuds pour la lisibilité
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.maxNodes=15

# Combiner les filtres
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.community=community_0 -Pplantuml.kg.maxNodes=15

# Filtrer par type d'arête uniquement
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.edgeTypes=EXTRACTED

# Filtrer par type de nœud
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.nodeTypes=code

# Répertoire de sortie personnalisé
./gradlew generateKnowledgeGraphDiagram -Pplantuml.kg.outputDir=docs/knowledge-graph
----

=== Propriétés

[cols="1,1,2"]
|===
| Propriété | Défaut | Description

| `plantuml.kg.community`
| _(aucune — toutes les communautés)_
| Filtrer les communautés par nom (correspondance sous-chaîne)

| `plantuml.kg.edgeTypes`
| _(toutes)_
| Types d'arêtes séparés par virgules : `EXTRACTED`, `INFERRED`, `AMBIGUOUS`

| `plantuml.kg.minConfidence`
| `0.0`
| Seuil minimum de confiance pour les arêtes

| `plantuml.kg.maxNodes`
| _(illimité)_
| Nombre maximum de nœuds à afficher

| `plantuml.kg.nodeTypes`
| _(tous)_
| Types de nœuds séparés par virgules (ex. `class`, `code`)

| `plantuml.kg.outputDir`
| `diagrams/knowledge-graph`
| Répertoire de sortie pour les fichiers `.puml` et `.png`
|===

=== Sortie

La tâche génère :

* `knowledge-graph-full.puml` + `knowledge-graph-full.png` (sans filtre de communauté)
* `knowledge-graph-{filtre}.puml` + `knowledge-graph-{filtre}.png` (avec filtre de communauté)

== Intégration IA — Pipeline LLM

Le plugin utilise LangChain4j pour l'intégration LLM avec un pipeline de traitement :

* `DiagramProcessor` — Gère l'interaction LLM et l'itération des diagrammes
* `PlantumlService` — Valide la syntaxe et génère les images
* `PlantumlManager` — Orchestre l'ensemble du processus

=== Cycle de vie du pipeline LLM

[plantuml, target=pipeline-lifecycle, format=svg]
....
@startuml
start
:Lire le fichier prompt;
:Générer le diagramme initial\navec l'LLM;
repeat
  :Valider la syntaxe PlantUML;
  if (Est valide ?) then (oui)
    :Sauvegarder le diagramme;
    :Indexer pour RAG;
    stop
  else (non)
    :Analyser les erreurs;
    :Demander correction\nà l'LLM;
  endif
repeat while (Essais < maxIterations)
:Sauvegarder la meilleure tentative\navec annotations d'erreurs;
stop
....

=== Fournisseurs supportés

[cols="1,1,1"]
|===
| Fournisseur | Module LangChain4j | Clé de config

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

=== Stratégie de prompt

Le plugin utilise une stratégie de prompt ciblée pour la génération PlantUML :

* Instructions claires pour générer une syntaxe PlantUML valide
* Accent sur l'exactitude plutôt que l'exhaustivité
* Amélioration itérative par validation de la syntaxe
* Préservation du contexte dans les prompts d'itération

== Dépendances

=== Dépendances d'exécution principales

[cols="1,1"]
|===
| Dépendance | Objectif

| `net.sourceforge.plantuml:plantuml`
| Traitement des diagrammes PlantUML

| `dev.langchain4j:langchain4j-core`
| Fonctionnalité centrale LangChain4j

| `dev.langchain4j:langchain4j-ollama`
| Intégration Ollama

| `dev.langchain4j:langchain4j-open-ai`
| Intégration OpenAI/Groq/HuggingFace

| `dev.langchain4j:langchain4j-google-ai-gemini`
| Intégration Gemini

| `dev.langchain4j:langchain4j-mistral-ai`
| Intégration Mistral AI

| `dev.langchain4j:langchain4j-anthropic`
| Intégration Claude

| `dev.langchain4j:langchain4j-pgvector`
| Stockage d'embeddings pgvector

| `dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2`
| Modèle ONNX in-process

| `org.eclipse.jgit`
| Opérations Git pour publication des diagrammes

| `com.fasterxml.jackson` (yaml + kotlin)
| Parsing de configuration YAML

| `io.arrow-kt:arrow-core`
| Utilitaires de programmation fonctionnelle

| `com.github.docker-java:docker-java-core`
| Gestion Docker pour PgVectorService

| `com.github.docker-java:docker-java-transport-httpclient5`
| Transport HTTP pour docker-java

| `org.postgresql:postgresql`
| Pilote JDBC PostgreSQL

| `org.jetbrains.kotlinx:kotlinx-coroutines-core`
| Opérations asynchrones
|===

== Détails d'Architecture

=== Vue C4 — Contexte du Plugin

[plantuml]
----
@startuml
actor Développeur

rectangle "Environnement Développeur" {
  rectangle "Système de Build Gradle" {
    component "Plugin Gradle PlantUML"
  }
}

rectangle "Composants Internes Plugin" {
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

Développeur --> "Système de Build Gradle"
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

=== Architecture Hexagonale (Ports & Adaptateurs)

Le plugin suit une architecture hexagonale qui sépare la logique métier,
les interfaces externes et les technologies d'infrastructure.

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

package "Adaptateurs" {
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

== Prérequis du consommateur

La configuration du consommateur est minimale — le plugin gère en interne toute la configuration des dépôts et dépendances.

=== settings.gradle.kts
[source,kotlin]
----
pluginManagement.repositories {
    mavenLocal()
    gradlePluginPortal()
}
rootProject.name = "nom-de-votre-projet"
----

=== build.gradle.kts
[source,kotlin]
----
plugins { 
    id("com.cheroliv.plantuml") version "1.2026.0"
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
    
  # Autres configurations de fournisseurs...

git:
  userName: "github-actions[bot]"
  userEmail: "github-actions[bot]@users.noreply.github.com"
  # ... autres configurations git

rag:
  databaseUrl: "jdbc:postgresql://localhost:5432/plantuml_rag"
  username: "plantuml_user"
  password: "plantumm_password"
  tableName: "plantuml_embeddings"
----

== Compatibilité des fonctionnalités Gradle

[source,kotlin]
----
gradlePlugin {
    plugins {
        create("plantuml") {
            compatibility {
                features {
                    // Certaines fonctionnalités peuvent ne pas être compatibles avec Configuration Cache
                    configurationCache = false
                }
            }
        }
    }
}
----

Déclarer `configurationCache = false` assure la compatibilité et un reporting honnête des limitations.

== Feuille de route
* Validation et rapport d'erreurs de diagrammes améliorés
* Support de fournisseurs LLM supplémentaires
* Support du Configuration Cache
* Interface web pour visualiser et gérer les diagrammes générés
* Fonctionnalités RAG avancées pour de meilleures suggestions de diagrammes

== Licence
Ce projet est sous licence Apache 2.0 – voir le fichier `LICENSE`.