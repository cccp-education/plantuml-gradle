<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — Internes du Plugin

> Guide développeur & contributeur pour le plugin Gradle `plantuml-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=Tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Couverture](https://img.shields.io/static/v1?label=couverture&message=%E2%89%A575%25&color=green)]()
[![Licence](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=Licence)](../LICENCE)

- **Version** : `0.0.1` · **Groupe** : `education.cccp` · **ID plugin** : `education.cccp.plantuml`
- **Toolchain** : Java 24 (ordinal) · Kotlin 2.3.20 · Gradle 9.5.1
- **Build** : `./gradlew build -x test` · **Tests** : `./gradlew test functionalTest cucumberTest` · **Gate couverture** : `./gradlew koverThresholdCheck` (≥75 %)

🌐 Langues : [English](README.md) | **Français**

---

## Organisation des modules

```
plantuml-plugin/
├── build.gradle.kts                         # build du plugin (catalogue : gradle/libs.versions.toml)
├── settings.gradle.kts                       # nmcp (com.gradleup.nmcp 1.5.0)
└── src/
    ├── main/kotlin/plantuml/
    │   ├── PlantumlPlugin.kt                 # Point d'entrée — enregistre l'extension + 6 tâches
    │   ├── PlantumlManager.kt                # Coordinateur central (Configuration / Tasks / Extensions)
    │   ├── ConfigLoader.kt                   # Chargeur de config YAML (Jackson)
    │   ├── ConfigMerger.kt                   # Fusion : défauts < yaml < params CLI
    │   ├── models.kt                         # PlantumlConfig + modèles LLM / RAG / Git / Pool
    │   ├── kgmodels.kt                       # Modèles de données du graphe de connaissance
    │   ├── PlantumlMessages.kt               # Accès aux bundles i18n (10 langues)
    │   ├── apikey/                            # Pool de clés API : rotation, quotas, audit
    │   ├── service/                          # LlmService, DiagramProcessor, parser/renderer KG
    │   └── tasks/                             # 5 tâches Gradle typées (+ agrégat `docs`)
    ├── main/resources/i18n/
    │   ├── Messages.properties                # base (fallback)
    │   └── Messages_{en,zh,hi,es,fr,ar,bn,pt,ru,ur}.properties
    ├── test/                                  # JUnit5 unit + Cucumber (features/ + scenarios/)
    └── functionalTest/                         # Tests fonctionnels GradleRunner (+ models/ fine-tune)
```

Le plugin **se dogfoode** : le projet racine `plantuml-gradle/build.gradle.kts` applique
`id("education.cccp.plantuml")` et pointe `configPath` vers `plantuml-context.yml`.

## Contrats N0 (depuis workspace-bom MEMPHIS)

Consommés transitivement via `implementation(platform("education.cccp:workspace-bom:0.0.1"))` :

| Contrat | Artefact | Fournit |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext (contexte RAG) |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig (rotation clés API) |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | Contrats release-notes (cross-borough) |

## Dépendance consommateur

Le plugin **consomme** `education.cccp.codebase` version `0.0.2` (appliqué via
`alias(libs.plugins.codebase)` dans `build.gradle.kts`) :

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

Cela câble le pipeline RAG de codebase-gradle (indexation pgvector, contexte composite)
dans `collectPlantumlIndex`. Les consommateurs aval héritent du plugin codebase appliqué
automatiquement.

## Dépendances clés

| Bibliothèque | Version | Rôle |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 fournisseurs LLM : Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace (+ beta), pgvector, all-minilm-l6-v2 |
| **Moteur PlantUML**    | `1.2026.0`          | `net.sourceforge.plantuml` — validation syntaxe + rendu PNG |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | Pipeline slides/docs (asciidoctor-jvm + gems + slides) |
| **JGit**               | `7.5.0`             | Commits automatisés de diagrammes (core + ssh + archive) |
| **Arrow KT**           | `2.2.2`             | Core fonctionnel (core + fx-coroutines + jackson) |
| **Jackson**            | (BOM `2.21.1`)      | Config YAML (`jackson-dataformat-yaml`, module kotlin, jsr310) |
| **Testcontainers PG**  | `1.21.4`            | `pgvector/pgvector` pour tests RAG d'intégration |
| **docker-java**        | `3.7.0`             | Pilotage programmatique des conteneurs |
| **WireMock**           | `3.9.1`             | Mock HTTP des endpoints LLM en tests |
| **Kover**              | `0.9.8`             | Gate couverture (≥75 %, `includedSourceSets: main, functionalTest`) |

### Bundle de fournisseurs langchain4j (`libs.bundles.plantuml-ai`)

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### Pool de clés API (`plantuml/apikey/`)

- `ApiKeyPool.kt` — rotation round-robin / least-used
- `QuotaTracker.kt` / `QuotaResetManager.kt` — quota par clé + reset
- `QuotaAuditLogger.kt` — journal d'audit
- `Provider.kt` — enum provider + résolution de clé

## Matrice de tests

| Tâche | Périmètre | Timeout | Parallélisme |
|------|-------|---------|-------------|
| `test`           | JUnit5 unit (exclut `plantuml.scenarios.**`, `PlantUmlPluginFunctionalTests`) | 30 s/classe, 60 s global | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune (SmolLM2-135M) | 5 min | `(cores/2)` |
| `cucumberTest`   | Scénarios BDD Cucumber (engine junit-platform, exclut junit-jupiter) | 5 min, `forkEvery = 1` | `maxParallelForks = 1` |

- Les tests tagués `real-llm` sont **exclus par défaut** — activer avec `-Ptest.tags="real-llm"`.
- `functionalTest` active aussi le tag `fine-tune` avec `-Ptest.tags="fine-tune"`.
- `check` dépend de `functionalTest` + `cucumberTest` + `koverThresholdCheck`.
- **380/380 PASS** (selon `.agents/INDEX.adoc` clôture EPIC 9, sessions 125-130).

### Fixture de fine-tuning

`buildFineTuningImage` construit l'image Docker `plantuml-fine-tune:latest` ;
`downloadFineTuningModel` télécharge `HuggingFaceTB/SmolLM2-135M-Instruct` dans
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/`. Les deux sont câblés
comme dépendances de `functionalTest`.

## Réglage JVM

| Profil | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC (`-XX:+UseSerialGC`) | `MaxMetaspaceSize=256m` | `maxHeapSize=1g` (cucumber), adaptatif sinon |
| Tous | `-XX:TieredStopAtLevel=1` (démarrage rapide) | — | — |
| Tous | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` force un **worker JVM unique** (`maxParallelForks = 1`, `forkEvery = 0`) pour
maximiser la réutilisation WireMock + GradleRunner + sharedProjectDir entre classes imbriquées.

## Commandes de build

```bash
./gradlew build                                # build complet (compile + tests)
./gradlew build -x test                         # compile seulement
./gradlew test                                  # tests unitaires JUnit5
./gradlew functionalTest                        # tests fonctionnels
./gradlew cucumberTest                          # BDD Cucumber
./gradlew koverThresholdCheck                   # couverture ≥ 75 %
./gradlew publishToMavenLocal                   # publication locale
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (nmcp)
```

Toujours exécuter `./gradlew -q publishToMavenLocal` après modification du code source du
plugin — le projet racine consomme le plugin via `mavenLocal`.

## Pipeline CI

`.github/workflows/test.yml` — un job **Build & Test** sur `ubuntu-latest`,
JDK 24 (Temurin), `./gradlew build`, timeout 15 min. Déclenché sur push/PR vers
`main` / `master`.

`.github/workflows/ci.yml` — job `check` supplémentaire déclenché par `workflow_run`
(après génération README) et sur pushes non-README, JDK 23,
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`.

## Publication (NMCP)

Configurée via `com.gradleup.nmcp.settings` (**1.5.0**) dans
`plantuml-plugin/settings.gradle.kts` :

```kotlin
nmcpSettings {
    centralPortal {
        username = globalProps.getProperty("ossrhUsername")
        password = globalProps.getProperty("ossrhPassword")
        publishingType = "AUTOMATIC"
    }
}
```

Les identifiants sont lus depuis `~/.gradle/gradle.properties` (`ossrhUsername`, `ossrhPassword`).
La signature utilise `useGpgCmd()` ; signé uniquement hors CI pour les non-SNAPSHOT
(`System.getenv("CI") != "true"`).

Le POM (sur **toutes** les `MavenPublication`) déclare :
- Apache License 2.0
- développeur `cccp-education` (cccp.edu@gmail.com)
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- `<relocation>` optionnel quand la propriété `relocationGroup` est définie

Artefact publié : `education.cccp:plantuml-plugin:0.0.1` sur Maven Central.

## Statut des EPICs

Selon `.agents/INDEX.adoc` :

| EPIC | Statut |
|------|--------|
| 1 Tests unitaires            | ✅ TERMINÉ |
| 2 Tests fonctionnels         | ✅ TERMINÉ |
| 3 Architecture               | ✅ TERMINÉ |
| 4 Documentation              | ✅ TERMINÉ |
| 5 RAG System                 | ✅ TERMINÉ |
| 6 API Key Pool               | ✅ TERMINÉ |
| 7 Dogfooding Plugin          | ✅ TERMINÉ (Phase 1) |
| 8 Migration AsciiDoc         | ✅ TERMINÉ |
| 9 Knowledge Graph Diagram    | ✅ TERMINÉ (380/380 PASS) |
| 10 Intégration Graphify      | ✅ TERMINÉ |
| 11 Article Blog KG + Tests   | ✅ TERMINÉ |
| 12 Consolidation             | ✅ TERMINÉ (Kover 81.19 %) |
| PUB-1 Publication Maven Central | ✅ TERMINÉ (0.0.1) |
| **13 Multi-provider API Pool** | 🟡 EN COURS (Sessions 138-141) |
| **PLT-I18N Internationalisation** | 🟠 EN COURS (US-0..US-3 ✅, 16/28 pts, session 151) |

## Docs d'architecture

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — Design composants & flux de données
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — Feuille de route EPICs
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — Sessions, EPICs, gouvernance
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — Problèmes connus & correctifs
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — Internes du pool
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — Procédures de dev

## Contribuer

1. Le build compile : `./gradlew build -x test`
2. Republier localement : `./gradlew -q publishToMavenLocal`
3. Tests unitaires verts : `./gradlew test`
4. Couverture respectée : `./gradlew koverThresholdCheck` (≥75 %)
5. Pas de fuite `@startuml`/`@enduml` dans les `.puml` générés
6. Suivre les conventions DDD (value objects dans `models.kt`, services dans `service/`)
7. Respecter l'i18n : ajouter les clés dans **tous** les `Messages_*.properties` (10 langues)

Voir [CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) et
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc).

## Licence

Apache License 2.0 — voir [LICENCE](../LICENCE).

---

_Partie de l'écosystème CCCP Education — `groupId: education.cccp`._