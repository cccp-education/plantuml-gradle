<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — باطن الإضافة

> دليل المطور والمساهم لإضافة Gradle `plantuml-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![Coverage](https://img.shields.io/static/v1?label=coverage&message=%E2%89%A575%25&color=green)]()
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **الإصدار**: `0.0.1` · **المجموعة**: `education.cccp` · **معرّف الإضافة**: `education.cccp.plantuml`
- **سلسلة الأدوات**: Java 24 (ترتيب) · Kotlin 2.3.20 · Gradle 9.5.1
- **البناء**: `./gradlew build -x test` · **الاختبارات**: `./gradlew test functionalTest cucumberTest` · **بوابة التغطية**: `./gradlew koverThresholdCheck` (≥75%)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | **العربية** | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## تخطيط الوحدات

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

الإضافة **تستخدم نفسها**: المشروع الجذري `plantuml-gradle/build.gradle.kts` يطبّق
`id("education.cccp.plantuml")` ويوجّه `configPath` إلى `plantuml-context.yml`.

## عقود N0 (من workspace-bom MEMPHIS)

مُستهلكة transitively عبر `implementation(platform("education.cccp:workspace-bom:0.0.1"))`:

| العقد | الأرتيفاكت | يوفّر |
|----------|----------|----------|
| `codebase-contracts`   | `education.cccp:codebase-contracts:0.0.1`   | ContextChannel, ChannelBudget, CompositeContext (سياق RAG) |
| `agent-contracts`      | `education.cccp:agent-contracts:0.0.1`      | Epic, UserStory, GradleTask, AgentState |
| `llm-pool-contracts`   | `education.cccp:llm-pool-contracts:0.0.1`   | LlmInstancePool, LlmInstance, QuotaConfig (تدوير مفاتيح API) |
| `opencode-session-contracts` | `education.cccp:opencode-session-contracts:0.0.1` | SessionPrompt, SessionResponse, AgentContext |
| `i18n-contracts`       | `education.cccp:i18n-contracts:0.0.1`       | SupportedLanguage, LanguageCatalog, I18nConfig, MessageBundle |
| `pipeline-contracts`   | `education.cccp:pipeline-contracts:0.0.1`   | عقود release-notes (cross-borough) |

## تبعية المستهلك

الإضافة **تستهلك** `education.cccp.codebase` إصدار `0.0.2` (مطبّقة عبر
`alias(libs.plugins.codebase)` في `build.gradle.kts`):

```toml
# plantuml-plugin/gradle/libs.versions.toml
codebase = "0.0.2"
# ...
[plugins]
codebase = { id = "education.cccp.codebase", version.ref = "codebase" }
```

هذا يوصّل خط أنابيب RAG من codebase-gradle (فهرسة pgvector، سياق مركّب) إلى
`collectPlantumlIndex`. المستهلكون下游 يحصلون على إضافة codebase مطبّقة تلقائيًا.

## التبعيات الرئيسية

| المكتبة | الإصدار | الدور |
|---------|---------|------|
| **langchain4j**         | `1.14.1`            | 7 مزودي LLM: Ollama, OpenAI, Gemini, Mistral, Anthropic, HuggingFace (+ beta), pgvector, all-minilm-l6-v2 |
| **محرك PlantUML**    | `1.2026.0`          | `net.sourceforge.plantuml` — تحقق الصياغة + عرض PNG |
| **Asciidoctor Gradle** | `5.0.0-alpha.1`     | خط أنابيب slides/docs (asciidoctor-jvm + gems + slides) |
| **JGit**               | `7.5.0`             | commits مؤتمتة للمخططات (core + ssh + archive) |
| **Arrow KT**           | `2.2.2`             | نواة وظيفية (core + fx-coroutines + jackson) |
| **Jackson**            | (BOM `2.21.1`)      | تكوين YAML (`jackson-dataformat-yaml`، وحدة kotlin، jsr310) |
| **Testcontainers PG**  | `1.21.4`            | `pgvector/pgvector` لاختبارات تكامل RAG |
| **docker-java**        | `3.7.0`             | تحكم برمجي بالحاويات |
| **WireMock**           | `3.9.1`             | محاكاة HTTP لنقاط نهاية LLM في الاختبارات |
| **Kover**              | `0.9.8`             | بوابة التغطية (≥75 %، `includedSourceSets: main, functionalTest`) |

### حزمة مزودي langchain4j (`libs.bundles.plantuml-ai`)

```
langchain4j, langchain4j-ollama, langchain4j-open-ai, langchain4j-gemini,
langchain4j-mistral, langchain4j-anthropic, langchain4j-hugging-face (beta),
langchain4j-pgvector (beta), langchain4j-minilm (beta)
```

### pool مفاتيح API (`plantuml/apikey/`)

- `ApiKeyPool.kt` — تدوير round-robin / least-used
- `QuotaTracker.kt` / `QuotaResetManager.kt` — حصة لكل مفتاح + إعادة تعيين
- `QuotaAuditLogger.kt` — سجل تدقيق
- `Provider.kt` — enum المزود + تحليل المفتاح

## مصفوفة الاختبارات

| المهمة | النطاق | المهلة | التوازي |
|------|-------|---------|-------------|
| `test`           | JUnit5 unit (excludes `plantuml.scenarios.**`, `PlantUmlPluginFunctionalTests`) | 30 s/class, 60 s global | `maxParallelForks = 1` |
| `functionalTest` | GradleRunner + WireMock + Testcontainers pgvector + fine-tune (SmolLM2-135M) | 5 min | `(cores/2)` |
| `cucumberTest`   | Cucumber BDD scenarios (JUnit-platform engine, excludes junit-jupiter) | 5 min, `forkEvery = 1` | `maxParallelForks = 1` |

- الاختبارات الموسومة `real-llm` **مستبعدة افتراضيًا** — فعّلها بـ `-Ptest.tags="real-llm"`.
- `functionalTest` يفعّل أيضًا وسم `fine-tune` بـ `-Ptest.tags="fine-tune"`.
- `check` يعتمد على `functionalTest` + `cucumberTest` + `koverThresholdCheck`.
- **380/380 PASS** (حسب `.agents/INDEX.adoc` إغلاق EPIC 9، sessions 125-130).

### تجهيز fine-tuning

`buildFineTuningImage` يبني صورة Docker `plantuml-fine-tune:latest`؛
`downloadFineTuningModel` يجلب `HuggingFaceTB/SmolLM2-135M-Instruct` إلى
`src/functionalTest/resources/models/SmolLM2-135M-Instruct/`. كلاهما موصولان كـ
تبعيات لـ `functionalTest`.

## ضبط JVM

| الملف | GC | Metaspace | Heap |
|---------|----|-----------|------|
| `test` / `cucumberTest` / `functionalTest` | SerialGC (`-XX:+UseSerialGC`) | `MaxMetaspaceSize=256m` | `maxHeapSize=1g` (cucumber)، تكييفي في البقية |
| الكل | `-XX:TieredStopAtLevel=1` (بدء سريع) | — | — |
| الكل | `-XX:+EnableDynamicAgentLoading` | — | — |

`test` يفرض **عامل JVM واحد** (`maxParallelForks = 1`، `forkEvery = 0`) لتعظيم
إعادة استخدام WireMock + GradleRunner + sharedProjectDir بين الفئات المتداخلة.

## أوامر البناء

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

شغّل دائمًا `./gradlew -q publishToMavenLocal` بعد تعديل مصدر الإضافة —
المشروع الجذري يستهلك الإضافة عبر `mavenLocal`.

## خط أنابيب CI

`.github/workflows/test.yml` — وظيفة **Build & Test** واحدة على `ubuntu-latest`،
JDK 24 (Temurin)، `./gradlew build`، مهلة 15 دقيقة. تُطلق عند push/PR إلى
`main` / `master`.

`.github/workflows/ci.yml` — وظيفة `check` تكميلية تُطلقها `workflow_run`
(بعد توليد README) وعند pushes غير README، JDK 23،
`./gradlew -p readme-plugin clean check -Dplantuml.test.mode=true --no-daemon`.

## النشر (NMCP)

مُكوّن عبر `com.gradleup.nmcp.settings` (**1.5.0**) في
`plantuml-plugin/settings.gradle.kts`:

```kotlin
nmcpSettings {
    centralPortal {
        username = globalProps.getProperty("ossrhUsername")
        password = globalProps.getProperty("ossrhPassword")
        publishingType = "AUTOMATIC"
    }
}
```

تُقرأ بيانات الاعتماد من `~/.gradle/gradle.properties` (`ossrhUsername`، `ossrhPassword`).
التوقيع يستخدم `useGpgCmd()`؛ يُوقّع فقط خارج CI لبن non-SNAPSHOT
(`System.getenv("CI") != "true"`).

POM (على **كل** `MavenPublication`) يصرّح بـ:
- Apache License 2.0
- مطوّر `cccp-education` (cccp.edu@gmail.com)
- SCM `https://github.com/cheroliv/plantuml-gradle.git`
- `<relocation>` اختياري عند ضبط خاصية `relocationGroup`

الأرتيفاكت المنشور: `education.cccp:plantuml-plugin:0.0.1` على Maven Central.

## حالة EPICs

حسب `.agents/INDEX.adoc`:

| EPIC | الحالة |
|------|--------|
| 1 اختبارات وحدة            | ✅ منجز |
| 2 اختبارات وظيفية         | ✅ منجز |
| 3 معمارية               | ✅ منجز |
| 4 توثيق              | ✅ منجز |
| 5 نظام RAG                 | ✅ منجز |
| 6 pool مفاتيح API               | ✅ منجز |
| 7 dogfooding الإضافة          | ✅ منجز (Phase 1) |
| 8 ترحيل AsciiDoc         | ✅ منجز |
| 9 مخطط الرسم المعرفي    | ✅ منجز (380/380 PASS) |
| 10 تكامل Graphify      | ✅ منجز |
| 11 مقال مدونة KG + اختبارات   | ✅ منجز |
| 12 توحيد             | ✅ منجز (Kover 81.19 %) |
| PUB-1 نشر Maven Central | ✅ منجز (0.0.1) |
| **13 Multi-provider API Pool** | 🟡 قيد التقدم (Sessions 138-141) |
| **PLT-I18N التدويل** | 🟠 قيد التقدم (US-0..US-3 ✅، 16/28 pts، session 151) |

## وثائق المعمارية

- [ARCHITECTURE.adoc](../plantuml-plugin/.agents/ARCHITECTURE.adoc) — تصميم المكوّنات وتدفق البيانات
- [DECISIONS_ARCHITECTURE.adoc](../plantuml-plugin/.agents/DECISIONS_ARCHITECTURE.adoc) — ADRs
- [ROADMAP.adoc](../plantuml-plugin/.agents/ROADMAP.adoc) — خارطة طريق EPICs
- [.agents/INDEX.adoc](../plantuml-plugin/.agents/INDEX.adoc) — الجلسات، EPICs، الحوكمة
- [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) — مشاكل معروفة وإصلاحات
- [API_KEY_POOL_ESSENTIALS.adoc](../plantuml-plugin/.agents/API_KEY_POOL_ESSENTIALS.adoc) — باطن pool
- [PROCEDURES.adoc](../plantuml-plugin/.agents/PROCEDURES.adoc) — إجراءات التطوير

## المساهمة

1. البناء يترجم: `./gradlew build -x test`
2. إعادة النشر محليًا: `./gradlew -q publishToMavenLocal`
3. اختبارات الوحدة خضراء: `./gradlew test`
4. احترام التغطية: `./gradlew koverThresholdCheck` (≥75 %)
5. لا تسرّبات `@startuml`/`@enduml` في `.puml` المولّدة
6. اتباع اصطلاحات DDD (value objects في `models.kt`، خدمات في `service/`)
7. احترام i18n: أضف المفاتيح في **كل** `Messages_*.properties` (10 لغات)

انظر [CONTRIBUTING.adoc](../plantuml-plugin/.agents/CONTRIBUTING.adoc) و
[CODE_OF_CONDUCT.adoc](../plantuml-plugin/.agents/CODE_OF_CONDUCT.adoc).

## الترخيص

Apache License 2.0 — انظر [LICENCE](../LICENCE).

---

_جزء من منظومة CCCP Education — `groupId: education.cccp`._