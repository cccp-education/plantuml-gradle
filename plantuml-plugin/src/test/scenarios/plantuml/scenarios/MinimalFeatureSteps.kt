package plantuml.scenarios

import io.cucumber.java.en.Then

class MinimalFeatureSteps(
    private val world: PlantumlWorld,
) {
    @Then("a PlantUML diagram should be generated")
    fun verifyPlantumlDiagramGenerated() {
        world.verifyDirectoryContainsFiles("generated/rag", "puml", "A PlantUML diagram should be generated")
    }
}
