<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — প্লাগইন অভ্যন্তরীণ

> `plantuml-plugin` Gradle প্লাগইনের জন্য ডেভেলপার ও অবদানকারী গাইড।

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Coverage](https://img.shields.io/static/v1?label=coverage&message=%E2%89%A575%25&color=green)]()
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **সংস্করণ**: `0.0.1` · **গোষ্ঠী**: `education.cccp` · **প্লাগইন ID**: `education.cccp.plantuml`
- **টুলচেইন**: Java 24 (ordinal) · Kotlin 2.3.20 · Gradle 9.5.1
- **বিল্ড**: `./gradlew build -x test` · **পরীক্ষা**: `./gradlew test functionalTest cucumberTest` · **কভারেজ গেট**: `./gradlew koverThresholdCheck` (≥75%)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | **বাংলা** | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## মডিউল বিন্যাস

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

প্লাগইনটি **নিজে নিজে dogfood করে**: মূল প্রোজেক্ট `plantuml-gradle/build.gradle.kts`
`id("education.cccp.plantuml")` প্রয়োগ করে এবং `configPath` কে `plantuml-context.yml` এ নির্দেশ করে।

## N0 চুক্তি (workspace-bom MEMPHIS থেকে)

`implementation(platform("education.cccp:workspace-bom:0.0.1"))` এর মাধ্যমে transitively গৃহীত:

| চুক্তি | আর্টিফ্যাক্ট | প্রদান করে |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext (RAG প্রসঙ্গ) |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig (API-কী ঘূর্ণন) |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | Release-notes চুক্তি (cross-borough) |

## ভোক্তা নির্ভরতা

প্লাগইনটি `education.cccp.codebase` সংস্করণ `0.0.2` **গ্রহণ** করে (`build.gradle.kts`-এ
`alias(libs.plugins.codebase)` এর মাধ্যমে প্রয়োগ):

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

এটি codebase-gradle RAG পাইপলাইন (pgvector সূচকীকরণ, যৌগিক প্রসঙ্গ) কে
`collectPlantumlIndex`-এ যুক্ত করে। ডাউনস্ট্রিম ভোক্তারা স্বয়ংক্রিয়ভাবে codebase প্লাগইন প্রয়োগ পান।

## মূল নির্ভরতাসমূহ

| লাইব্রেরি | সংস্করণ | ভূমিকা |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 LLM প্রদানকারী: Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace (+ beta), pgvector, all-minilm-l6-v2 |
| **PlantUML ইঞ্জিন**    | `1.2026.0`          | `net.sourceforge.plantuml` — সিনট্যাক্স যাচাই + PNG রেন্ডার |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | Slides/docs পাইপলাইন (asciidoctor-jvm + gems + slides) |
| **JGit**               | `7.5.0`             | স্বয়ংক্রিয় ডায়াগ্রাম কমিট (core + ssh + archive) |
| **Arrow KT**           | `2.2.2`             | ফাংশনাল কোর (core + fx-coroutines + jackson) |
| **Jackson**            | (BOM `2.21.1`)      | YAML কনফিগ (`jackson-dataformat-yaml`, kotlin মডিউল, jsr310) |
| **Testcontainers PG**  | `1.21.4`            | RAG ইন্টিগ্রেশন পরীক্ষার জন্য `pgvector/pgvector` |
| **docker-java**        | `3.7.0`             | প্রোগ্রামেটিক কন্টেইনার নিয়ন্ত্রণ |
| **WireMock**           | `3.9.1`             | পরীক্ষায় LLM এন্ডপয়েন্টের HTTP মকিং |
| **Kover**              | `0.9.8`             | কভারেজ গেট (≥75 %, `includedSourceSets: main, functionalTest`) |

### Langchain4j প্রদানকারী বান্ডল (`libs.bundles.plantuml-ai`)

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### API-কী পুল (`plantuml/apikey/`)

- `ApiKeyPool.kt` — round-robin / least-used ঘূর্ণন
- `QuotaTracker.kt` / `QuotaResetManager.kt` — প্রতি-কী কোটা + রিসেট
- `QuotaAuditLogger.kt` — অডিট জার্নাল
- `Provider.kt` — প্রদানকারী enum + কী নির্ধারণ

## পরীক্ষা ম্যাট্রিক্স

| কার্য | পরিসর | টাইমআউট | সমান্তরালতা |
|------|-------|---------|-------------|
| `test`           | JUnit5 unit (excludes `plantuml.scenarios.**`, `PlantUmlPluginFunctionalTests`) | 30 s/class, 60 s global | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune (SmolLM2-135M) | 5 min | `(cores/2)` |
| `cucumberTest`   | Cucumber BDD scenarios (JUnit-platform engine, excludes junit-jupiter) | 5 min, `forkEvery = 1` | `maxParallelForks = 1` |

- `real-llm` ট্যাগ করা পরীক্ষাগুলি **ডিফল্টরূপে বাদ দেওয়া** — `-Ptest.tags="real-llm"` দিয়ে সক্ষম করুন।
- `functionalTest` অতিরিক্তভাবে `-Ptest.tags="fine-tune"` দিয়ে `fine-tune` ট্যাগ সক্ষম করে।
- `check` `functionalTest` + `cucumberTest` + `koverThresholdCheck`-এর উপর নির্ভর করে।
- **380/380 PASS** (`.agents/INDEX.adoc` EPIC 9 সমাপ্তি অনুসারে, sessions 125-130)।

### ফাইন-টিউনিং ফিক্সচার

`buildFineTuningImage` Docker ইমেজ `plantuml-fine-tune:latest` তৈরি করে;
`downloadFineTuningModel` `HuggingFaceTB/SmolLM2-135M-Instruct` কে
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/`-এ আনে। উভয়ই
`functionalTest`-এর নির্ভরতা হিসেবে যুক্ত।

## JVM টিউনিং

| প্রোফাইল | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC (`-XX:+UseSerialGC`) | `MaxMetaspaceSize=256m` | `maxHeapSize=1g` (cucumber), অন্যত্র অভিযোজী |
| সব | `-XX:TieredStopAtLevel=1` (দ্রুত স্টার্টআপ) | — | — |
| সব | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` একটি **একক JVM ওয়ার্কার** (`maxParallelForks = 1`, `forkEvery = 0`) জোর করে যাতে
নেস্টেড ক্লাসের মধ্যে WireMock + GradleRunner + sharedProjectDir পুনঃব্যবহার সর্বোচ্চ হয়।

## বিল্ড কমান্ড

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

প্লাগইন সোর্স পরিবর্তনের পরে সর্বদা `./gradlew -q publishToMavenLocal` চালান —
মূল প্রোজেক্ট `mavenLocal`-এর মাধ্যমে প্লাগইন গ্রহণ করে।

## CI পাইপলাইন

`.github/workflows/test.yml` — `ubuntu-latest`-এ একটি একক **Build & Test** job,
JDK 24 (Temurin), `./gradlew build`, 15 মিনিট টাইমআউট। `main` / `master`-এ
push/PR-এ ট্রিগার হয়।

`.github/workflows/ci.yml` — সম্পূরক `check` job, `workflow_run`
(README জেনারেশনের পরে) এবং non-README push-এ ট্রিগার, JDK 23,
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`।

## প্রকাশনা (NMCP)

`com.gradleup.nmcp.settings` (**1.5.0**) এর মাধ্যমে
`plantuml-plugin/settings.gradle.kts`-এ কনফিগার:

```kotlin
nmcpSettings {
    centralPortal {
        username = globalProps.getProperty("ossrhUsername")
        password = globalProps.getProperty("ossrhPassword")
        publishingType = "AUTOMATIC"
    }
}
```

ক্রেডেনশিয়াল `~/.gradle/gradle.properties` (`ossrhUsername`, `ossrhPassword`) থেকে পড়া হয়।
সাইনিং `useGpgCmd()` ব্যবহার করে; শুধুমাত্র CI-এর বাইরে non-SNAPSHOT বিল্ডের জন্য সাইন করা হয়
(`System.getenv("CI") != "true"`)।

POM (**সব** `MavenPublication`-এ) ঘোষণা করে:
- Apache License 2.0
- ডেভেলপার `cccp-education` (cccp.edu@gmail.com)
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- `relocationGroup` প্রপার্টি সেট থাকলে ঐচ্ছিক `<relocation>`

প্রকাশিত আর্টিফ্যাক্ট: `education.cccp:plantuml-plugin:0.0.1` Maven Central-এ।

## EPIC অবস্থা

`.agents/INDEX.adoc` অনুসারে:

| EPIC | অবস্থা |
|------|--------|
| 1 ইউনিট পরীক্ষা            | ✅ সম্পন্ন |
| 2 ফাংশনাল পরীক্ষা         | ✅ সম্পন্ন |
| 3 আর্কিটেকচার               | ✅ সম্পন্ন |
| 4 ডকুমেন্টেশন              | ✅ সম্পন্ন |
| 5 RAG সিস্টেম                 | ✅ সম্পন্ন |
| 6 API Key Pool               | ✅ সম্পন্ন |
| 7 Dogfooding Plugin          | ✅ সম্পন্ন (Phase 1) |
| 8 AsciiDoc মাইগ্রেশন         | ✅ সম্পন্ন |
| 9 Knowledge Graph Diagram    | ✅ সম্পন্ন (380/380 PASS) |
| 10 Graphify ইন্টিগ্রেশন      | ✅ সম্পন্ন |
| 11 ব্লগ আর্টিকেল KG + পরীক্ষা   | ✅ সম্পন্ন |
| 12 কনসোলিডেশন             | ✅ সম্পন্ন (Kover 81.19 %) |
| PUB-1 Maven Central প্রকাশনা | ✅ সম্পন্ন (0.0.1) |
| **13 Multi-provider API Pool** | 🟡 চলমান (Sessions 138-141) |
| **PLT-I18N আন্তর্জাতিকীকরণ** | 🟠 চলমান (US-0..US-3 ✅, 16/28 pts, session 151) |

## আর্কিটেকচার ডকস

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — কম্পোনেন্ট ও ডেটা-ফ্লো ডিজাইন
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — EPIC রোডম্যাপ
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — সেশন, EPICs, গভর্ন্যান্স
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — পরিচিত সমস্যা ও সমাধান
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — পুল অভ্যন্তরীণ
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — দেব প্রসিডিউর

## অবদান

1. বিল্ড কম্পাইল হয়: `./gradlew build -x test`
2. লোকাল রিপাবলিশ: `./gradlew -q publishToMavenLocal`
3. ইউনিট পরীক্ষা সবুজ: `./gradlew test`
4. কভারেজ মানা: `./gradlew koverThresholdCheck` (≥75 %)
5. জেনারেটেড `.puml`-এ কোনো `@startuml`/`@enduml` লিক নেই
6. DDD কনভেনশন অনুসরণ (value objects `models.kt`-এ, services `service/`-এ)
7. i18n সম্মান: **সব** `Messages_*.properties`-এ কী যোগ করুন (10 ভাষা)

[CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) এবং
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc) দেখুন।

## লাইসেন্স

Apache License 2.0 — [LICENCE](../LICENCE) দেখুন।

---

_CCCP Education ইকোসিস্টেমের অংশ — `groupId: education.cccp`।_