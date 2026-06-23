<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — Guide Consommateur

> Plugin Gradle assisté par IA pour générer des diagrammes PlantUML depuis des prompts en langage naturel, avec RAG (pgvector), un moteur de graphe de connaissance, et un pool rotatif de clés API.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=Tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Licence](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=Licence)](../LICENCE)

- **Version** : `0.0.1` · **Groupe** : `education.cccp` · **ID plugin** : `education.cccp.plantuml`
- **Toolchain** : Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **Build** : `./gradlew build` · **Tests** : `./gradlew test` + `functionalTest` + `cucumberTest` (380/380 PASS)
- **Couverture** : ≥ 75 % (Kover `koverThresholdCheck`, intégré à `check`)

🌐 Langues : [English](README.md) | **Français**

---

## Ce que ça fait

`plantuml-gradle` surveille les fichiers `.prompt` dans un répertoire de prompts, les
envoie à un LLM (7 fournisseurs langchain4j), valide la syntaxe PlantUML retournée,
génère des images PNG, collecte les diagrammes valides dans un index RAG **pgvector**,
et produit un **diagramme de graphe de connaissance** depuis un extrait
`graphify-out/graph.json` du workspace.

Partie de l'écosystème multi-plugins CCCP Education :

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (déterministe)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validation ──► PNG + index RAG
```

Le plugin consomme `education.cccp.codebase` (`0.0.2`) pour l'indexation RAG et partage
les contrats N0 via `education.cccp:workspace-bom:0.0.1`.

## Démarrage rapide

### 1. Appliquer le plugin

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. Configurer l'extension (optionnel)

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "fr"
    supportedLanguages = listOf("en", "fr", "es")
}
```

Si `plantuml-context.yml` est absent ou vide, le plugin utilise les valeurs par défaut
intégrées (voir `PlantumlManager.Configuration`).

### 3. Générer les diagrammes

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → diagramme KG (sans LLM)
./gradlew docs                            # pipeline complet : KG + docs + diagrammes + validation
```

## Tâches disponibles

| Tâche | Groupe | Description |
|-------|--------|-------------|
| `generatePlantumlDiagrams`      | generate | Traite les prompts PlantUML et génère les diagrammes avec l'aide d'un LLM |
| `validatePlantumlSyntax`         | verify   | Valide la syntaxe PlantUML pour le débogage (`-Pplantuml.diagram=file.puml`) |
| `collectPlantumlIndex`           | collect  | Reconstruit l'index RAG avec les diagrammes PlantUML collectés |
| `generateDiagramDocs`            | generate | Génère automatiquement des diagrammes PlantUML de documentation depuis le graphe de connaissance Graphify |
| `generateKnowledgeGraphDiagram`  | generate | Génère un diagramme de graphe de connaissance depuis `graphify-out/graph.json` (déterministe, sans LLM) |
| `docs`                           | info     | Pipeline complet : KG + docs + diagrammes, finalisé par `validatePlantumlSyntax` |

## Extension DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "fr"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| Propriété | Type | Défaut | Description |
|-----------|------|--------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | Chemin du fichier de configuration YAML |
| `language`            | `Property<String>`      | `"en"`                 | Langue i18n active pour les libellés de tâches |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | Langues d'UI activées (10 supportées) |

## Prérequis

- **Java** 24+ (toolchain Kotlin 2.3.20, ordinal JDK 24)
- **Gradle** 9.5.1+
- **Docker** (pour Testcontainers pgvector dans `collectPlantumlIndex`)
- Un **endpoint LLM** — Ollama par défaut `http://localhost:11434`, modèle `smollm:135m`
  (les ports `11434–11436` sont interdits globalement ; rotation sur `11437–11465`)

## Build & tests

```bash
./gradlew build                       # build complet (compile + tests)
./gradlew test                        # tests unitaires JUnit5
./gradlew functionalTest              # tests fonctionnels (GradleRunner + WireMock)
./gradlew cucumberTest                # scénarios BDD Cucumber
./gradlew koverThresholdCheck         # couverture ≥ 75 %
./gradlew publishToMavenLocal         # publication locale
```

Pour activer les tests LLM réels (exclus par défaut) :

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## Dépannage

| Symptôme | Solution |
|----------|----------|
| Avertissement `No plantuml-context.yml`   | Innocuous — le plugin utilise les valeurs par défaut |
| Conflit de port pgvector 5432             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| LLM bloqué / timeout test 30 s            | vérifier qu'Ollama tourne, augmenter le heap (`GRADLE_OPTS=-Xmx2g`) |
| `graphify-out/graph.json not found`       | exécuter `graphify . --no-viz` d'abord pour construire le graphe |
| API Key Pool is empty                      | configurer `langchain4j.<provider>.pool` dans `plantuml-context.yml` |

Voir [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) pour le détail.

## Licence

Apache License 2.0 — voir [LICENCE](../LICENCE).

---

_Partie de l'écosystème CCCP Education — `groupId: education.cccp`._