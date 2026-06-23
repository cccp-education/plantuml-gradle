<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — دليل المستهلك

> إضافة Gradle مدعومة بالذكاء الاصطناعي لتوليد مخططات PlantUML من تعليمات بلغة طبيعية، مع RAG (pgvector)، وعارض رسم معرفي، وتدوير مفاتيح API.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **الإصدار**: `0.0.1` · **المجموعة**: `education.cccp` · **معرّف الإضافة**: `education.cccp.plantuml`
- **سلسلة الأدوات**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **البناء**: `./gradlew build` · **الاختبارات**: `./gradlew test` + `functionalTest` + `cucumberTest` (380/380 PASS)
- **التغطية**: ≥ 75 % (Kover `koverThresholdCheck`، مدمجة في `check`)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | **العربية** | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## ماذا يفعل

تراقب `plantuml-gradle` ملفات `.prompt` في دليل التعليمات، ترسلها إلى
LLM (7 مزودي langchain4j)، تتحقق من صياغة PlantUML المرتجعة، تعرض صور
PNG، تجمع المخططات الصحيحة في فهرس RAG **pgvector**، وتعرض
**مخطط رسم معرفي** من مستخرج `graphify-out/graph.json` لمساحة العمل.

جزء من منظومة CCCP Education متعددة الإضافات:

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (deterministic)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validate ──► PNG + RAG index
```

تستهلك الإضافة `education.cccp.codebase` (`0.0.2`) لفهرسة RAG وتشارك
عقود N0 عبر `education.cccp:workspace-bom:0.0.1`.

## البدء السريع

### 1. تطبيق الإضافة

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. تكوين الامتداد (اختياري)

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "en"
    supportedLanguages = listOf("en", "fr", "es")
}
```

إذا كان `plantuml-context.yml` غائبًا أو فارغًا، تعود الإضافة إلى
القيم الافتراضية المدمجة (انظر `PlantumlManager.Configuration`).

### 3. توليد المخططات

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → KG diagram (no LLM)
./gradlew docs                            # full pipeline: KG + docs + diagrams + validate
```

## المهام المتاحة

| المهمة | المجموعة | الوصف |
|------|-------|-------------|
| `generatePlantumlDiagrams`      | generate | يعالج تعليمات PlantUML ويولد المخططات بمساعدة LLM |
| `validatePlantumlSyntax`         | verify   | يتحقق من صياغة PlantUML لأغراض التصحيح (`-Pplantuml.diagram=file.puml`) |
| `collectPlantumlIndex`           | collect  | يعيد بناء فهرس RAG بالمخططات المجموعة |
| `generateDiagramDocs`            | generate | يولد تلقائيًا مخططات PlantUML للتوثيق من رسم Graphify المعرفي |
| `generateKnowledgeGraphDiagram`  | generate | يولد مخطط الرسم المعرفي من `graphify-out/graph.json` (حتمي، بدون LLM) |
| `docs`                           | info     | خط أنابيب كامل: KG + توثيق + مخططات، يُنهيه `validatePlantumlSyntax` |

## امتداد DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "en"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| الخاصية | النوع | الافتراضي | الوصف |
|----------|------|---------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | مسار ملف تكوين YAML |
| `language`            | `Property<String>`      | `"en"`                 | لغة i18n النشطة لتسميات المهام |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | لغات الواجهة المفعّلة (10 مدعومة) |

## المتطلبات المسبقة

- **Java** 24+ (سلسلة أدوات Kotlin 2.3.20، ترتيب JDK 24)
- **Gradle** 9.5.1+
- **Docker** (لـ Testcontainers pgvector في `collectPlantumlIndex`)
- **نقطة نهاية LLM** — الافتراضي Ollama `http://localhost:11434`، النموذج `smollm:135m`
  (المنافذ `11434–11436` ممنوعة عالميًا؛ التدوير عبر `11437–11465`)

## البناء والاختبار

```bash
./gradlew build                       # full build (compile + tests)
./gradlew test                        # JUnit5 unit tests
./gradlew functionalTest              # functional tests (GradleRunner + WireMock)
./gradlew cucumberTest               # Cucumber BDD scenarios
./gradlew koverThresholdCheck        # coverage ≥ 75 %
./gradlew publishToMavenLocal        # publish locally
```

لتفعيل اختبارات LLM الحقيقية (مستبعدة افتراضيًا):

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## استكشاف الأخطاء

| العَرَض | الحل |
|---------|-----|
| تحذير `No plantuml-context.yml`      | غير ضار — تستخدم الإضافة القيم الافتراضية المدمجة |
| تعارض منفذ pgvector 5432             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| توقف LLM / مهلة اختبار 30 ثانية      | تحقق من تشغيل Ollama، زِد الكومة (`GRADLE_OPTS=-Xmx2g`) |
| `graphify-out/graph.json not found`     | شغّل أولاً `graphify . --no-viz` لبناء الرسم المعرفي |
| API Key Pool is empty                   | تكوين `langchain4j.<provider>.pool` في `plantuml-context.yml` |

انظر [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) للتفاصيل.

## الترخيص

Apache License 2.0 — انظر [LICENCE](../LICENCE).

---

_جزء من منظومة CCCP Education — `groupId: education.cccp`._