<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — Guia do Consumidor

> Plugin do Gradle assistido por IA para gerar diagramas PlantUML a partir de prompts em linguagem natural, com RAG (pgvector), um renderizador de grafo de conhecimento e um pool rotativo de chaves API.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **Versão**: `0.0.1` · **Grupo**: `education.cccp` · **ID do plugin**: `education.cccp.plantuml`
- **Toolchain**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **Build**: `./gradlew build` · **Testes**: `./gradlew test` + `functionalTest` + `cucumberTest` (380/380 PASS)
- **Cobertura**: ≥ 75 % (Kover `koverThresholdCheck`, integrado em `check`)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | **Português** | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## O que faz

`plantuml-gradle` monitora arquivos `.prompt` num diretório de prompts, envia-os a um
LLM (7 provedores langchain4j), valida a sintaxe PlantUML retornada, renderiza imagens
PNG, coleta diagramas válidos num índice RAG **pgvector** e renderiza um
**diagrama de grafo de conhecimento** a partir de um extrato `graphify-out/graph.json` do workspace.

Parte do ecossistema multi-plugin CCCP Education:

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (deterministic)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validate ──► PNG + RAG index
```

O plugin consome `education.cccp.codebase` (`0.0.2`) para indexação RAG e partilha
contratos N0 via `education.cccp:workspace-bom:0.0.1`.

## Início rápido

### 1. Aplicar o plugin

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. Configurar a extensão (opcional)

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "en"
    supportedLanguages = listOf("en", "fr", "es")
}
```

Se `plantuml-context.yml` estiver ausente ou vazio, o plugin recorre aos padrões
integrados (ver `PlantumlManager.Configuration`).

### 3. Gerar diagramas

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → KG diagram (no LLM)
./gradlew docs                            # full pipeline: KG + docs + diagrams + validate
```

## Tarefas disponíveis

| Tarefa | Grupo | Descrição |
|------|-------|-------------|
| `generatePlantumlDiagrams`      | generate | Processa prompts PlantUML e gera diagramas com assistência LLM |
| `validatePlantumlSyntax`         | verify   | Valida a sintaxe PlantUML para depuração (`-Pplantuml.diagram=file.puml`) |
| `collectPlantumlIndex`           | collect  | Reconstrói o índice RAG com os diagramas PlantUML coletados |
| `generateDiagramDocs`            | generate | Gera automaticamente diagramas PlantUML de documentação a partir do grafo de conhecimento Graphify |
| `generateKnowledgeGraphDiagram`  | generate | Gera um diagrama de grafo de conhecimento a partir de `graphify-out/graph.json` (determinístico, sem LLM) |
| `docs`                           | info     | Pipeline completo: KG + docs + diagramas, finalizado por `validatePlantumlSyntax` |

## Extensão DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "en"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| Propriedade | Tipo | Padrão | Descrição |
|----------|------|---------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | Caminho do ficheiro de configuração YAML |
| `language`            | `Property<String>`      | `"en"`                 | Idioma i18n ativo para os rótulos de tarefas |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | Idiomas de UI ativados (10 suportados) |

## Pré-requisitos

- **Java** 24+ (toolchain Kotlin 2.3.20, ordinal JDK 24)
- **Gradle** 9.5.1+
- **Docker** (para Testcontainers pgvector em `collectPlantumlIndex`)
- Um **endpoint LLM** — padrão Ollama `http://localhost:11434`, modelo `smollm:135m`
  (portas `11434–11436` proibidas globalmente; rodar em `11437–11465`)

## Build e testes

```bash
./gradlew build                       # full build (compile + tests)
./gradlew test                        # JUnit5 unit tests
./gradlew functionalTest              # functional tests (GradleRunner + WireMock)
./gradlew cucumberTest               # Cucumber BDD scenarios
./gradlew koverThresholdCheck        # coverage ≥ 75 %
./gradlew publishToMavenLocal        # publish locally
```

Para ativar testes com LLM real (excluídos por padrão):

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## Resolução de problemas

| Sintoma | Solução |
|---------|-----|
| Aviso `No plantuml-context.yml`      | Inofensivo — o plugin usa padrões integrados |
| Conflito de porta pgvector 5432             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| LLM trava / timeout de teste 30 s           | verificar se Ollama está a correr, aumentar o heap (`GRADLE_OPTS=-Xmx2g`) |
| `graphify-out/graph.json not found`     | executar primeiro `graphify . --no-viz` para construir o grafo de conhecimento |
| API Key Pool is empty                   | configurar `langchain4j.<provider>.pool` em `plantuml-context.yml` |

Ver [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) para detalhes.

## Licença

Apache License 2.0 — ver [LICENCE](../LICENCE).

---

_Parte do ecossistema CCCP Education — `groupId: education.cccp`._