<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — 插件内部机制

> `plantuml-plugin` Gradle 插件的开发者与贡献者指南。

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Coverage](https://img.shields.io/static/v1?label=coverage&message=%E2%89%A575%25&color=green)]()
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **版本**：`0.0.1` · **组**：`education.cccp` · **插件 ID**：`education.cccp.plantuml`
- **工具链**：Java 24（序号）· Kotlin 2.3.20 · Gradle 9.5.1
- **构建**：`./gradlew build -x test` · **测试**：`./gradlew test functionalTest cucumberTest` · **覆盖率门禁**：`./gradlew koverThresholdCheck`（≥75%）

🌐 Languages: [English](README.md) | **中文** | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## 模块布局

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

该插件**自食其力（dogfood）**：根项目 `plantuml-gradle/build.gradle.kts` 应用
`id("education.cccp.plantuml")` 并将 `configPath` 指向 `plantuml-context.yml`。

## N0 契约（来自 workspace-bom MEMPHIS）

通过 `implementation(platform("education.cccp:workspace-bom:0.0.1"))` 传递消费：

| 契约 | 制品 | 提供 |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext（RAG 上下文） |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig（API-密钥轮换） |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | Release-notes 契约（跨 borough） |

## 消费者依赖

该插件**消费** `education.cccp.codebase` 版本 `0.0.2`（通过
`build.gradle.kts` 中的 `alias(libs.plugins.codebase)` 应用）：

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

这会将 codebase-gradle 的 RAG 流水线（pgvector 索引、复合上下文）接入
`collectPlantumlIndex`。下游消费者会自动获得 codebase 插件的应用。

## 关键依赖

| 库 | 版本 | 角色 |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 个 LLM 提供商：Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace（+ beta），pgvector, all-minilm-l6-v2 |
| **PlantUML 引擎**    | `1.2026.0`          | `net.sourceforge.plantuml` — 语法验证 + PNG 渲染 |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | Slides/文档流水线（asciidoctor-jvm + gems + slides） |
| **JGit**               | `7.5.0`             | 自动化图表提交（core + ssh + archive） |
| **Arrow KT**           | `2.2.2`             | 函数式核心（core + fx-coroutines + jackson） |
| **Jackson**            | (BOM `2.21.1`)      | YAML 配置（`jackson-dataformat-yaml`，kotlin 模块，jsr310） |
| **Testcontainers PG**  | `1.21.4`            | `pgvector/pgvector` 用于 RAG 集成测试 |
| **docker-java**        | `3.7.0`             | 编程式容器控制 |
| **WireMock**           | `3.9.1`             | 测试中 LLM 端点的 HTTP 模拟 |
| **Kover**              | `0.9.8`             | 覆盖率门禁（≥75 %，`includedSourceSets: main, functionalTest`） |

### Langchain4j 提供商包（`libs.bundles.plantuml-ai`）

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### API-密钥池（`plantuml/apikey/`）

- `ApiKeyPool.kt` — round-robin / least-used 轮换
- `QuotaTracker.kt` / `QuotaResetManager.kt` — 每密钥配额 + 重置
- `QuotaAuditLogger.kt` — 审计日志
- `Provider.kt` — 提供商枚举 + 密钥解析

## 测试矩阵

| 任务 | 范围 | 超时 | 并行度 |
|------|-------|---------|-------------|
| `test`           | JUnit5 单元（排除 `plantuml.scenarios.**`、`PlantUmlPluginFunctionalTests`） | 30 s/类，60 s 全局 | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune（SmolLM2-135M） | 5 min | `(cores/2)` |
| `cucumberTest`   | Cucumber BDD 场景（junit-platform 引擎，排除 junit-jupiter） | 5 min，`forkEvery = 1` | `maxParallelForks = 1` |

- 标记为 `real-llm` 的测试**默认排除** — 用 `-Ptest.tags="real-llm"` 启用。
- `functionalTest` 额外以 `-Ptest.tags="fine-tune"` 启用 `fine-tune` 标签。
- `check` 依赖 `functionalTest` + `cucumberTest` + `koverThresholdCheck`。
- **380/380 PASS**（据 `.agents/INDEX.adoc` EPIC 9 收尾，sessions 125-130）。

### 微调夹具

`buildFineTuningImage` 构建 Docker 镜像 `plantuml-fine-tune:latest`；
`downloadFineTuningModel` 拉取 `HuggingFaceTB/SmolLM2-135M-Instruct` 至
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/`。两者均作为
`functionalTest` 的依赖接入。

## JVM 调优

| 配置 | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC（`-XX:+UseSerialGC`） | `MaxMetaspaceSize=256m` | `maxHeapSize=1g`（cucumber），其余自适应 |
| 全部 | `-XX:TieredStopAtLevel=1`（快速启动） | — | — |
| 全部 | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` 强制**单 JVM worker**（`maxParallelForks = 1`，`forkEvery = 0`），以最大化
WireMock + GradleRunner + sharedProjectDir 在嵌套类间的复用。

## 构建命令

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

修改插件源码后始终运行 `./gradlew -q publishToMavenLocal` —
根项目通过 `mavenLocal` 消费该插件。

## CI 流水线

`.github/workflows/test.yml` — 在 `ubuntu-latest` 上的单个 **Build & Test** job，
JDK 24（Temurin），`./gradlew build`，15 分钟超时。在 push/PR 到
`main` / `master` 时触发。

`.github/workflows/ci.yml` — 补充的 `check` job，由 `workflow_run`
（README 生成之后）及非 README push 触发，JDK 23，
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`。

## 发布（NMCP）

通过 `com.gradleup.nmcp.settings`（**1.5.0**）在
`plantuml-plugin/settings.gradle.kts` 中配置：

```kotlin
nmcpSettings {
    centralPortal {
        username = globalProps.getProperty("ossrhUsername")
        password = globalProps.getProperty("ossrhPassword")
        publishingType = "AUTOMATIC"
    }
}
```

凭据从 `~/.gradle/gradle.properties`（`ossrhUsername`、`ossrhPassword`）读取。
签名使用 `useGpgCmd()`；仅在 CI 外对非 SNAPSHOT 构建签名
（`System.getenv("CI") != "true"`）。

POM（在**所有** `MavenPublication` 上）声明：
- Apache License 2.0
- 开发者 `cccp-education`（cccp.edu@gmail.com）
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- 当设置了 `relocationGroup` 属性时可选 `<relocation>`

已发布制品：`education.cccp:plantuml-plugin:0.0.1`，在 Maven Central。

## EPIC 状态

据 `.agents/INDEX.adoc`：

| EPIC | 状态 |
|------|--------|
| 1 单元测试            | ✅ 终结 |
| 2 功能测试         | ✅ 终结 |
| 3 架构               | ✅ 终结 |
| 4 文档              | ✅ 终结 |
| 5 RAG 系统                 | ✅ 终结 |
| 6 API 密钥池               | ✅ 终结 |
| 7 自食其力插件          | ✅ 终结（Phase 1） |
| 8 AsciiDoc 迁移         | ✅ 终结 |
| 9 知识图谱    | ✅ 终结（380/380 PASS） |
| 10 Graphify 集成      | ✅ 终结 |
| 11 博客文章 KG + 测试   | ✅ 终结 |
| 12 合并             | ✅ 终结（Kover 81.19 %） |
| PUB-1 Maven Central 发布 | ✅ 终结（0.0.1） |
| **13 多提供商 API 池** | 🟡 进行中（sessions 138-141） |
| **PLT-I18N 国际化** | 🟠 进行中（US-0..US-3 ✅，16/28 pts，session 151） |

## 架构文档

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — 组件与数据流设计
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — EPIC 路线图
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — 会话、EPIC、治理
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — 已知问题与修复
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — 池内部机制
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — 开发流程

## 贡献

1. 构建可编译：`./gradlew build -x test`
2. 本地重新发布：`./gradlew -q publishToMavenLocal`
3. 单元测试通过：`./gradlew test`
4. 覆盖率达标：`./gradlew koverThresholdCheck`（≥75 %）
5. 生成的 `.puml` 无 `@startuml`/`@enduml` 泄漏
6. 遵循 DDD 约定（值对象在 `models.kt`，服务在 `service/`）
7. 尊重 i18n：在**所有** `Messages_*.properties` 中添加键（10 种语言）

见 [CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) 和
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc)。

## 许可证

Apache License 2.0 — 见 [LICENCE](../LICENCE)。

---

_CCCP Education 生态系统的一部分 — `groupId: education.cccp`。_