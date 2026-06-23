<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — Guía del Consumidor

> Plugin de Gradle asistido por IA para generar diagramas PlantUML a partir de prompts en lenguaje natural, con RAG (pgvector), un renderizador de grafos de conocimiento y un pool rotativo de claves API.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **Versión**: `0.0.1` · **Grupo**: `education.cccp` · **ID del plugin**: `education.cccp.plantuml`
- **Toolchain**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **Build**: `./gradlew build` · **Tests**: `./gradlew test` + `functionalTest` + `cucumberTest` (380/380 PASS)
- **Cobertura**: ≥ 75 % (Kover `koverThresholdCheck`, integrado en `check`)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | **Español** | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## Qué hace

`plantuml-gradle` vigila los archivos `.prompt` en un directorio de prompts, los envía a un
LLM (7 proveedores langchain4j), valida la sintaxis PlantUML devuelta, renderiza imágenes
PNG, recolecta los diagramas válidos en un índice RAG **pgvector**, y renderiza un
**diagrama de grafo de conocimiento** a partir de un extracto `graphify-out/graph.json` del workspace.

Parte del ecosistema multi-plugin de CCCP Education:

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (deterministic)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validate ──► PNG + RAG index
```

El plugin consume `education.cccp.codebase` (`0.0.2`) para la indexación RAG y comparte
los contratos N0 vía `education.cccp:workspace-bom:0.0.1`.

## Inicio rápido

### 1. Aplicar el plugin

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. Configurar la extensión (opcional)

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "en"
    supportedLanguages = listOf("en", "fr", "es")
}
```

Si `plantuml-context.yml` está ausente o vacío, el plugin recurre a los valores por defecto
integrados (ver `PlantumlManager.Configuration`).

### 3. Generar diagramas

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → KG diagram (no LLM)
./gradlew docs                            # full pipeline: KG + docs + diagrams + validate
```

## Tareas disponibles

| Tarea | Grupo | Descripción |
|------|-------|-------------|
| `generatePlantumlDiagrams`      | generate | Procesa los prompts PlantUML y genera diagramas con asistencia LLM |
| `validatePlantumlSyntax`         | verify   | Valida la sintaxis PlantUML para depuración (`-Pplantuml.diagram=file.puml`) |
| `collectPlantumlIndex`           | collect  | Reconstruye el índice RAG con los diagramas PlantUML recolectados |
| `generateDiagramDocs`            | generate | Genera automáticamente diagramas PlantUML de documentación desde el grafo de conocimiento Graphify |
| `generateKnowledgeGraphDiagram`  | generate | Genera un diagrama de grafo de conocimiento desde `graphify-out/graph.json` (determinista, sin LLM) |
| `docs`                           | info     | Pipeline completo: KG + docs + diagramas, finalizado por `validatePlantumlSyntax` |

## Extensión DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "en"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| Propiedad | Tipo | Por defecto | Descripción |
|----------|------|---------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | Ruta al archivo de configuración YAML |
| `language`            | `Property<String>`      | `"en"`                 | Idioma i18n activo para las etiquetas de tareas |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | Idiomas de UI habilitados (10 soportados) |

## Requisitos previos

- **Java** 24+ (toolchain Kotlin 2.3.20, ordinal JDK 24)
- **Gradle** 9.5.1+
- **Docker** (para Testcontainers pgvector en `collectPlantumlIndex`)
- Un **endpoint LLM** — por defecto Ollama `http://localhost:11434`, modelo `smollm:135m`
  (los puertos `11434–11436` están prohibidos globalmente; rotar sobre `11437–11465`)

## Build y test

```bash
./gradlew build                       # full build (compile + tests)
./gradlew test                        # JUnit5 unit tests
./gradlew functionalTest              # functional tests (GradleRunner + WireMock)
./gradlew cucumberTest               # Cucumber BDD scenarios
./gradlew koverThresholdCheck        # coverage ≥ 75 %
./gradlew publishToMavenLocal        # publish locally
```

Para habilitar los tests con LLM real (excluidos por defecto):

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## Solución de problemas

| Síntoma | Solución |
|---------|-----|
| Advertencia `No plantuml-context.yml`      | Inofensiva — el plugin usa los valores por defecto integrados |
| Conflicto puerto pgvector 5432             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| LLM se cuelga / timeout de test 30 s       | comprobar que Ollama está en ejecución, aumentar el heap (`GRADLE_OPTS=-Xmx2g`) |
| `graphify-out/graph.json not found`     | ejecutar primero `graphify . --no-viz` para construir el grafo de conocimiento |
| API Key Pool is empty                   | configurar `langchain4j.<provider>.pool` en `plantuml-context.yml` |

Ver [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) para más detalles.

## Licencia

Apache License 2.0 — ver [LICENCE](../LICENCE).

---

_Parte del ecosistema CCCP Education — `groupId: education.cccp`._