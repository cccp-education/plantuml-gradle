<!-- master source — other languages are translations of this file -->
# plantuml-gradle — Plugin Internals

> Developer & contributor guide for the `plantuml-plugin` Gradle plugin.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Coverage](https://img.shields.io/static/v1?label=coverage&message=%E2%89%A575%25&color=green)]()
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **Version**: `0.0.1` · **Group**: `education.cccp` · **Plugin ID**: `education.cccp.plantuml`
- **Toolchain**: Java 24 (ordinal) · Kotlin 2.3.20 · Gradle 9.5.1
- **Build**: `./gradlew build -x test` · **Tests**: `./gradlew test functionalTest cucumberTest` · **Coverage gate**: `./gradlew koverThresholdCheck` (≥75%)

🌐 Languages: **EN** | [中文](README.plugin/README.zh.md) | [हिन्दी](README.plugin/README.hi.md) | [Español](README.plugin/README.es.md) | [Français](README.plugin/README.fr.md) | [العربية](README.plugin/README.ar.md) | [বাংলা](README.plugin/README.bn.md) | [Português](README.plugin/README.pt.md) | [Русский](README.plugin/README.ru.md) | [اردو](README.plugin/README.ur.md)

---

## Module layout

```
plantuml-plugin/
├── build.gradle.kts                         # plugin build (catalog: gradle/libs.versions.toml)
├── settings.gradle.kts                       # nmcp settings (com.gradleup.nmcp 1.5.0)
└── src/
    ├── main/kotlin/plantuml/
    │   ├── PlantumlPlugin.kt                 # Plugin entry point — registers extension + 6 tasks
    │   ├── PlantumlManager.kt                # Central coordinator (Configuration / Tasks / Extensions)
    │   ├── ConfigLoader.kt                   # YAML config loader (Jackson)
    │   ├── ConfigMerger.kt                   # Merge: defaults < yaml < CLI params
    │   ├── models.kt                         # PlantumlConfig + LLM / RAG / Git / Pool models
    │   ├── kgmodels.kt                       # Knowledge-graph data models
    │   ├── PlantumlMessages.kt               # i18n message bundle accessor (10 langs)
    │   ├── apikey/                            # API-key pool: rotation, quota tracking, audit
    │   ├── service/                          # LlmService, DiagramProcessor, KG parser/renderer
    │   └── tasks/                             # 5 typed Gradle tasks (+ `docs` aggregate)
    ├── main/resources/i18n/
    │   ├── Messages.properties                # base (fallback)
    │   └── Messages_{en,zh,hi,es,fr,ar,bn,pt,ru,ur}.properties
    ├── test/                                  # JUnit5 unit + Cucumber (features/ + scenarios/)
    └── functionalTest/                         # GradleRunner functional tests (+ models/ fine-tune)
```

The plugin **dogfoods itself**: the root `plantuml-gradle/build.gradle.kts` applies
`id("education.cccp.plantuml")` and points `configPath` at `plantuml-context.yml`.

## N0 contracts (from workspace-bom MEMPHIS)

Consumed transitively via `implementation(platform("education.cccp:workspace-bom:0.0.1"))`:

| Contract | Artifact | Provides |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext (RAG context) |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig (API-key rotation) |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | Release-notes contracts (cross-borough) |

## Consumer dependency

The plugin **consumes** `education.cccp.codebase` version `0.0.2` (applied via
`alias(libs.plugins.codebase)` in `build.gradle.kts`):

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

This wires the codebase-gradle RAG pipeline (pgvector indexing, composite context) into
`collectPlantumlIndex`. Downstream consumers get the codebase plugin applied automatically.

## Key dependencies

| Library | Version | Role |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 LLM providers: Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace (+ beta), pgvector, all-minilm-l6-v2 |
| **PlantUML engine**    | `1.2026.0`          | `net.sourceforge.plantuml` — syntax validation + PNG render |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | Slides/docs pipeline (asciidoctor-jvm + gems + slides) |
| **JGit**               | `7.5.0`             | Automated diagram commits (core + ssh + archive) |
| **Arrow KT**           | `2.2.2`             | Functional core (core + fx-coroutines + jackson) |
| **Jackson**            | (BOM `2.21.1`)      | YAML config (`jackson-dataformat-yaml`, kotlin module, jsr310) |
| **Testcontainers PG**  | `1.21.4`            | `pgvector/pgvector` for RAG integration tests |
| **docker-java**        | `3.7.0`             | Programmatic container control |
| **WireMock**           | `3.9.1`             | HTTP mocking of LLM endpoints in tests |
| **Kover**              | `0.9.8`             | Coverage gate (≥75 %, `includedSourceSets: main, functionalTest`) |

### Langchain4j provider bundle (`libs.bundles.plantuml-ai`)

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### API-key pool (`plantuml/apikey/`)

- `ApiKeyPool.kt` — round-robin / least-used rotation
- `QuotaTracker.kt` / `QuotaResetManager.kt` — per-key quota + reset
- `QuotaAuditLogger.kt` — audit journal
- `Provider.kt` — provider enum + key resolution

## Test matrix

| Task | Scope | Timeout | Parallelism |
|------|-------|---------|-------------|
| `test`           | JUnit5 unit (excludes `plantuml.scenarios.**`, `PlantUmlPluginFunctionalTests`) | 30 s/class, 60 s global | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune (SmolLM2-135M) | 5 min | `(cores/2)` |
| `cucumberTest`   | Cucumber BDD scenarios (JUnit-platform engine, excludes junit-jupiter) | 5 min, `forkEvery = 1` | `maxParallelForks = 1` |

- Tests tagged `real-llm` are **excluded by default** — enable with `-Ptest.tags="real-llm"`.
- `functionalTest` additionally enables `fine-tune` tag with `-Ptest.tags="fine-tune"`.
- `check` depends on `functionalTest` + `cucumberTest` + `koverThresholdCheck`.
- **380/380 PASS** (per `.agents/INDEX.adoc` EPIC 9 close-out, sessions 125-130).

### Fine-tuning fixture

`buildFineTuningImage` builds Docker image `plantuml-fine-tune:latest`;
`downloadFineTuningModel` fetches `HuggingFaceTB/SmolLM2-135M-Instruct` into
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/`. Both are wired as
dependencies of `functionalTest`.

## JVM tuning

| Profile | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC (`-XX:+UseSerialGC`) | `MaxMetaspaceSize=256m` | `maxHeapSize=1g` (cucumber), adaptive elsewhere |
| All | `-XX:TieredStopAtLevel=1` (fast startup) | — | — |
| All | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` forces a **single JVM worker** (`maxParallelForks = 1`, `forkEvery = 0`) to
maximize WireMock + GradleRunner + sharedProjectDir reuse across nested classes.

## Build commands

```bash
./gradlew build                                # full build (compile + tests)
./gradlew build -x test                         # compile only
./gradlew test                                  # JUnit5 unit tests
./gradlew functionalTest                        # functional tests
./gradlew cucumberTest                          # Cucumber BDD
./gradlew koverThresholdCheck                   # coverage ≥ 75 %
./gradlew publishToMavenLocal                   # local publish
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (nmcp)
```

Always run `./gradlew -q publishToMavenLocal` after modifying plugin source — the
root project consumes the plugin via `mavenLocal`.

## CI pipeline

`.github/workflows/test.yml` — single **Build & Test** job on `ubuntu-latest`,
JDK 24 (Temurin), `./gradlew build`, 15 min timeout. Triggers on push/PR to
`main` / `master`.

`.github/workflows/ci.yml` — supplementary `check` job triggered by `workflow_run`
(after README generation) and on non-README pushes, JDK 23,
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`.

## Publication (NMCP)

Configured via `com.gradleup.nmcp.settings` (**1.5.0**) in
`plantuml-plugin/settings.gradle.kts`:

```kotlin
nmcpSettings {
    centralPortal {
        username = globalProps.getProperty("ossrhUsername")
        password = globalProps.getProperty("ossrhPassword")
        publishingType = "AUTOMATIC"
    }
}
```

Credentials are read from `~/.gradle/gradle.properties` (`ossrhUsername`, `ossrhPassword`).
Signing uses `useGpgCmd()`; signed only outside CI for non-SNAPSHOT builds
(`System.getenv("CI") != "true"`).

POM (on **all** `MavenPublication`s) declares:
- Apache License 2.0
- developer `cccp-education` (cccp.edu@gmail.com)
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- optional `<relocation>` when `relocationGroup` property is set

Published artifact: `education.cccp:plantuml-plugin:0.0.1` on Maven Central.

## EPIC status

Per `.agents/INDEX.adoc`:

| EPIC | Status |
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

## Architecture docs

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — Component & data-flow design
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — EPIC roadmap
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — Sessions, EPICs, governance
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — Known issues & fixes
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — Pool internals
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — Dev procedures

## Contributing

1. Build compiles: `./gradlew build -x test`
2. Republish locally: `./gradlew -q publishToMavenLocal`
3. Unit tests green: `./gradlew test`
4. Coverage respected: `./gradlew koverThresholdCheck` (≥75 %)
5. No `@startuml`/`@enduml` leaks in generated `.puml`
6. Follow DDD conventions (value objects in `models.kt`, services in `service/`)
7. Respect i18n: add keys to **all** `Messages_*.properties` (10 languages)

See [CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) and
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc).

## License

Apache License 2.0 — see [LICENCE](../LICENCE).

---

_Part of the CCCP Education ecosystem — `groupId: education.cccp`._