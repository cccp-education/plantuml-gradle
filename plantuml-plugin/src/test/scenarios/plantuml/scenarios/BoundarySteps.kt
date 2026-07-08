package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.PlantumlMessages
import plantuml.boundary.GlossaryEntry
import plantuml.boundary.IdiomaticGlossary
import plantuml.boundary.TextClassifier
import plantuml.boundary.TranslationResolver
import plantuml.boundary.TranslationStrategy

class BoundarySteps(private val world: PlantumlWorld) {

    @Given("a translation resolver with a FR glossary")
    fun aTranslationResolverWithFrGlossary() {
        val glossary = IdiomaticGlossary().apply {
            register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))
            register("rollback", "fr", GlossaryEntry("rollback", TranslationStrategy.BORROW))
            register("dependency injection", "fr", GlossaryEntry("injection de dépendances", TranslationStrategy.TRANSLATE))
        }
        world.boundaryResolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = glossary,
            messageResolver = { key, language -> runCatching { PlantumlMessages.get(key, language) }.getOrNull() }
        )
    }

    @When("the resolver resolves {string} in language {string}")
    fun theResolverResolves(text: String, language: String) {
        world.boundaryResult = world.boundaryResolver!!.resolve(text, language)
    }

    @Then("the translated text should be {string}")
    fun theTranslatedTextShouldBe(expected: String) {
        assertThat(world.boundaryResult!!.translated).isEqualTo(expected)
    }

    @Then("the translated text should be {string} in FR")
    fun theTranslatedTextShouldBeInFr(expected: String) {
        assertThat(world.boundaryResult!!.translated).isEqualTo(expected)
    }

    @Then("the strategy should be {word}")
    fun theStrategyShouldBe(strategy: String) {
        assertThat(world.boundaryResult!!.strategy)
            .isEqualTo(TranslationStrategy.valueOf(strategy))
    }

    @Then("the category should be {word}")
    fun theCategoryShouldBe(category: String) {
        assertThat(world.boundaryResult!!.category)
            .isEqualTo(plantuml.boundary.TranslationCategory.valueOf(category))
    }
}