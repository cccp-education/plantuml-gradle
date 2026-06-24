<!-- master source — other languages are translations of this file -->
# plantuml-gradle — Consumer Guide

> AI-assisted Gradle plugin for generating PlantUML diagrams from natural-language prompts, with RAG (pgvector), a knowledge-graph renderer, and a rotating API-key pool.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **Version**: `0.0.1` · **Group**: `education.cccp` · **Plugin ID**: `education.cccp.plantuml`
- **Toolchain**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **Build**: `./gradlew build` · **Tests**: `./gradlew test` + `functionalTest` + `cucumberTest` (380/380 PASS)
- **Coverage**: ≥ 75 % (Kover `koverThresholdCheck`, wired into `check`)

🌐 Languages: **EN** | [中文](README.consommateurs/README.zh.md) | [हिन्दी](README.consommateurs/README.hi.md) | [Español](README.consommateurs/README.es.md) | [Français](README.consommateurs/README.fr.md) | [العربية](README.consommateurs/README.ar.md) | [বাংলা](README.consommateurs/README.bn.md) | [Português](README.consommateurs/README.pt.md) | [Русский](README.consommateurs/README.ru.md) | [اردو](README.consommateurs/README.ur.md)

---

## What it does

`plantuml-gradle` watches `.prompt` files in a prompts directory, sends them to an
LLM (7 langchain4j providers), validates the returned PlantUML syntax, renders PNG
images, collects valid diagrams into a **pgvector** RAG index, and renders a
**knowledge-graph diagram** from a `graphify-out/graph.json` workspace extract.

Part of the CCCP Education multi-plugin ecosystem:

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (deterministic)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validate ──► PNG + RAG index
```

The plugin consumes `education.cccp.codebase` (`0.0.2`) for RAG indexing and shares
N0 contracts via `education.cccp:workspace-bom:0.0.1`.

## Quick Start

### 1. Apply the plugin

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. Configure the extension (optional)

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "en"
    supportedLanguages = listOf("en", "fr", "es")
}
```

If `plantuml-context.yml` is absent or empty, the plugin falls back to built-in
defaults (see `PlantumlManager.Configuration`).

### 3. Generate diagrams

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → KG diagram (no LLM)
./gradlew docs                            # full pipeline: KG + docs + diagrams + validate
```

## Available tasks

| Task | Group | Description |
|------|-------|-------------|
| `generatePlantumlDiagrams`      | generate | Process PlantUML prompts and generate diagrams with LLM assistance |
| `validatePlantumlSyntax`         | verify   | Validates PlantUML syntax for debugging (`-Pplantuml.diagram=file.puml`) |
| `collectPlantumlIndex`           | collect  | Rebuilds the RAG index with collected PlantUML diagrams |
| `generateDiagramDocs`            | generate | Auto-generate PlantUML documentation diagrams from Graphify knowledge graph |
| `generateKnowledgeGraphDiagram`  | generate | Generate Knowledge Graph diagram from `graphify-out/graph.json` (deterministic, no LLM) |
| `docs`                           | info     | Full pipeline: KG + docs + diagrams, finalized by `validatePlantumlSyntax` |

## Extension DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "en"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | Path to the YAML configuration file |
| `language`            | `Property<String>`      | `"en"`                 | Active i18n language for task labels |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | Enabled UI languages (10 supported) |

## Prerequisites

- **Java** 24+ (Kotlin 2.3.20 toolchain, JDK ordinal 24)
- **Gradle** 9.5.1+
- **Docker** (for Testcontainers pgvector in `collectPlantumlIndex`)
- An **LLM endpoint** — default Ollama `http://localhost:11434`, model `smollm:135m`
  (ports `11434–11436` are forbidden globally; rotate over `11437–11465`)

## Build & test

```bash
./gradlew build                       # full build (compile + tests)
./gradlew test                        # JUnit5 unit tests
./gradlew functionalTest              # functional tests (GradleRunner + WireMock)
./gradlew cucumberTest               # Cucumber BDD scenarios
./gradlew koverThresholdCheck        # coverage ≥ 75 %
./gradlew publishToMavenLocal        # publish locally
```

To enable real-LLM tests (excluded by default):

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `No plantuml-context.yml` warning      | Harmless — plugin uses built-in defaults |
| pgvector port 5432 conflict             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| LLM hangs / 30 s test timeout           | check Ollama is running, increase heap (`GRADLE_OPTS=-Xmx2g`) |
| `graphify-out/graph.json not found`     | run `graphify . --no-viz` first to build the knowledge graph |
| API Key Pool is empty                   | configure `langchain4j.<provider>.pool` in `plantuml-context.yml` |

See [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) for details.

## License

Apache License 2.0 — see [LICENCE](../LICENCE).

---

_Part of the CCCP Education ecosystem — `groupId: education.cccp`._