<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — 消费者指南

> AI 辅助的 Gradle 插件，从自然语言提示生成 PlantUML 图表，集成 RAG（pgvector）、知识图谱渲染器和轮换式 API 密钥池。

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **版本**：`0.0.1` · **组**：`education.cccp` · **插件 ID**：`education.cccp.plantuml`
- **工具链**：Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **构建**：`./gradlew build` · **测试**：`./gradlew test` + `functionalTest` + `cucumberTest`（380/380 PASS）
- **覆盖率**：≥ 75 %（Kover `koverThresholdCheck`，已接入 `check`）

🌐 Languages: [English](README.md) | **中文** | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## 功能简介

`plantuml-gradle` 监视提示目录中的 `.prompt` 文件，将其发送至
LLM（7 个 langchain4j 提供商），验证返回的 PlantUML 语法，渲染 PNG
图像，将有效图表收集到 **pgvector** RAG 索引中，并从
`graphify-out/graph.json` 工作区提取物渲染出 **知识图谱**。

CCCP Education 多插件生态系统的一部分：

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (deterministic)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validate ──► PNG + RAG index
```

该插件消费 `education.cccp.codebase`（`0.0.2`）进行 RAG 索引，并通过
`education.cccp:workspace-bom:0.0.1` 共享 N0 契约。

## 快速开始

### 1. 应用插件

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. 配置扩展（可选）

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "en"
    supportedLanguages = listOf("en", "fr", "es")
}
```

若 `plantuml-context.yml` 缺失或为空，插件将回退至内置默认值
（见 `PlantumlManager.Configuration`）。

### 3. 生成图表

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → KG diagram (no LLM)
./gradlew docs                            # full pipeline: KG + docs + diagrams + validate
```

## 可用任务

| 任务 | 组 | 描述 |
|------|-------|-------------|
| `generatePlantumlDiagrams`      | generate | 处理 PlantUML 提示并借助 LLM 生成图表 |
| `validatePlantumlSyntax`         | verify   | 验证 PlantUML 语法用于调试（`-Pplantuml.diagram=file.puml`） |
| `collectPlantumlIndex`           | collect  | 用收集到的 PlantUML 图表重建 RAG 索引 |
| `generateDiagramDocs`            | generate | 从 Graphify 知识图谱自动生成 PlantUML 文档图表 |
| `generateKnowledgeGraphDiagram`  | generate | 从 `graphify-out/graph.json` 生成知识图谱（确定性，无 LLM） |
| `docs`                           | info     | 完整流水线：KG + 文档 + 图表，由 `validatePlantumlSyntax` 收尾 |

## 扩展 DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "en"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| 属性 | 类型 | 默认值 | 描述 |
|----------|------|---------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | YAML 配置文件路径 |
| `language`            | `Property<String>`      | `"en"`                 | 任务标签的活跃 i18n 语言 |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | 已启用的 UI 语言（支持 10 种） |

## 前置要求

- **Java** 24+（Kotlin 2.3.20 工具链，JDK 序号 24）
- **Gradle** 9.5.1+
- **Docker**（用于 `collectPlantumlIndex` 中的 Testcontainers pgvector）
- 一个 **LLM 端点** — 默认 Ollama `http://localhost:11434`，模型 `smollm:135m`
  （端口 `11434–11436` 全局禁用；在 `11437–11465` 上轮换）

## 构建与测试

```bash
./gradlew build                       # full build (compile + tests)
./gradlew test                        # JUnit5 unit tests
./gradlew functionalTest              # functional tests (GradleRunner + WireMock)
./gradlew cucumberTest               # Cucumber BDD scenarios
./gradlew koverThresholdCheck        # coverage ≥ 75 %
./gradlew publishToMavenLocal        # publish locally
```

启用真实 LLM 测试（默认排除）：

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## 故障排除

| 症状 | 解决方法 |
|---------|-----|
| `No plantuml-context.yml` 警告      | 无害 — 插件使用内置默认值 |
| pgvector 端口 5432 冲突             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| LLM 卡住 / 30 秒测试超时           | 检查 Ollama 是否运行，增大堆（`GRADLE_OPTS=-Xmx2g`） |
| `graphify-out/graph.json not found`     | 先运行 `graphify . --no-viz` 构建知识图谱 |
| API Key Pool is empty                   | 在 `plantuml-context.yml` 中配置 `langchain4j.<provider>.pool` |

详见 [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc)。

## 许可证

Apache License 2.0 — 见 [LICENCE](../LICENCE)。

---

_CCCP Education 生态系统的一部分 — `groupId: education.cccp`。_