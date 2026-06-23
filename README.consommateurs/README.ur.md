<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — صارفین کا گائیڈ

> قدرتی-زبان کے پرومٹس سے PlantUML ڈایاگرام بنانے کے لیے AI-معاون Gradle پلگ ان، RAG (pgvector)، ایک نالج-گراف رینڈرر، اور گھومنے والے API-کلید پول کے ساتھ۔

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **ورژن**: `0.0.1` · **گروہ**: `education.cccp` · **پلگ ان ID**: `education.cccp.plantuml`
- **ٹول چین**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **بلڈ**: `./gradlew build` · **ٹیسٹ**: `./gradlew test` + `functionalTest` + `cucumberTest` (380/380 PASS)
- **کوریج**: ≥ 75 % (Kover `koverThresholdCheck`، `check` میں ضم)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | **اردو**

---

## یہ کیا کرتا ہے

`plantuml-gradle` ایک پرومٹٹ ڈائریکٹری میں `.prompt` فائلوں پر نظر رکھتا ہے، انہیں
ایک LLM (7 langchain4j فراہم کنندگان) کو بھیجتا ہے، واپس کردہ PlantUML نحوی صورت کی تصدیق کرتا ہے، PNG
تصاویر رینڈر کرتا ہے، درست ڈایاگراموں کو ایک **pgvector** RAG انڈیکس میں اکٹھا کرتا ہے، اور
`graphify-out/graph.json` ورک اسپیس نکات سے ایک **نالج-گراف ڈایاگرام** رینڈر کرتا ہے۔

CCCP Education کثیر-پلگ ان ماحولیاتی نظام کا حصہ:

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (deterministic)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validate ──► PNG + RAG index
```

پلگ ان RAG انڈیکسنگ کے لیے `education.cccp.codebase` (`0.0.2`) استعمال کرتا ہے اور
`education.cccp:workspace-bom:0.0.1` کے ذریعے N0 معاہدے شیئر کرتا ہے۔

## فوری آغاز

### 1. پلگ ان لاگو کریں

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. توسیع تشکیل دیں (اختیاری)

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "en"
    supportedLanguages = listOf("en", "fr", "es")
}
```

اگر `plantuml-context.yml` غیر موجود یا خالی ہے، تو پلگ انbuiltin
ڈیفالٹس پر لوٹتا ہے (دیکھیں `PlantumlManager.Configuration`)۔

### 3. ڈایاگرام بنائیں

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → KG diagram (no LLM)
./gradlew docs                            # full pipeline: KG + docs + diagrams + validate
```

## دستیاب کام

| کام | گروہ | تفصیل |
|------|-------|-------------|
| `generatePlantumlDiagrams`      | generate | PlantUML پرومٹس پر عمل کرتا ہے اور LLM مدد سے ڈایاگرام بناتا ہے |
| `validatePlantumlSyntax`         | verify   | ڈیبگنگ کے لیے PlantUML نحوی صورت کی تصدیق کرتا ہے (`-Pplantuml.diagram=file.puml`) |
| `collectPlantumlIndex`           | collect  | اکٹھے کردہ PlantUML ڈایاگراموں سے RAG انڈیکس دوبارہ بناتا ہے |
| `generateDiagramDocs`            | generate | Graphify نالج گراف سے خود بخود PlantUML دستاویزی ڈایاگرام بناتا ہے |
| `generateKnowledgeGraphDiagram`  | generate | `graphify-out/graph.json` سے نالج گراف ڈایاگرام بناتا ہے (قطعی، کوئی LLM نہیں) |
| `docs`                           | info     | مکمل پائپ لائن: KG + دستاویزات + ڈایاگرام، `validatePlantumlSyntax` کے ذریعے حتمی |

## توسیع DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "en"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| خصوصیت | قسم | ڈیفالٹ | تفصیل |
|----------|------|---------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | YAML کنفیگریشن فائل کا راستہ |
| `language`            | `Property<String>`      | `"en"`                 | کام لیبلز کے لیے فعال i18n زبان |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | فعال UI زبانیں (10 معاون) |

## پیش نیازات

- **Java** 24+ (Kotlin 2.3.20 ٹول چین، JDK ترتیب 24)
- **Gradle** 9.5.1+
- **Docker** (`collectPlantumlIndex` میں Testcontainers pgvector کے لیے)
- ایک **LLM اینڈ پوائنٹ** — ڈیفالٹ Ollama `http://localhost:11434`، ماڈل `smollm:135m`
  (پورٹس `11434–11436` عالمی طور پر ممنوع ہیں؛ `11437–11465` پر گھومیں)

## بلڈ اور ٹیسٹ

```bash
./gradlew build                       # full build (compile + tests)
./gradlew test                        # JUnit5 unit tests
./gradlew functionalTest              # functional tests (GradleRunner + WireMock)
./gradlew cucumberTest               # Cucumber BDD scenarios
./gradlew koverThresholdCheck        # coverage ≥ 75 %
./gradlew publishToMavenLocal        # publish locally
```

اصل-LLM ٹیسٹ فعال کرنے کے لیے (بذات خود خارج):

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## مسئلہ حل

| علامت | حل |
|---------|-----|
| `No plantuml-context.yml` تنبیہ      | بے ضرر — پلگ ان builtin ڈیفالٹس استعمال کرتا ہے |
| pgvector پورٹ 5432 تنازع             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| LLM رک جاتا ہے / 30 s ٹیسٹ ٹائم آؤٹ           | چیک کریں کہ Ollama چل رہا ہے، ہیپ بڑھائیں (`GRADLE_OPTS=-Xmx2g`) |
| `graphify-out/graph.json not found`     | نالج گراف بنانے کے لیے پہلے `graphify . --no-viz` چلائیں |
| API Key Pool is empty                   | `plantuml-context.yml` میں `langchain4j.<provider>.pool` تشکیل دیں |

تفصیل کے لیے [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) دیکھیں۔

## لائسنس

Apache License 2.0 — [LICENCE](../LICENCE) دیکھیں۔

---

_CCCP Education ماحولیاتی نظام کا حصہ — `groupId: education.cccp`۔_