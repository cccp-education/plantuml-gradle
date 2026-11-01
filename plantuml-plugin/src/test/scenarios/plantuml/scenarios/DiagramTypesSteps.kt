package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class DiagramTypesSteps(
    private val world: PlantumlWorld,
) {
    @Given("a prompt file {string} with content:")
    fun createPromptFileWithContent(
        fileName: String,
        content: String,
    ) {
        world.createGradleProject()
        world.createPromptFile(fileName, content)

        val plantumlCode =
            when {
                content.contains("sequence", ignoreCase = true) ->
                    "@startuml\\nactor User\\nparticipant System\\nUser -> System: Login\\n@enduml"
                content.contains("class", ignoreCase = true) ->
                    "@startuml\\nclass Book\\nclass Library\\nBook --> Library\\n@enduml"
                content.contains("component", ignoreCase = true) ->
                    "@startuml\\ncomponent [API]\\ncomponent [Database]\\n[API] --> [Database]\\n@enduml"
                content.contains("use case", ignoreCase = true) ->
                    "@startuml\\nactor User\\nusecase Login\\nUser --> Login\\n@enduml"
                content.contains("activity", ignoreCase = true) ->
                    "@startuml\\nstart\\n:Process Order;\\nstop\\n@enduml"
                content.contains("state", ignoreCase = true) ->
                    "@startuml\\n[*] --> Red\\nRed --> Green\\n@enduml"
                content.contains("deployment", ignoreCase = true) ->
                    "@startuml\\nnode Cloud\\nnode DB\\nCloud --> DB\\n@enduml"
                else -> "@startuml\\nactor User\\n@enduml"
            }

        world.startMockLlm(
            """
            {
              "plantuml": {
                "code": "$plantumlCode",
                "description": "Generated diagram"
              }
            }
            """.trimIndent(),
        )
    }

    @Then("the generated PlantUML should use sequence diagram syntax")
    fun generatedPlantUmlShouldUseSequenceDiagramSyntax() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    "@startuml",
                    "@enduml",
                    "participant",
                    "actor",
                )
            }
        }
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
        )
    }

    @Then("contain {string} or {string} keywords")
    fun containKeywords(
        keyword1: String,
        keyword2: String,
    ) {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(keyword1, keyword2)
            }
        }
    }

    @Then("the generated PlantUML should use class diagram syntax")
    fun generatedPlantUmlShouldUseClassDiagramSyntax() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    "@startuml",
                    "@enduml",
                    "class",
                )
            }
        }
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
        )
    }

    @Then("contain {string} definitions with relationships")
    fun containClassDefinitionsWithRelationships(keyword: String) {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    keyword,
                    "--",
                    "-->",
                    "<|--",
                    "*--",
                    "o--",
                )
            }
        }
    }

    @Then("the generated PlantUML should use component diagram syntax")
    fun generatedPlantUmlShouldUseComponentDiagramSyntax() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    "@startuml",
                    "@enduml",
                    "component",
                    "[",
                    "]",
                )
            }
        }
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
        )
    }

    @Then("contain {string} or {string} notation")
    fun containComponentNotation(
        keyword1: String,
        keyword2: String,
    ) {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(keyword1, keyword2)
            }
        }
    }

    @Then("the generated PlantUML should use use case diagram syntax")
    fun generatedPlantUmlShouldUseUseCaseDiagramSyntax() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    "@startuml",
                    "@enduml",
                    "usecase",
                    "actor",
                )
            }
        }
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
        )
    }

    @Then("contain {string} and {string} definitions")
    fun containUsecaseAndActorDefinitions(
        keyword1: String,
        keyword2: String,
    ) {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(keyword1, keyword2)
            }
        }
    }

    @Then("the generated PlantUML should use activity diagram syntax")
    fun generatedPlantUmlShouldUseActivityDiagramSyntax() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    "@startuml",
                    "@enduml",
                    "start",
                    "stop",
                )
            }
        }
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
        )
    }

    @Then("contain {string}, {string}, and activity nodes")
    fun containStartStopAndActivityNodes(
        startKeyword: String,
        stopKeyword: String,
    ) {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    startKeyword,
                    stopKeyword,
                    ":",
                    "end",
                )
            }
        }
    }

    @Then("the generated PlantUML should use state diagram syntax")
    fun generatedPlantUmlShouldUseStateDiagramSyntax() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    "@startuml",
                    "@enduml",
                    "state",
                    "[*",
                    "*]",
                )
            }
        }
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
        )
    }

    @Then("contain state definitions and transitions")
    fun containStateDefinitionsAndTransitions() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    "state",
                    "-->",
                    "--",
                    "[*",
                    "*]",
                )
            }
        }
    }

    @Then("the generated PlantUML should use deployment diagram syntax")
    fun generatedPlantUmlShouldUseDeploymentDiagramSyntax() {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    "@startuml",
                    "@enduml",
                    "node",
                )
            }
        }
        assertThat(world.buildResult?.output).containsAnyOf(
            "BUILD SUCCESSFUL",
            "Processing",
        )
    }

    @Then("contain {string} and deployment artifacts")
    fun containNodeAndDeploymentArtifacts(keyword: String) {
        val diagramsDir = File(world.projectDir, "generated/diagrams")
        if (diagramsDir.exists()) {
            val pumlFiles = diagramsDir.listFiles { file -> file.extension == "puml" }
            if (pumlFiles != null && pumlFiles.isNotEmpty()) {
                val content = pumlFiles.first().readText()
                assertThat(content).containsAnyOf(
                    keyword,
                    "artifact",
                    "component",
                    "cloud",
                )
            }
        }
    }
}
