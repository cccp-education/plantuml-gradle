<!-- translated from README.md rev 0.0.1 -->
# plantuml-gradle — उपभोक्ता गाइड

> AI-सहायता प्राप्त Gradle प्लगइन जो प्राकृतिक-भाषा प्रॉम्प्ट्स से PlantUML आरेख उत्पन्न करता है, RAG (pgvector), एक नॉलेज-ग्राफ रेंडरर, और एक घूर्णनशील API-कुंजी पूल के साथ।

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/plantuml-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/plantuml-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.plantuml.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.plantuml)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/plantuml-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/plantuml-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/plantuml-gradle?label=License)](../LICENCE)

- **संस्करण**: `0.0.1` · **समूह**: `education.cccp` · **प्लगइन ID**: `education.cccp.plantuml`
- **टूलचेन**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1
- **बिल्ड**: `./gradlew build` · **परीक्षण**: `./gradlew test` + `functionalTest` + `cucumberTest` (380/380 PASS)
- **कवरेज**: ≥ 75 % (Kover `koverThresholdCheck`, `check` में एकीकृत)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | **हिन्दी** | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## यह क्या करता है

`plantuml-gradle` एक प्रॉम्प्ट निर्देशिका में `.prompt` फ़ाइलों पर नज़र रखता है, उन्हें
एक LLM (7 langchain4j प्रदाताओं) को भेजता है, लौटाई गई PlantUML सिंटैक्स को मान्य करता है, PNG
छवियाँ रेंडर करता है, मान्य आरेखों को एक **pgvector** RAG इंडेक्स में एकत्र करता है, और
`graphify-out/graph.json` वर्कस्पेस निष्कर्ष से एक **नॉलेज-ग्राफ आरेख** रेंडर करता है।

CCCP Education बहु-प्लगइन पारिस्थितिकी तंत्र का हिस्सा:

```
graphify-out/graph.json ──► generateKnowledgeGraphDiagram ──► PlantUML (deterministic)
prompts/*.prompt ──► generatePlantumlDiagrams ──► LLM ──► validate ──► PNG + RAG index
```

यह प्लगइन RAG अनुक्रमण के लिए `education.cccp.codebase` (`0.0.2`) का उपभोग करता है और
`education.cccp:workspace-bom:0.0.1` के माध्यम से N0 अनुबंध साझा करता है।

## त्वरित प्रारंभ

### 1. प्लगइन लागू करें

```gradle
plugins {
    id("education.cccp.plantuml") version "0.0.1"
}
```

### 2. एक्सटेंशन कॉन्फ़िगर करें (वैकल्पिक)

```gradle
plantuml {
    configPath = file("plantuml-context.yml").absolutePath
    language   = "en"
    supportedLanguages = listOf("en", "fr", "es")
}
```

यदि `plantuml-context.yml` अनुपस्थित या खाली है, तो प्लगइन अंतर्निहित
डिफ़ॉल्ट्स पर वापस लौटता है (देखें `PlantumlManager.Configuration`)।

### 3. आरेख उत्पन्न करें

```bash
./gradlew generatePlantumlDiagrams        # prompts/*.prompt → LLM → .puml + PNG + RAG
./gradlew generateKnowledgeGraphDiagram   # graphify-out/graph.json → KG diagram (no LLM)
./gradlew docs                            # full pipeline: KG + docs + diagrams + validate
```

## उपलब्ध कार्य

| कार्य | समूह | विवरण |
|------|-------|-------------|
| `generatePlantumlDiagrams`      | generate | PlantUML प्रॉम्प्ट्स संसाधित करता है और LLM सहायता से आरेख उत्पन्न करता है |
| `validatePlantumlSyntax`         | verify   | डिबगिंग के लिए PlantUML सिंटैक्स मान्य करता है (`-Pplantuml.diagram=file.puml`) |
| `collectPlantumlIndex`           | collect  | एकत्रित PlantUML आरेखों के साथ RAG इंडेक्स पुनर्निर्माण करता है |
| `generateDiagramDocs`            | generate | Graphify नॉलेज ग्राफ से स्वतः PlantUML दस्तावेज़ आरेख उत्पन्न करता है |
| `generateKnowledgeGraphDiagram`  | generate | `graphify-out/graph.json` से नॉलेज ग्राफ आरेख उत्पन्न करता है (निर्धारक, कोई LLM नहीं) |
| `docs`                           | info     | पूर्ण पाइपलाइन: KG + दस्तावेज़ + आरेख, `validatePlantumlSyntax` द्वारा अंतिम रूप दिया गया |

## एक्सटेंशन DSL

```gradle
plantuml {
    configPath          = file("plantuml-context.yml").absolutePath
    language            = "en"
    supportedLanguages   = listOf("en", "fr", "es", "zh", "hi", "ar", "bn", "pt", "ru", "ur")
}
```

| गुण | प्रकार | डिफ़ॉल्ट | विवरण |
|----------|------|---------|-------------|
| `configPath`          | `Property<String>`      | `plantuml-context.yml` | YAML कॉन्फ़िगरेशन फ़ाइल का पथ |
| `language`            | `Property<String>`      | `"en"`                 | कार्य लेबल के लिए सक्रिय i18n भाषा |
| `supportedLanguages`  | `ListProperty<String>`  | `["en"]`               | सक्षम UI भाषाएँ (10 समर्थित) |

## पूर्वापेक्षाएँ

- **Java** 24+ (Kotlin 2.3.20 टूलचेन, JDK क्रम 24)
- **Gradle** 9.5.1+
- **Docker** (`collectPlantumlIndex` में Testcontainers pgvector के लिए)
- एक **LLM एंडपॉइंट** — डिफ़ॉल्ट Ollama `http://localhost:11434`, मॉडल `smollm:135m`
  (पोर्ट `11434–11436` वैश्विक रूप से निषिद्ध हैं; `11437–11465` पर घूर्णन करें)

## बिल्ड और परीक्षण

```bash
./gradlew build                       # full build (compile + tests)
./gradlew test                        # JUnit5 unit tests
./gradlew functionalTest              # functional tests (GradleRunner + WireMock)
./gradlew cucumberTest               # Cucumber BDD scenarios
./gradlew koverThresholdCheck        # coverage ≥ 75 %
./gradlew publishToMavenLocal        # publish locally
```

वास्तविक-LLM परीक्षण सक्षम करने के लिए (डिफ़ॉल्ट रूप से बहिष्कृत):

```bash
./gradlew test -Ptest.tags="real-llm"
./gradlew functionalTest -Ptest.tags="real-llm,fine-tune"
```

## समस्या निवारण

| लक्षण | समाधान |
|---------|-----|
| `No plantuml-context.yml` चेतावनी      | हानिरहित — प्लगइन अंतर्निहित डिफ़ॉल्ट्स उपयोग करता है |
| pgvector पोर्ट 5432 टकराव             | `./gradlew collectPlantumlIndex -Pplantuml.rag.port=5433` |
| LLM रुकता है / 30 s परीक्षण टाइमआउट           | जाँचें कि Ollama चल रहा है, हीप बढ़ाएँ (`GRADLE_OPTS=-Xmx2g`) |
| `graphify-out/graph.json not found`     | पहले नॉलेज ग्राफ बनाने के लिए `graphify . --no-viz` चलाएँ |
| API Key Pool is empty                   | `plantuml-context.yml` में `langchain4j.<provider>.pool` कॉन्फ़िगर करें |

विवरण के लिए [TROUBLESHOOTING.adoc](../plantuml-plugin/.agents/TROUBLESHOOTING.adoc) देखें।

## लाइसेंस

Apache License 2.0 — [LICENCE](../LICENCE) देखें।

---

_CCCP Education पारिस्थितिकी तंत्र का हिस्सा — `groupId: education.cccp`।_