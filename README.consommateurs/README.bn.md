<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — ভোক্তা গাইড

> প্রাকৃতিক-ভাষা প্রম্পট থেকে PlantUML ডায়াগ্রাম তৈরির জন্য AI-সহায়িত Gradle প্লাগইন, RAG (pgvector), একটি নলেজ-গ্রাফ রেন্ডারার এবং একটি ঘূর্ণায়মান API-কী পুল সহ।

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **সংস্করণ**: `0.0.1` · **গোষ্ঠী**: `education.cccp` · **প্লাগইন ID**: `education.cccp.plantuml`
- **টুলচেইন**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **বিল্ড**: `./gradlew build` · **পরীক্ষা**: `./gradlew test` + `functionalTest` + `cucumberTest` (380/380 PASS)
- **কভারেজ**: ≥ 75 % (Kover `koverThresholdCheck`, `check`-এ সংহিত)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | **বাংলা** | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## এটি কী করে

`plantuml-gradle` একটি প্রম্পট ডিরেক্টরিতে `.prompt` ফাইলগুলি পর্যবেক্ষণ করে, সেগুলিকে
একটি LLM-এ (7টি langchain4j প্রদানকারী) পাঠায়, প্রত্যাবর্তিত PlantUML সিনট্যাক্স যাচাই করে, PNG
চিত্র রেন্ডার করে, বৈধ ডায়াগ্রামগুলিকে একটি **pgvector** RAG সূচকে সংগ্রহ করে এবং
`graphify-out/graph.json` ওয়ার্কস্পেস নিষ্কর্ষ থেকে একটি **নলেজ-গ্রাফ ডায়াগ্রাম** রেন্ডার করে।

CCCP Education বহু-প্লাগইন ইকোসিস্টেমের অংশ:

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (deterministic)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validate ──► PNG + RAG index
```

প্লাগইনটি RAG সূচকীকরণের জন্য `education.cccp.codebase` (`0.0.2`) গ্রহণ করে এবং
`education.cccp:workspace-bom:0.0.1` এর মাধ্যমে N0 চুক্তি ভাগ করে।

## দ্রুত শুরু

### 1. প্লাগইন প্রয়োগ করুন

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. এক্সটেনশন কনফিগার করুন (ঐচ্ছিক)

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "en"
    supportedLanguages = listOf("en", "fr", "es")
}
```

`plantuml-context.yml` অনুপস্থিত বা খালি থাকলে, প্লাগইন অন্তর্নির্মিত
ডিফল্টে ফিরে যায় (দেখুন `PlantumlManager.Configuration`)।

### 3. ডায়াগ্রাম তৈরি করুন

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → KG diagram (no LLM)
./gradlew docs                            # full pipeline: KG + docs + diagrams + validate
```

## উপলব্ধ কার্য

| কার্য | গোষ্ঠী | বিবরণ |
|------|-------|-------------|
| `generatePlantumlDiagrams`      | generate | PlantUML প্রম্পট প্রক্রিয়া করে এবং LLM সহায়তায় ডায়াগ্রাম তৈরি করে |
| `validatePlantumlSyntax`         | verify   | ডিবাগিংয়ের জন্য PlantUML সিনট্যাক্স যাচাই করে (`-Pplantuml.diagram=file.puml`) |
| `collectPlantumlIndex`           | collect  | সংগৃহীত PlantUML ডায়াগ্রাম দিয়ে RAG সূচক পুনর্নির্মাণ করে |
| `generateDiagramDocs`            | generate | Graphify নলেজ গ্রাফ থেকে স্বয়ংক্রিয়ভাবে PlantUML ডকুমেন্টেশন ডায়াগ্রাম তৈরি করে |
| `generateKnowledgeGraphDiagram`  | generate | `graphify-out/graph.json` থেকে নলেজ গ্রাফ ডায়াগ্রাম তৈরি করে (নির্ধারক, কোনো LLM নয়) |
| `docs`                           | info     | সম্পূর্ণ পাইপলাইন: KG + ডকস + ডায়াগ্রাম, `validatePlantumlSyntax` দ্বারা চূড়ান্ত |

## এক্সটেনশন DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "en"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| বৈশিষ্ট্য | ধরন | ডিফল্ট | বিবরণ |
|----------|------|---------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | YAML কনফিগারেশন ফাইলের পথ |
| `language`            | `Property<String>`      | `"en"`                 | কার্য লেবেলের জন্য সক্রিয় i18n ভাষা |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | সক্রিয় UI ভাষা (10টি সমর্থিত) |

## পূর্বশর্ত

- **Java** 24+ (Kotlin 2.3.20 টুলচেইন, JDK অর্ডিনাল 24)
- **Gradle** 9.5.1+
- **Docker** (`collectPlantumlIndex`-এ Testcontainers pgvector-এর জন্য)
- একটি **LLM এন্ডপয়েন্ট** — ডিফল্ট Ollama `http://localhost:11434`, মডেল `smollm:135m`
  (পোর্ট `11434–11436` বিশ্বব্যাপী নিষিদ্ধ; `11437–11465`-এ ঘূর্ণন)

## বিল্ড ও পরীক্ষা

```bash
./gradlew build                       # full build (compile + tests)
./gradlew test                        # JUnit5 unit tests
./gradlew functionalTest              # functional tests (GradleRunner + WireMock)
./gradlew cucumberTest               # Cucumber BDD scenarios
./gradlew koverThresholdCheck        # coverage ≥ 75 %
./gradlew publishToMavenLocal        # publish locally
```

প্রকৃত-LLM পরীক্ষা সক্ষম করতে (ডিফল্টরূপে বাদ দেওয়া):

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## সমস্যা সমাধান

| লক্ষণ | সমাধান |
|---------|-----|
| `No plantuml-context.yml` সতর্কতা      | ক্ষতিকারক নয় — প্লাগইন অন্তর্নির্মিত ডিফল্ট ব্যবহার করে |
| pgvector পোর্ট 5432 দ্বন্দ্ব             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| LLM আটকে যায় / 30 s পরীক্ষা টাইমআউট           | Ollama চলছে কিনা দেখুন, হিপ বাড়ান (`GRADLE_OPTS=-Xmx2g`) |
| `graphify-out/graph.json not found`     | নলেজ গ্রাফ তৈরি করতে প্রথমে `graphify . --no-viz` চালান |
| API Key Pool is empty                   | `plantuml-context.yml`-এ `langchain4j.<provider>.pool` কনফিগার করুন |

বিস্তারিত জানতে [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) দেখুন।

## লাইসেন্স

Apache License 2.0 — [LICENCE](../LICENCE) দেখুন।

---

_CCCP Education ইকোসিস্টেমের অংশ — `groupId: education.cccp`।_