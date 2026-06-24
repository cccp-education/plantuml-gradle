<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — प्लगइन आंतरिक

> `plantuml-plugin` Gradle प्लगइन के लिए डेवलपर और योगदानकर्ता गाइड।

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Coverage](https://img.shields.io/static/v1?label=coverage&message=%E2%89%A575%25&color=green)]()
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **संस्करण**: `0.0.1` · **समूह**: `education.cccp` · **प्लगइन ID**: `education.cccp.plantuml`
- **टूलचेन**: Java 24 (ordinal) · Kotlin 2.3.20 · Gradle 9.5.1
- **बिल्ड**: `./gradlew build -x test` · **परीक्षण**: `./gradlew test functionalTest cucumberTest` · **कवरेज गेट**: `./gradlew koverThresholdCheck` (≥75%)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | **हिन्दी** | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## मॉड्यूल लेआउट

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

प्लगइन **स्वयं का उपभोग करता है**: मूल प्रोजेक्ट `plantuml-gradle/build.gradle.kts`
`id("education.cccp.plantuml")` लागू करता है और `configPath` को `plantuml-context.yml` पर निर्देशित करता है।

## N0 अनुबंध (workspace-bom MEMPHIS से)

`implementation(platform("education.cccp:workspace-bom:0.0.1"))` के माध्यम से ट्रांज़िटिवली उपभुक्त:

| अनुबंध | आर्टिफैक्ट | प्रदान करता है |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext (RAG संदर्भ) |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig (API-कुंजी घूर्णन) |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | Release-notes अनुबंध (cross-borough) |

## उपभोक्ता निर्भरता

प्लगइन `education.cccp.codebase` संस्करण `0.0.2` **उपभोग** करता है (`build.gradle.kts` में
`alias(libs.plugins.codebase)` के माध्यम से लागू):

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

यह codebase-gradle RAG पाइपलाइन (pgvector अनुक्रमण, समग्र संदर्भ) को
`collectPlantumlIndex` में जोड़ता है। डाउनस्ट्रीम उपभोक्ताओं को codebase प्लगइन स्वतः लागू मिलता है।

## प्रमुख निर्भरताएँ

| लाइब्रेरी | संस्करण | भूमिका |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 LLM प्रदाता: Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace (+ beta), pgvector, all-minilm-l6-v2 |
| **PlantUML इंजन**    | `1.2026.0`          | `net.sourceforge.plantuml` — सिंटैक्स सत्यापन + PNG रेंडर |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | Slides/docs पाइपलाइन (asciidoctor-jvm + gems + slides) |
| **JGit**               | `7.5.0`             | स्वचालित आरेख कमिट (core + ssh + archive) |
| **Arrow KT**           | `2.2.2`             | फंक्शनल कोर (core + fx-coroutines + jackson) |
| **Jackson**            | (BOM `2.21.1`)      | YAML कॉन्फ़िग (`jackson-dataformat-yaml`, kotlin मॉड्यूल, jsr310) |
| **Testcontainers PG**  | `1.21.4`            | RAG एकीकरण परीक्षणों के लिए `pgvector/pgvector` |
| **docker-java**        | `3.7.0`             | प्रोग्रामेटिक कंटेनर नियंत्रण |
| **WireMock**           | `3.9.1`             | परीक्षणों में LLM एंडपॉइंट का HTTP मॉकिंग |
| **Kover**              | `0.9.8`             | कवरेज गेट (≥75 %, `includedSourceSets: main, functionalTest`) |

### Langchain4j प्रदाता बंडल (`libs.bundles.plantuml-ai`)

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### API-कुंजी पूल (`plantuml/apikey/`)

- `ApiKeyPool.kt` — round-robin / least-used घूर्णन
- `QuotaTracker.kt` / `QuotaResetManager.kt` — प्रति-कुंजी कोटा + रीसेट
- `QuotaAuditLogger.kt` — ऑडिट जर्नल
- `Provider.kt` — प्रदाता enum + कुंजी समाधान

## परीक्षण मैट्रिक्स

| कार्य | दायरा | टाइमआउट | समानांतरता |
|------|-------|---------|-------------|
| `test`           | JUnit5 unit (excludes `plantuml.scenarios.**`, `PlantUmlPluginFunctionalTests`) | 30 s/class, 60 s global | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune (SmolLM2-135M) | 5 min | `(cores/2)` |
| `cucumberTest`   | Cucumber BDD scenarios (JUnit-platform engine, excludes junit-jupiter) | 5 min, `forkEvery = 1` | `maxParallelForks = 1` |

- `real-llm` टैग किए गए परीक्षण **डिफ़ॉल्ट रूप से बहिष्कृत** हैं — `-Ptest.tags="real-llm"` से सक्षम करें।
- `functionalTest` अतिरिक्त रूप से `-Ptest.tags="fine-tune"` के साथ `fine-tune` टैग सक्षम करता है।
- `check` `functionalTest` + `cucumberTest` + `koverThresholdCheck` पर निर्भर है।
- **380/380 PASS** (`.agents/INDEX.adoc` EPIC 9 समापन, sessions 125-130 के अनुसार)।

### फाइन-ट्यूनिंग फिक्स्चर

`buildFineTuningImage` Docker इमेज `plantuml-fine-tune:latest` बनाता है;
`downloadFineTuningModel` `HuggingFaceTB/SmolLM2-135M-Instruct` को
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/` में लाता है। दोनों
`functionalTest` की निर्भरताओं के रूप में जोड़े गए हैं।

## JVM ट्यूनिंग

| प्रोफ़ाइल | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC (`-XX:+UseSerialGC`) | `MaxMetaspaceSize=256m` | `maxHeapSize=1g` (cucumber), अन्यत्र अनुकूली |
| सभी | `-XX:TieredStopAtLevel=1` (तेज़ स्टार्टअप) | — | — |
| सभी | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` एक **एकल JVM वर्कर** (`maxParallelForks = 1`, `forkEvery = 0`) पर बल देता है ताकि
नेस्टेड क्लासेस के बीच WireMock + GradleRunner + sharedProjectDir पुनर्उपयोग अधिकतम हो।

## बिल्ड कमांड

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

प्लगइन स्रोत संशोधित करने के बाद हमेशा `./gradlew -q publishToMavenLocal` चलाएँ —
मूल प्रोजेक्ट प्लगइन को `mavenLocal` के माध्यम से उपभोग करता है।

## CI पाइपलाइन

`.github/workflows/test.yml` — `ubuntu-latest` पर एकल **Build & Test** job,
JDK 24 (Temurin), `./gradlew build`, 15 min टाइमआउट। `main` / `master` पर
push/PR पर ट्रिगर।

`.github/workflows/ci.yml` — अनुपूरक `check` job, `workflow_run`
(README जनरेशन के बाद) और non-README push पर ट्रिगर, JDK 23,
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`।

## प्रकाशन (NMCP)

`com.gradleup.nmcp.settings` (**1.5.0**) के माध्यम से
`plantuml-plugin/settings.gradle.kts` में कॉन्फ़िगर:

```kotlin
nmcpSettings {
    centralPortal {
        username = globalProps.getProperty("ossrhUsername")
        password = globalProps.getProperty("ossrhPassword")
        publishingType = "AUTOMATIC"
    }
}
```

क्रेडेंशियल्स `~/.gradle/gradle.properties` (`ossrhUsername`, `ossrhPassword`) से पढ़े जाते हैं।
साइनिंग `useGpgCmd()` का उपयोग करती है; केवल CI के बाहर non-SNAPSHOT बिल्ड्स के लिए साइन किया जाता है
(`System.getenv("CI") != "true"`)।

POM (**सभी** `MavenPublication` पर) घोषित करता है:
- Apache License 2.0
- डेवलपर `cccp-education` (cccp.edu@gmail.com)
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- `relocationGroup` प्रॉपर्टी सेट होने पर वैकल्पिक `<relocation>`

प्रकाशित आर्टिफैक्ट: `education.cccp:plantuml-plugin:0.0.1` Maven Central पर।

## EPIC स्थिति

`.agents/INDEX.adoc` के अनुसार:

| EPIC | स्थिति |
|------|--------|
| 1 यूनिट परीक्षण            | ✅ पूर्ण |
| 2 फंक्शनल परीक्षण         | ✅ पूर्ण |
| 3 आर्किटेक्चर               | ✅ पूर्ण |
| 4 डॉक्यूमेंटेशन              | ✅ पूर्ण |
| 5 RAG सिस्टम                 | ✅ पूर्ण |
| 6 API Key Pool               | ✅ पूर्ण |
| 7 Dogfooding Plugin          | ✅ पूर्ण (Phase 1) |
| 8 AsciiDoc माइग्रेशन         | ✅ पूर्ण |
| 9 Knowledge Graph Diagram    | ✅ पूर्ण (380/380 PASS) |
| 10 Graphify एकीकरण      | ✅ पूर्ण |
| 11 ब्लॉग आलेख KG + परीक्षण   | ✅ पूर्ण |
| 12 समेकन             | ✅ पूर्ण (Kover 81.19 %) |
| PUB-1 Maven Central प्रकाशन | ✅ पूर्ण (0.0.1) |
| **13 Multi-provider API Pool** | 🟡 प्रगति पर (Sessions 138-141) |
| **PLT-I18N अंतर्राष्ट्रीयकरण** | 🟠 प्रगति पर (US-0..US-3 ✅, 16/28 pts, session 151) |

## आर्किटेक्चर दस्तावेज़

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — घटक और डेटा-फ्लो डिज़ाइन
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — EPIC रोडमैप
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — सत्र, EPICs, शासन
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — ज्ञात समस्याएँ और समाधान
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — पूल आंतरिक
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — देव प्रक्रियाएँ

## योगदान

1. बिल्ड कंपाइल हो: `./gradlew build -x test`
2. स्थानीय पुनःप्रकाशन: `./gradlew -q publishToMavenLocal`
3. यूनिट परीक्षण हरे: `./gradlew test`
4. कवरेज का पालन: `./gradlew koverThresholdCheck` (≥75 %)
5. जनित `.puml` में कोई `@startuml`/`@enduml` लीक नहीं
6. DDD सम्मेलनों का पालन (value objects `models.kt` में, services `service/` में)
7. i18n का सम्मान: **सभी** `Messages_*.properties` में कुंजी जोड़ें (10 भाषाएँ)

[CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) और
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc) देखें।

## लाइसेंस

Apache License 2.0 — [LICENCE](../LICENCE) देखें।

---

_CCCP Education पारिस्थितिकी तंत्र का हिस्सा — `groupId: education.cccp`।_