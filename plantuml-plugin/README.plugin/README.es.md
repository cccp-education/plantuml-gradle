<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — Interior del Plugin

> Guía para desarrolladores y contribuyentes del plugin Gradle `plantuml-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Coverage](https://img.shields.io/static/v1?label=coverage&message=%E2%89%A575%25&color=green)]()
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **Versión**: `0.0.1` · **Grupo**: `education.cccp` · **ID del plugin**: `education.cccp.plantuml`
- **Toolchain**: Java 24 (ordinal) · Kotlin 2.3.20 · Gradle 9.5.1
- **Build**: `./gradlew build -x test` · **Tests**: `./gradlew test functionalTest cucumberTest` · **Puerta de cobertura**: `./gradlew koverThresholdCheck` (≥75%)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | **Español** | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## Disposición de módulos

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

El plugin **se dogfooda**: el proyecto raíz `plantuml-gradle/build.gradle.kts` aplica
`id("education.cccp.plantuml")` y apunta `configPath` a `plantuml-context.yml`.

## Contratos N0 (de workspace-bom MEMPHIS)

Consumidos transitivamente vía `implementation(platform("education.cccp:workspace-bom:0.0.1"))`:

| Contrato | Artefacto | Proporciona |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext (contexto RAG) |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig (rotación de claves API) |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | Contratos de release-notes (cross-borough) |

## Dependencia del consumidor

El plugin **consume** `education.cccp.codebase` versión `0.0.2` (aplicado vía
`alias(libs.plugins.codebase)` en `build.gradle.kts`):

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

Esto cablea el pipeline RAG de codebase-gradle (indexación pgvector, contexto compuesto)
en `collectPlantumlIndex`. Los consumidores aguas abajo reciben el plugin codebase aplicado
automáticamente.

## Dependencias clave

| Biblioteca | Versión | Rol |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 proveedores LLM: Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace (+ beta), pgvector, all-minilm-l6-v2 |
| **Motor PlantUML**    | `1.2026.0`          | `net.sourceforge.plantuml` — validación de sintaxis + render PNG |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | Pipeline slides/docs (asciidoctor-jvm + gems + slides) |
| **JGit**               | `7.5.0`             | Commits automatizados de diagramas (core + ssh + archive) |
| **Arrow KT**           | `2.2.2`             | Core funcional (core + fx-coroutines + jackson) |
| **Jackson**            | (BOM `2.21.1`)      | Config YAML (`jackson-dataformat-yaml`, módulo kotlin, jsr310) |
| **Testcontainers PG**  | `1.21.4`            | `pgvector/pgvector` para tests de integración RAG |
| **docker-java**        | `3.7.0`             | Control programático de contenedores |
| **WireMock**           | `3.9.1`             | Mock HTTP de endpoints LLM en tests |
| **Kover**              | `0.9.8`             | Puerta de cobertura (≥75 %, `includedSourceSets: main, functionalTest`) |

### Bundle de proveedores langchain4j (`libs.bundles.plantuml-ai`)

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### Pool de claves API (`plantuml/apikey/`)

- `ApiKeyPool.kt` — rotación round-robin / least-used
- `QuotaTracker.kt` / `QuotaResetManager.kt` — cuota por clave + reset
- `QuotaAuditLogger.kt` — diario de auditoría
- `Provider.kt` — enum de proveedor + resolución de clave

## Matriz de tests

| Tarea | Ámbito | Timeout | Paralelismo |
|------|-------|---------|-------------|
| `test`           | JUnit5 unit (excluye `plantuml.scenarios.**`, `PlantUmlPluginFunctionalTests`) | 30 s/clase, 60 s global | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune (SmolLM2-135M) | 5 min | `(cores/2)` |
| `cucumberTest`   | Escenarios BDD Cucumber (engine junit-platform, excluye junit-jupiter) | 5 min, `forkEvery = 1` | `maxParallelForks = 1` |

- Los tests etiquetados `real-llm` están **excluidos por defecto** — habilitar con `-Ptest.tags="real-llm"`.
- `functionalTest` además habilita la etiqueta `fine-tune` con `-Ptest.tags="fine-tune"`.
- `check` depende de `functionalTest` + `cucumberTest` + `koverThresholdCheck`.
- **380/380 PASS** (según `.agents/INDEX.adoc` cierre EPIC 9, sessions 125-130).

### Fixture de fine-tuning

`buildFineTuningImage` construye la imagen Docker `plantuml-fine-tune:latest`;
`downloadFineTuningModel` descarga `HuggingFaceTB/SmolLM2-135M-Instruct` en
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/`. Ambos están cableados como
dependencias de `functionalTest`.

## Ajuste de JVM

| Perfil | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC (`-XX:+UseSerialGC`) | `MaxMetaspaceSize=256m` | `maxHeapSize=1g` (cucumber), adaptativo en el resto |
| Todos | `-XX:TieredStopAtLevel=1` (arranque rápido) | — | — |
| Todos | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` fuerza un **único worker JVM** (`maxParallelForks = 1`, `forkEvery = 0`) para
maximizar la reutilización de WireMock + GradleRunner + sharedProjectDir entre clases anidadas.

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

Siempre ejecutar `./gradlew -q publishToMavenLocal` tras modificar el código fuente del
plugin — el proyecto raíz consume el plugin vía `mavenLocal`.

## Pipeline CI

`.github/workflows/test.yml` — un único job **Build & Test** en `ubuntu-latest`,
JDK 24 (Temurin), `./gradlew build`, timeout 15 min. Se dispara en push/PR a
`main` / `master`.

`.github/workflows/ci.yml` — job `check` suplementario disparado por `workflow_run`
(tras generación de README) y en pushes no-README, JDK 23,
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`.

## Publicación (NMCP)

Configurada vía `com.gradleup.nmcp.settings` (**1.5.0**) en
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

Las credenciales se leen de `~/.gradle/gradle.properties` (`ossrhUsername`, `ossrhPassword`).
El firmado usa `useGpgCmd()`; firmado solo fuera de CI para builds no-SNAPSHOT
(`System.getenv("CI") != "true"`).

El POM (en **todas** las `MavenPublication`) declara:
- Apache License 2.0
- desarrollador `cccp-education` (cccp.edu@gmail.com)
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- `<relocation>` opcional cuando la propiedad `relocationGroup` está definida

Artefacto publicado: `education.cccp:plantuml-plugin:0.0.1` en Maven Central.

## Estado de los EPICs

Según `.agents/INDEX.adoc`:

| EPIC | Estado |
|------|--------|
| 1 Tests unitarios            | ✅ TERMINADO |
| 2 Tests funcionales         | ✅ TERMINADO |
| 3 Arquitectura               | ✅ TERMINADO |
| 4 Documentación              | ✅ TERMINADO |
| 5 RAG System                 | ✅ TERMINADO |
| 6 API Key Pool               | ✅ TERMINADO |
| 7 Dogfooding Plugin          | ✅ TERMINADO (Phase 1) |
| 8 Migración AsciiDoc         | ✅ TERMINADO |
| 9 Knowledge Graph Diagram    | ✅ TERMINADO (380/380 PASS) |
| 10 Integración Graphify      | ✅ TERMINADO |
| 11 Artículo Blog KG + Tests   | ✅ TERMINADO |
| 12 Consolidación             | ✅ TERMINADO (Kover 81.19 %) |
| PUB-1 Publicación Maven Central | ✅ TERMINADO (0.0.1) |
| **13 Multi-provider API Pool** | 🟡 EN CURSO (Sessions 138-141) |
| **PLT-I18N Internacionalización** | 🟠 EN CURSO (US-0..US-3 ✅, 16/28 pts, session 151) |

## Docs de arquitectura

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — Diseño de componentes y flujo de datos
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — Hoja de ruta de EPICs
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — Sesiones, EPICs, gobernanza
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — Problemas conocidos y correcciones
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — Interior del pool
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — Procedimientos de dev

## Contribuir

1. El build compila: `./gradlew build -x test`
2. Republicar localmente: `./gradlew -q publishToMavenLocal`
3. Tests unitarios en verde: `./gradlew test`
4. Cobertura respetada: `./gradlew koverThresholdCheck` (≥75 %)
5. Sin fugas `@startuml`/`@enduml` en los `.puml` generados
6. Seguir convenciones DDD (value objects en `models.kt`, servicios en `service/`)
7. Respetar i18n: añadir claves en **todos** los `Messages_*.properties` (10 idiomas)

Ver [CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) y
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc).

## Licencia

Apache License 2.0 — ver [LICENCE](../LICENCE).

---

_Parte del ecosistema CCCP Education — `groupId: education.cccp`._