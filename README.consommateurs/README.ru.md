<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — Руководство потребителя

> Плагин Gradle с поддержкой ИИ для генерации диаграмм PlantUML из подсказок на естественном языке, с RAG (pgvector), рендерером графа знаний и ротацией пула API-ключей.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **Версия**: `0.0.1` · **Группа**: `education.cccp` · **ID плагина**: `education.cccp.plantuml`
- **Набор инструментов**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **Сборка**: `./gradlew build` · **Тесты**: `./gradlew test` + `functionalTest` + `cucumberTest` (380/380 PASS)
- **Покрытие**: ≥ 75 % (Kover `koverThresholdCheck`, подключён к `check`)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | **Русский** | [اردو](README.ur.md)

---

## Что делает

`plantuml-gradle` отслеживает файлы `.prompt` в каталоге подсказок, отправляет их в
LLM (7 провайдеров langchain4j), проверяет возвращённый синтаксис PlantUML, рендерит PNG
изображения, собирает корректные диаграммы в индекс RAG **pgvector** и рендерит
**диаграмму графа знаний** из извлечения `graphify-out/graph.json` рабочего пространства.

Часть мультиплагинной экосистемы CCCP Education:

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (deterministic)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validate ──► PNG + RAG index
```

Плагин потребляет `education.cccp.codebase` (`0.0.2`) для индексации RAG и разделяет
контракты N0 через `education.cccp:workspace-bom:0.0.1`.

## Быстрый старт

### 1. Применить плагин

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. Настроить расширение (необязательно)

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "en"
    supportedLanguages = listOf("en", "fr", "es")
}
```

Если `plantuml-context.yml` отсутствует или пуст, плагин откатывается к встроенным
значениям по умолчанию (см. `PlantumlManager.Configuration`).

### 3. Сгенерировать диаграммы

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → KG diagram (no LLM)
./gradlew docs                            # full pipeline: KG + docs + diagrams + validate
```

## Доступные задачи

| Задача | Группа | Описание |
|------|-------|-------------|
| `generatePlantumlDiagrams`      | generate | Обрабатывает подсказки PlantUML и генерирует диаграммы с помощью LLM |
| `validatePlantumlSyntax`         | verify   | Проверяет синтаксис PlantUML для отладки (`-Pplantuml.diagram=file.puml`) |
| `collectPlantumlIndex`           | collect  | Перестраивает индекс RAG из собранных диаграмм PlantUML |
| `generateDiagramDocs`            | generate | Автоматически генерирует диаграммы PlantUML для документации из графа знаний Graphify |
| `generateKnowledgeGraphDiagram`  | generate | Генерирует диаграмму графа знаний из `graphify-out/graph.json` (детерминированно, без LLM) |
| `docs`                           | info     | Полный конвейер: KG + документы + диаграммы, завершается `validatePlantumlSyntax` |

## Расширение DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "en"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| Свойство | Тип | По умолчанию | Описание |
|----------|------|---------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | Путь к YAML-файлу конфигурации |
| `language`            | `Property<String>`      | `"en"`                 | Активный язык i18n для меток задач |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | Включённые языки UI (10 поддерживаются) |

## Требования

- **Java** 24+ (toolchain Kotlin 2.3.20, порядковый номер JDK 24)
- **Gradle** 9.5.1+
- **Docker** (для Testcontainers pgvector в `collectPlantumlIndex`)
- **LLM-эндпоинт** — по умолчанию Ollama `http://localhost:11434`, модель `smollm:135m`
  (порты `11434–11436` запрещены глобально; ротация по `11437–11465`)

## Сборка и тесты

```bash
./gradlew build                       # full build (compile + tests)
./gradlew test                        # JUnit5 unit tests
./gradlew functionalTest              # functional tests (GradleRunner + WireMock)
./gradlew cucumberTest               # Cucumber BDD scenarios
./gradlew koverThresholdCheck        # coverage ≥ 75 %
./gradlew publishToMavenLocal        # publish locally
```

Чтобы включить тесты с реальным LLM (исключены по умолчанию):

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## Устранение неполадок

| Симптом | Решение |
|---------|-----|
| Предупреждение `No plantuml-context.yml`      | Безопасно — плагин использует встроенные значения по умолчанию |
| Конфликт порта pgvector 5432             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| LLM зависает / таймаут теста 30 с           | проверьте, что Ollama запущен, увеличьте heap (`GRADLE_OPTS=-Xmx2g`) |
| `graphify-out/graph.json not found`     | сначала выполните `graphify . --no-viz` для построения графа знаний |
| API Key Pool is empty                   | настройте `langchain4j.<provider>.pool` в `plantuml-context.yml` |

Подробнее см. [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc).

## Лицензия

Apache License 2.0 — см. [LICENCE](../LICENCE).

---

_Часть экосистемы CCCP Education — `groupId: education.cccp`._