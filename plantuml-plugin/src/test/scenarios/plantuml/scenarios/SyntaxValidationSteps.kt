package plantuml.scenarios

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat

class SyntaxValidationSteps(
    private val world: PlantumlWorld,
) {
    @When("I run validatePlantumlSyntax task with file {string}")
    fun runValidatePlantumlSyntaxTaskWithFile(fileName: String) =
        runBlocking {
            val properties = mapOf("plantuml.diagram" to "test-diagrams/$fileName")
            world.executeGradle("validatePlantumlSyntax", properties = properties)
        }

    @Then("the syntax should be reported as valid")
    fun verifySyntaxValid() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).doesNotContain("Invalid")
    }

    @Then("the syntax should be reported as invalid")
    fun verifySyntaxInvalid() {
        assertThat(world.buildResult).isNotNull
        assertThat(world.buildResult!!.output).containsAnyOf("Invalid", "Error")
    }
}
