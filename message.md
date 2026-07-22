Salut ! Je vous présente **plantuml-gradle**, un plugin Gradle qui intègre la génération de diagrammes PlantUML dans votre build — avec ou sans IA.

:link: **GitHub :** [plantuml-gradle](https://github.com/cheroliv/plantuml-gradle)

---

### :brain: Ce que le plugin fait pour vous

Des `.puml` ? Il les valide et les rend en PNG. Pas envie de dessiner ? Écrivez un `.prompt`, le plugin appelle un LLM et livre un diagramme validé. Architecture du code ? Il génère des diagrammes depuis le Knowledge Graph de votre codebase.

### :rocket: Les 5 tâches

| Tâche | Ce qu'elle fait |
|---|---|
| `generatePlantumlDiagrams` | `.prompt` → LLM → `.puml` + `.png`. Boucle auto avec retry si invalide. |
| `validatePlantumlSyntax` | Valide vos `.puml`. Idéal en CI. |
| `collectPlantumlIndex` | Indexe vos diagrammes dans pgvector. Les générations IA s'améliorent avec le temps. |
| `generateKnowledgeGraphDiagram` | Diagramme d'architecture depuis Graphify. **Déterministe, pas d'IA.** |
| `generateDiagramDocs` | Génère des `.prompt` depuis le KG → LLM → documentation auto. |

### :wrench: Setup

```kotlin
plugins { id("education.cccp.plantuml") version "0.0.2" }
```

7 providers : Ollama (gratuit), OpenAI, Gemini, Mistral, Claude, HuggingFace, Groq. Pool de clés API avec rotation.

---

**v0.0.5** — retours bienvenus ! :star: #PlantUML #Gradle #LLM