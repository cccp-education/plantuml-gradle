<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — Внутреннее устройство плагина

> Руководство для разработчиков и контрибьюторов плагина Gradle `plantuml-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Coverage](https://img.shields.io/static/v1?label=coverage&message=%E2%89%A575%25&color=green)]()
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **Версия**: `0.0.1` · **Группа**: `education.cccp` · **ID плагина**: `education.cccp.plantuml`
- **Набор инструментов**: Java 24 (ordinal) · Kotlin 2.3.20 · Gradle 9.5.1
- **Сборка**: `./gradlew build -x test` · **Тесты**: `./gradlew test functionalTest cucumberTest` · **Шлюз покрытия**: `./gradlew koverThresholdCheck` (≥75%)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | **Русский** | [اردو](README.ur.md)

---

## Структура модулей

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

Плагин **использует сам себя (dogfood)**: корневой проект `plantuml-gradle/build.gradle.kts`
применяет `id("education.cccp.plantuml")` и направляет `configPath` на `plantuml-context.yml`.

## N0 контракты (из workspace-bom MEMPHIS)

Потребляются транзитивно через `implementation(platform("education.cccp:workspace-bom:0.0.1"))`:

| Контракт | Артефакт | Предоставляет |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext (контекст RAG) |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig (ротация API-ключей) |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | Контракты release-notes (cross-borough) |

## Зависимость потребителя

Плагин **потребляет** `education.cccp.codebase` версии `0.0.2` (применяется через
`alias(libs.plugins.codebase)` в `build.gradle.kts`):

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

Это подключает RAG-конвейер codebase-gradle (индексация pgvector, составной контекст)
в `collectPlantumlIndex`. Нижестоящие потребители получают плагин codebase применённым
автоматически.

## Ключевые зависимости

| Библиотека | Версия | Роль |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 LLM-провайдеров: Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace (+ beta), pgvector, all-minilm-l6-v2 |
| **Движок PlantUML**    | `1.2026.0`          | `net.sourceforge.plantuml` — проверка синтаксиса + рендер PNG |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | Конвейер slides/docs (asciidoctor-jvm + gems + slides) |
| **JGit**               | `7.5.0`             | Автоматические коммиты диаграмм (core + ssh + archive) |
| **Arrow KT**           | `2.2.2`             | Функциональное ядро (core + fx-coroutines + jackson) |
| **Jackson**            | (BOM `2.21.1`)      | YAML-конфиг (`jackson-dataformat-yaml`, kotlin-модуль, jsr310) |
| **Testcontainers PG**  | `1.21.4`            | `pgvector/pgvector` для интеграционных RAG-тестов |
| **docker-java**        | `3.7.0`             | Программное управление контейнерами |
| **WireMock**           | `3.9.1`             | HTTP-моки LLM-эндпоинтов в тестах |
| **Kover**              | `0.9.8`             | Шлюз покрытия (≥75 %, `includedSourceSets: main, functionalTest`) |

### Бандл провайдеров langchain4j (`libs.bundles.plantuml-ai`)

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### Пул API-ключей (`plantuml/apikey/`)

- `ApiKeyPool.kt` — ротация round-robin / least-used
- `QuotaTracker.kt` / `QuotaResetManager.kt` — квота на ключ + сброс
- `QuotaAuditLogger.kt` — журнал аудита
- `Provider.kt` — enum провайдера + разрешение ключа

## Матрица тестов

| Задача | Область | Таймаут | Параллелизм |
|------|-------|---------|-------------|
| `test`           | JUnit5 unit (excludes `plantuml.scenarios.**`, `PlantUmlPluginFunctionalTests`) | 30 s/class, 60 s global | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune (SmolLM2-135M) | 5 min | `(cores/2)` |
| `cucumberTest`   | Cucumber BDD scenarios (JUnit-platform engine, excludes junit-jupiter) | 5 min, `forkEvery = 1` | `maxParallelForks = 1` |

- Тесты с тегом `real-llm` **исключены по умолчанию** — включить через `-Ptest.tags="real-llm"`.
- `functionalTest` дополнительно включает тег `fine-tune` через `-Ptest.tags="fine-tune"`.
- `check` зависит от `functionalTest` + `cucumberTest` + `koverThresholdCheck`.
- **380/380 PASS** (согласно `.agents/INDEX.adoc` закрытие EPIC 9, sessions 125-130).

### Фикстура fine-tuning

`buildFineTuningImage` собирает Docker-образ `plantuml-fine-tune:latest`;
`downloadFineTuningModel` загружает `HuggingFaceTB/SmolLM2-135M-Instruct` в
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/`. Оба подключены как
зависимости `functionalTest`.

## Настройка JVM

| Профиль | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC (`-XX:+UseSerialGC`) | `MaxMetaspaceSize=256m` | `maxHeapSize=1g` (cucumber), адаптивно в остальных |
| Все | `-XX:TieredStopAtLevel=1` (быстрый старт) | — | — |
| Все | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` принудительно использует **одного JVM-воркера** (`maxParallelForks = 1`, `forkEvery = 0`) для
максимизации повторного использования WireMock + GradleRunner + sharedProjectDir между вложенными классами.

## Команды сборки

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

Всегда запускайте `./gradlew -q publishToMavenLocal` после изменения исходного кода
плагина — корневой проект потребляет плагин через `mavenLocal`.

## CI-конвейер

`.github/workflows/test.yml` — одиночный job **Build & Test** на `ubuntu-latest`,
JDK 24 (Temurin), `./gradlew build`, таймаут 15 мин. Запускается при push/PR в
`main` / `master`.

`.github/workflows/ci.yml` — дополнительный job `check`, запускаемый `workflow_run`
(после генерации README) и при non-README push, JDK 23,
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`.

## Публикация (NMCP)

Настроено через `com.gradleup.nmcp.settings` (**1.5.0**) в
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

Учётные данные читаются из `~/.gradle/gradle.properties` (`ossrhUsername`, `ossrhPassword`).
Подпись использует `useGpgCmd()`; подписывается только вне CI для non-SNAPSHOT сборок
(`System.getenv("CI") != "true"`).

POM (на **всех** `MavenPublication`) объявляет:
- Apache License 2.0
- разработчик `cccp-education` (cccp.edu@gmail.com)
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- опциональный `<relocation>` при установленном свойстве `relocationGroup`

Опубликованный артефакт: `education.cccp:plantuml-plugin:0.0.1` в Maven Central.

## Статус EPIC

Согласно `.agents/INDEX.adoc`:

| EPIC | Статус |
|------|--------|
| 1 Unit-тесты            | ✅ ЗАВЕРШЕНО |
| 2 Функциональные тесты         | ✅ ЗАВЕРШЕНО |
| 3 Архитектура               | ✅ ЗАВЕРШЕНО |
| 4 Документация              | ✅ ЗАВЕРШЕНО |
| 5 RAG-система                 | ✅ ЗАВЕРШЕНО |
| 6 Пул API-ключей               | ✅ ЗАВЕРШЕНО |
| 7 Dogfooding-плагин          | ✅ ЗАВЕРШЕНО (Phase 1) |
| 8 Миграция AsciiDoc         | ✅ ЗАВЕРШЕНО |
| 9 Диаграмма графа знаний    | ✅ ЗАВЕРШЕНО (380/380 PASS) |
| 10 Интеграция Graphify      | ✅ ЗАВЕРШЕНО |
| 11 Статья в блоге KG + тесты   | ✅ ЗАВЕРШЕНО |
| 12 Консолидация             | ✅ ЗАВЕРШЕНО (Kover 81.19 %) |
| PUB-1 Публикация Maven Central | ✅ ЗАВЕРШЕНО (0.0.1) |
| **13 Multi-provider API Pool** | 🟡 В ПРОЦЕССЕ (sessions 138-141) |
| **PLT-I18N Интернационализация** | 🟠 В ПРОЦЕССЕ (US-0..US-3 ✅, 16/28 pts, session 151) |

## Документация по архитектуре

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — Дизайн компонентов и потоков данных
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — Дорожная карта EPIC
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — Сессии, EPIC, управление
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — Известные проблемы и исправления
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — Внутренности пула
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — Процедуры разработки

## Контрибьютинг

1. Сборка компилируется: `./gradlew build -x test`
2. Локальная перепубликация: `./gradlew -q publishToMavenLocal`
3. Unit-тесты зелёные: `./gradlew test`
4. Покрытие соблюдено: `./gradlew koverThresholdCheck` (≥75 %)
5. Нет утечек `@startuml`/`@enduml` в сгенерированных `.puml`
6. Следовать DDD-соглашениям (value objects в `models.kt`, сервисы в `service/`)
7. Уважать i18n: добавлять ключи во **все** `Messages_*.properties` (10 языков)

См. [CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) и
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc).

## Лицензия

Apache License 2.0 — см. [LICENCE](../LICENCE).

---

_Часть экосистемы CCCP Education — `groupId: education.cccp`._