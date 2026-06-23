<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — Interior do Plugin

> Guia para programadores e contribuintes do plugin Gradle `plantuml-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Coverage](https://img.shields.io/static/v1?label=coverage&message=%E2%89%A575%25&color=green)]()
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **Versão**: `0.0.1` · **Grupo**: `education.cccp` · **ID do plugin**: `education.cccp.plantuml`
- **Toolchain**: Java 24 (ordinal) · Kotlin 2.3.20 · Gradle 9.5.1
- **Build**: `./gradlew build -x test` · **Testes**: `./gradlew test functionalTest cucumberTest` · **Portão de cobertura**: `./gradlew koverThresholdCheck` (≥75%)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | **Português** | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## Disposição dos módulos

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

O plugin **faz dogfooding de si mesmo**: o projeto raiz `plantuml-gradle/build.gradle.kts` aplica
`id("education.cccp.plantuml")` e aponta `configPath` para `plantuml-context.yml`.

## Contratos N0 (de workspace-bom MEMPHIS)

Consumidos transitivamente via `implementation(platform("education.cccp:workspace-bom:0.0.1"))`:

| Contrato | Artefacto | Fornece |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext (contexto RAG) |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig (rotação de chaves API) |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | Contratos de release-notes (cross-borough) |

## Dependência do consumidor

O plugin **consome** `education.cccp.codebase` versão `0.0.2` (aplicado via
`alias(libs.plugins.codebase)` em `build.gradle.kts`):

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

Isto cableia o pipeline RAG de codebase-gradle (indexação pgvector, contexto composto)
em `collectPlantumlIndex`. Os consumidores a jusante recebem o plugin codebase aplicado
automaticamente.

## Dependências chave

| Biblioteca | Versão | Papel |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 provedores LLM: Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace (+ beta), pgvector, all-minilm-l6-v2 |
| **Motor PlantUML**    | `1.2026.0`          | `net.sourceforge.plantuml` — validação de sintaxe + render PNG |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | Pipeline slides/docs (asciidoctor-jvm + gems + slides) |
| **JGit**               | `7.5.0`             | Commits automatizados de diagramas (core + ssh + archive) |
| **Arrow KT**           | `2.2.2`             | Core funcional (core + fx-coroutines + jackson) |
| **Jackson**            | (BOM `2.21.1`)      | Config YAML (`jackson-dataformat-yaml`, módulo kotlin, jsr310) |
| **Testcontainers PG**  | `1.21.4`            | `pgvector/pgvector` para testes de integração RAG |
| **docker-java**        | `3.7.0`             | Controlo programático de contentores |
| **WireMock**           | `3.9.1`             | Mock HTTP de endpoints LLM em testes |
| **Kover**              | `0.9.8`             | Portão de cobertura (≥75 %, `includedSourceSets: main, functionalTest`) |

### Bundle de provedores langchain4j (`libs.bundles.plantuml-ai`)

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### Pool de chaves API (`plantuml/apikey/`)

- `ApiKeyPool.kt` — rotação round-robin / least-used
- `QuotaTracker.kt` / `QuotaResetManager.kt` — quota por chave + reset
- `QuotaAuditLogger.kt` — diário de auditoria
- `Provider.kt` — enum de provedor + resolução de chave

## Matriz de testes

| Tarefa | Âmbito | Timeout | Paralelismo |
|------|-------|---------|-------------|
| `test`           | JUnit5 unit (excludes `plantuml.scenarios.**`, `PlantUmlPluginFunctionalTests`) | 30 s/classe, 60 s global | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune (SmolLM2-135M) | 5 min | `(cores/2)` |
| `cucumberTest`   | Cucumber BDD scenarios (JUnit-platform engine, excludes junit-jupiter) | 5 min, `forkEvery = 1` | `maxParallelForks = 1` |

- Testes etiquetados `real-llm` estão **excluídos por padrão** — ativar com `-Ptest.tags="real-llm"`.
- `functionalTest` adicionalmente ativa a etiqueta `fine-tune` com `-Ptest.tags="fine-tune"`.
- `check` depende de `functionalTest` + `cucumberTest` + `koverThresholdCheck`.
- **380/380 PASS** (segundo `.agents/INDEX.adoc` fecho EPIC 9, sessions 125-130).

### Fixture de fine-tuning

`buildFineTuningImage` constrói a imagem Docker `plantuml-fine-tune:latest`;
`downloadFineTuningModel` descarrega `HuggingFaceTB/SmolLM2-135M-Instruct` em
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/`. Ambos estão cabeados como
dependências de `functionalTest`.

## Afinação JVM

| Perfil | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC (`-XX:+UseSerialGC`) | `MaxMetaspaceSize=256m` | `maxHeapSize=1g` (cucumber), adaptativo no resto |
| Todos | `-XX:TieredStopAtLevel=1` (arranque rápido) | — | — |
| Todos | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` força um **único worker JVM** (`maxParallelForks = 1`, `forkEvery = 0`) para
maximizar a reutilização de WireMock + GradleRunner + sharedProjectDir entre classes aninhadas.

## Comandos de build

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

Executar sempre `./gradlew -q publishToMavenLocal` após modificar o código-fonte do
plugin — o projeto raiz consome o plugin via `mavenLocal`.

## Pipeline CI

`.github/workflows/test.yml` — um único job **Build & Test** em `ubuntu-latest`,
JDK 24 (Temurin), `./gradlew build`, timeout 15 min. Dispara em push/PR para
`main` / `master`.

`.github/workflows/ci.yml` — job `check` suplementar disparado por `workflow_run`
(após geração de README) e em pushes não-README, JDK 23,
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`.

## Publicação (NMCP)

Configurada via `com.gradleup.nmcp.settings` (**1.5.0**) em
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

As credenciais são lidas de `~/.gradle/gradle.properties` (`ossrhUsername`, `ossrhPassword`).
A assinatura usa `useGpgCmd()`; assinado apenas fora de CI para builds não-SNAPSHOT
(`System.getenv("CI") != "true"`).

O POM (em **todas** as `MavenPublication`) declara:
- Apache License 2.0
- programador `cccp-education` (cccp.edu@gmail.com)
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- `<relocation>` opcional quando a propriedade `relocationGroup` está definida

Artefacto publicado: `education.cccp:plantuml-plugin:0.0.1` em Maven Central.

## Estado dos EPICs

Segundo `.agents/INDEX.adoc`:

| EPIC | Estado |
|------|--------|
| 1 Testes unitários            | ✅ TERMINADO |
| 2 Testes funcionais         | ✅ TERMINADO |
| 3 Arquitetura               | ✅ TERMINADO |
| 4 Documentação              | ✅ TERMINADO |
| 5 RAG System                 | ✅ TERMINADO |
| 6 API Key Pool               | ✅ TERMINADO |
| 7 Dogfooding Plugin          | ✅ TERMINADO (Phase 1) |
| 8 Migração AsciiDoc         | ✅ TERMINADO |
| 9 Knowledge Graph Diagram    | ✅ TERMINADO (380/380 PASS) |
| 10 Integração Graphify      | ✅ TERMINADO |
| 11 Artigo Blog KG + Testes   | ✅ TERMINADO |
| 12 Consolidação             | ✅ TERMINADO (Kover 81.19 %) |
| PUB-1 Publicação Maven Central | ✅ TERMINADO (0.0.1) |
| **13 Multi-provider API Pool** | 🟡 EM CURSO (Sessions 138-141) |
| **PLT-I18N Internacionalização** | 🟠 EM CURSO (US-0..US-3 ✅, 16/28 pts, session 151) |

## Docs de arquitetura

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — Design de componentes e fluxo de dados
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — Roteiro de EPICs
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — Sessões, EPICs, governação
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — Problemas conhecidos e correções
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — Interior do pool
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — Procedimentos de dev

## Contribuir

1. O build compila: `./gradlew build -x test`
2. Republicar localmente: `./gradlew -q publishToMavenLocal`
3. Testes unitários verdes: `./gradlew test`
4. Cobertura respeitada: `./gradlew koverThresholdCheck` (≥75 %)
5. Sem fugas `@startuml`/`@enduml` nos `.puml` gerados
6. Seguir convenções DDD (value objects em `models.kt`, serviços em `service/`)
7. Respeitar i18n: adicionar chaves em **todos** os `Messages_*.properties` (10 idiomas)

Ver [CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) e
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc).

## Licença

Apache License 2.0 — ver [LICENCE](../LICENCE).

---

_Parte do ecossistema CCCP Education — `groupId: education.cccp`._