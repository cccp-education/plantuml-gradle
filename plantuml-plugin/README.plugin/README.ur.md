<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — پلگ ان کے اندرونی معاملات

> Gradle پلگ ان `plantuml-plugin` کے لیے ڈویلپر اور معاون کا گائیڈ۔

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Coverage](https://img.shields.io/static/v1?label=coverage&message=%E2%89%A575%25&color=green)]()
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **ورژن**: `0.0.1` · **گروہ**: `education.cccp` · **پلگ ان ID**: `education.cccp.plantuml`
- **ٹول چین**: Java 24 (ordinal) · Kotlin 2.3.20 · Gradle 9.5.1
- **بلڈ**: `./gradlew build -x test` · **ٹیسٹ**: `./gradlew test functionalTest cucumberTest` · **کوریج گیٹ**: `./gradlew koverThresholdCheck` (≥75%)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | **اردو**

---

## ماڈیول کا ترتیب

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

پلگ ان **خود استعمال کرتا ہے (dogfood)**: جڑ پروجیکٹ `plantuml-gradle/build.gradle.kts`
`id("education.cccp.plantuml")` لاگو کرتا ہے اور `configPath` کو `plantuml-context.yml` کی طرف اشارہ کرتا ہے۔

## N0 معاہدے (workspace-bom MEMPHIS سے)

`implementation(platform("education.cccp:workspace-bom:0.0.1"))` کے ذریعے transitively استعمال شدہ:

| معاہدہ | آرٹیفیکٹ | فراہم کرتا ہے |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext (RAG سیاق) |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig (API-کلید گھومنا) |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | Release-notes معاہدے (cross-borough) |

## صارف کی انحصار

پلگ ان `education.cccp.codebase` ورژن `0.0.2` **استعمال** کرتا ہے (`build.gradle.kts` میں
`alias(libs.plugins.codebase)` کے ذریعے لاگو):

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

یہ codebase-gradle کے RAG پائپ لائن (pgvector انڈیکسنگ، ملی سیاق) کو
`collectPlantumlIndex` میں جوڑتا ہے۔ ڈاؤن سٹریم صارفین کو codebase پلگ ان خود بخود لاگو ملتا ہے۔

## اہم انحصارات

| لائبریری | ورژن | کردار |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 LLM فراہم کنندگان: Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace (+ beta), pgvector, all-minilm-l6-v2 |
| **PlantUML انجن**    | `1.2026.0`          | `net.sourceforge.plantuml` — نحوی صورت کی تصدیق + PNG رینڈر |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | Slides/docs پائپ لائن (asciidoctor-jvm + gems + slides) |
| **JGit**               | `7.5.0`             | خودکار ڈایاگرام commits (core + ssh + archive) |
| **Arrow KT**           | `2.2.2`             | فنکشنل کور (core + fx-coroutines + jackson) |
| **Jackson**            | (BOM `2.21.1`)      | YAML کنفیگ (`jackson-dataformat-yaml`, kotlin ماڈیول, jsr310) |
| **Testcontainers PG**  | `1.21.4`            | RAG انضمام ٹیسٹس کے لیے `pgvector/pgvector` |
| **docker-java**        | `3.7.0`             | پروگرامیٹک کنٹینر کنٹرول |
| **WireMock**           | `3.9.1`             | ٹیسٹس میں LLM اینڈ پوائنٹس کی HTTP mocking |
| **Kover**              | `0.9.8`             | کوریج گیٹ (≥75 %, `includedSourceSets: main, functionalTest`) |

### Langchain4j فراہم کنندگان کا بنڈل (`libs.bundles.plantuml-ai`)

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### API-کلید پول (`plantuml/apikey/`)

- `ApiKeyPool.kt` — round-robin / least-used گھومنا
- `QuotaTracker.kt` / `QuotaResetManager.kt` — فی-کلید کوٹہ + ری سیٹ
- `QuotaAuditLogger.kt` — آڈٹ جرنل
- `Provider.kt` — فراہم کنندہ enum + کلید حل

## ٹیسٹ میٹرکس

| کام | دائرہ | ٹائم آؤٹ | متوازیت |
|------|-------|---------|-------------|
| `test`           | JUnit5 unit (excludes `plantuml.scenarios.**`, `PlantUmlPluginFunctionalTests`) | 30 s/class, 60 s global | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune (SmolLM2-135M) | 5 min | `(cores/2)` |
| `cucumberTest`   | Cucumber BDD scenarios (JUnit-platform engine, excludes junit-jupiter) | 5 min, `forkEvery = 1` | `maxParallelForks = 1` |

- `real-llm` ٹیگ شدہ ٹیسٹس **بذات خود خارج** ہیں — `-Ptest.tags="real-llm"` سے فعال کریں۔
- `functionalTest` اضافی طور پر `-Ptest.tags="fine-tune"` کے ساتھ `fine-tune` ٹیگ فعال کرتا ہے۔
- `check` `functionalTest` + `cucumberTest` + `koverThresholdCheck` پر منحصر ہے۔
- **380/380 PASS** (`.agents/INDEX.adoc` EPIC 9 اختتام کے مطابق، sessions 125-130)۔

### Fine-tuning فکسچر

`buildFineTuningImage` Docker امیج `plantuml-fine-tune:latest` بناتا ہے؛
`downloadFineTuningModel` `HuggingFaceTB/SmolLM2-135M-Instruct` کو
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/` میں لاتا ہے۔ دونوں
`functionalTest` کی انحصارات کے طور پر جڑے ہیں۔

## JVM tuning

| پروفائل | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC (`-XX:+UseSerialGC`) | `MaxMetaspaceSize=256m` | `maxHeapSize=1g` (cucumber)، باقی میں ڈھیلا |
| سب | `-XX:TieredStopAtLevel=1` (تیز اسٹارٹ اپ) | — | — |
| سب | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` ایک **واحد JVM ورکر** (`maxParallelForks = 1`, `forkEvery = 0`) پر زور دیتا ہے تاکہ
nested کلاسز کے درمیان WireMock + GradleRunner + sharedProjectDir دوبارہ استعمال زیادہ سے زیادہ ہو۔

## بلڈ کمانڈز

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

پلگ ان سورس میں ترمیم کے بعد ہمیشہ `./gradlew -q publishToMavenLocal` چلائیں —
جڑ پروجیکٹ `mavenLocal` کے ذریعے پلگ ان استعمال کرتا ہے۔

## CI پائپ لائن

`.github/workflows/test.yml` — `ubuntu-latest` پر ایک واحد **Build & Test** job،
JDK 24 (Temurin)، `./gradlew build`، 15 منٹ ٹائم آؤٹ۔ `main` / `master` پر
push/PR پر ٹرگر ہوتا ہے۔

`.github/workflows/ci.yml` — معاون `check` job، `workflow_run`
(README جنریشن کے بعد) اور non-README push پر ٹرگر، JDK 23،
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`۔

## اشاعت (NMCP)

`com.gradleup.nmcp.settings` (**1.5.0**) کے ذریعے
`plantuml-plugin/settings.gradle.kts` میں تشکیل:

```kotlin
nmcpSettings {
    centralPortal {
        username = globalProps.getProperty("ossrhUsername")
        password = globalProps.getProperty("ossrhPassword")
        publishingType = "AUTOMATIC"
    }
}
```

اسناد `~/.gradle/gradle.properties` (`ossrhUsername`, `ossrhPassword`) سے پڑھے جاتے ہیں۔
دستخط `useGpgCmd()` استعمال کرتا ہے؛ صرف CI کے باہر non-SNAPSHOT بلڈز کے لیے دستخط ہوتے ہیں
(`System.getenv("CI") != "true"`)۔

POM (**سب** `MavenPublication` پر) کا اعلان:
- Apache License 2.0
- ڈویلپر `cccp-education` (cccp.edu@gmail.com)
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- `relocationGroup` پراپرٹی سیٹ ہونے پر اختیاری `<relocation>`

شائع شدہ آرٹیفیکٹ: `education.cccp:plantuml-plugin:0.0.1` Maven Central پر۔

## EPIC کی حالت

`.agents/INDEX.adoc` کے مطابق:

| EPIC | حالت |
|------|--------|
| 1 یونٹ ٹیسٹس            | ✅ مکمل |
| 2 فنکشنل ٹیسٹس         | ✅ مکمل |
| 3 آرکیٹیکچر               | ✅ مکمل |
| 4 دستاویزات              | ✅ مکمل |
| 5 RAG سسٹم                 | ✅ مکمل |
| 6 API Key Pool               | ✅ مکمل |
| 7 Dogfooding Plugin          | ✅ مکمل (Phase 1) |
| 8 AsciiDoc مائیگریشن         | ✅ مکمل |
| 9 Knowledge Graph Diagram    | ✅ مکمل (380/380 PASS) |
| 10 Graphify انضمام      | ✅ مکمل |
| 11 بلاگ مضمون KG + ٹیسٹس   | ✅ مکمل |
| 12 یکجائتی             | ✅ مکمل (Kover 81.19 %) |
| PUB-1 Maven Central اشاعت | ✅ مکمل (0.0.1) |
| **13 Multi-provider API Pool** | 🟡 جاری (Sessions 138-141) |
| **PLT-I18N بین الاقوامییت** | 🟠 جاری (US-0..US-3 ✅, 16/28 pts, session 151) |

## آرکیٹیکچر دستاویزات

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — کمپوننٹ اور ڈیٹا-فلو ڈیزائن
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — EPIC روڈ میپ
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — سیشنز، EPICs، گورننس
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — معروف مسائل اور اصلاحات
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — پول کے اندرونی معاملات
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — dev طریقہ کار

## معاونت

1. بلڈ کمپائل ہو: `./gradlew build -x test`
2. مقامی دوبارہ اشاعت: `./gradlew -q publishToMavenLocal`
3. یونٹ ٹیسٹس سبز: `./gradlew test`
4. کوریج کی پاسداری: `./gradlew koverThresholdCheck` (≥75 %)
5. جنریٹڈ `.puml` میں کوئی `@startuml`/`@enduml` لیک نہیں
6. DDD کنونشنز پر عمل (value objects `models.kt` میں، خدمات `service/` میں)
7. i18n کا احترام: **سب** `Messages_*.properties` میں کلیدیں شامل کریں (10 زبانیں)

[CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) اور
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc) دیکھیں۔

## لائسنس

Apache License 2.0 — [LICENCE](../LICENCE) دیکھیں۔

---

_CCCP Education ماحولیاتی نظام کا حصہ — `groupId: education.cccp`۔_