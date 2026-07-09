package plantuml.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import plantuml.EdgeType
import plantuml.KnowledgeGraph
import plantuml.KnowledgeGraphCommunity
import plantuml.KnowledgeGraphNode
import plantuml.PlantumlMessages
import plantuml.boundary.GlossaryEntry
import plantuml.boundary.IdiomaticGlossary
import plantuml.boundary.NonTranslatableTermRegistry
import plantuml.boundary.TextClassifier
import plantuml.boundary.TranslationResolver
import plantuml.boundary.TranslationStrategy
import plantuml.service.DiagramProcessor
import plantuml.service.KnowledgeGraphRenderer
import plantuml.service.PlantumlService

class BoundarySteps(private val world: PlantumlWorld) {

    private val logger = org.slf4j.LoggerFactory.getLogger(BoundarySteps::class.java)

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

    @Given("a translation resolver with a FR glossary and non-translatable term {string}")
    fun aTranslationResolverWithFrGlossaryAndNonTranslatableTerm(term: String) {
        val glossary = IdiomaticGlossary().apply {
            register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))
            register("rollback", "fr", GlossaryEntry("rollback", TranslationStrategy.BORROW))
            register("dependency injection", "fr", GlossaryEntry("injection de dépendances", TranslationStrategy.TRANSLATE))
        }
        val registry = NonTranslatableTermRegistry().apply { register(term) }
        world.boundaryResolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = glossary,
            messageResolver = { key, language -> runCatching { PlantumlMessages.get(key, language) }.getOrNull() },
            nonTranslatableRegistry = registry
        )
    }

    @Given("a translation resolver with a FR glossary registering {string} as BORROW and non-translatable term {string}")
    fun aTranslationResolverWithFrGlossaryRegisteringBorrowAndNonTranslatableTerm(glossaryTerm: String, nonTranslatableTerm: String) {
        val glossary = IdiomaticGlossary().apply {
            register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))
            register("rollback", "fr", GlossaryEntry("rollback", TranslationStrategy.BORROW))
            register("dependency injection", "fr", GlossaryEntry("injection de dépendances", TranslationStrategy.TRANSLATE))
            register(glossaryTerm, "fr", GlossaryEntry("$glossaryTerm-emprunt", TranslationStrategy.BORROW))
        }
        val registry = NonTranslatableTermRegistry().apply { register(nonTranslatableTerm) }
        world.boundaryResolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = glossary,
            messageResolver = { key, language -> runCatching { PlantumlMessages.get(key, language) }.getOrNull() },
            nonTranslatableRegistry = registry
        )
    }

    @Given("a diagram processor in test mode with a FR resolver")
    fun aDiagramProcessorInTestModeWithFrResolver() {
        val plantumlService = org.mockito.Mockito.mock(PlantumlService::class.java)
        org.mockito.Mockito.`when`(plantumlService.validateSyntax(org.mockito.Mockito.anyString()))
            .thenReturn(PlantumlService.SyntaxValidationResult.Valid)
        world.boundaryResolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = IdiomaticGlossary(),
            messageResolver = { key, language -> runCatching { PlantumlMessages.get(key, language) }.getOrNull() }
        )
        world.boundaryDiagram = null
        world.i18nErrorPromptsDir = null
    }

    @When("the diagram processor processes prompt {string} in language {string}")
    fun theDiagramProcessorProcessesPrompt(prompt: String, language: String) {
        val plantumlService = org.mockito.Mockito.mock(PlantumlService::class.java)
        org.mockito.Mockito.`when`(plantumlService.validateSyntax(org.mockito.Mockito.anyString()))
            .thenReturn(PlantumlService.SyntaxValidationResult.Valid)
        val processor = DiagramProcessor(plantumlService, null, null)
        world.boundaryDiagram = processor.processPrompt(
            prompt = prompt,
            logger = logger,
            resolver = world.boundaryResolver,
            language = language
        )
    }

    @Then("the generated diagram title should be translated to {string} in FR")
    fun theGeneratedDiagramTitleShouldBeTranslatedToFr(expected: String) {
        val diagram = world.boundaryDiagram
        assertThat(diagram).isNotNull()
        assertThat(diagram!!.plantuml.code).contains(expected)
    }

    @Then("the generated diagram should contain the translated rectangle label {string}")
    fun theGeneratedDiagramShouldContainTranslatedRectangleLabel(expected: String) {
        val diagram = world.boundaryDiagram
        assertThat(diagram).isNotNull()
        assertThat(diagram!!.plantuml.code).contains("rectangle \"$expected\"")
    }

    @Then("the generated diagram description should preserve the prompt {string}")
    fun theGeneratedDiagramDescriptionShouldPreserveThePrompt(prompt: String) {
        val diagram = world.boundaryDiagram
        assertThat(diagram).isNotNull()
        assertThat(diagram!!.plantuml.description).contains(prompt)
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

    @Given("a full pipeline translation resolver with a FR glossary and a non-translatable registry containing {string}")
    fun aFullPipelineResolverWithFrGlossaryAndRegistry(term: String) {
        val glossary = IdiomaticGlossary().apply {
            register("pipeline", "fr", GlossaryEntry("pipeline", TranslationStrategy.BORROW))
            register("rollback", "fr", GlossaryEntry("rollback", TranslationStrategy.BORROW))
            register("dependency injection", "fr", GlossaryEntry("injection de dépendances", TranslationStrategy.TRANSLATE))
        }
        val registry = NonTranslatableTermRegistry().apply { register(term) }
        world.boundaryResolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = glossary,
            messageResolver = { key, language -> runCatching { PlantumlMessages.get(key, language) }.getOrNull() },
            nonTranslatableRegistry = registry
        )
    }

    @Given("a knowledge graph with nodes {string}, {string} and a community {string}")
    fun aKnowledgeGraphWithNodesAndCommunity(node1: String, node2: String, community: String) {
        val graph = KnowledgeGraph(
            nodes = listOf(
                KnowledgeGraphNode(name = node1, type = "class", community = community),
                KnowledgeGraphNode(name = node2, type = "class", community = community)
            ),
            edges = emptyList(),
            communities = listOf(
                KnowledgeGraphCommunity(name = community, nodes = listOf(node1, node2))
            )
        )
        world.boundaryGraph = graph
    }

    @When("the knowledge graph renderer renders the graph in language {string}")
    fun theKnowledgeGraphRendererRendersTheGraph(language: String) {
        val renderer = KnowledgeGraphRenderer()
        world.boundaryRenderedDiagram = renderer.render(
            graph = world.boundaryGraph!!,
            resolver = world.boundaryResolver,
            language = language
        )
    }

    @Then("the rendered diagram should translate the {string} folder label via messages")
    fun theRenderedDiagramShouldTranslateFolderLabel(label: String) {
        val rendered = world.boundaryRenderedDiagram!!
        val resolved = world.boundaryResolver!!.resolve(label, "fr")
        assertThat(resolved.strategy).isEqualTo(TranslationStrategy.TRANSLATE)
        assertThat(rendered).contains("folder \"${resolved.translated}\"")
    }

    @Then("the rendered diagram should preserve the {string} node identifier")
    fun theRenderedDiagramShouldPreserveNodeIdentifier(node: String) {
        val rendered = world.boundaryRenderedDiagram!!
        assertThat(rendered).contains(node)
    }

    @Then("the rendered diagram should borrow the {string} community name")
    fun theRenderedDiagramShouldBorrowCommunityName(community: String) {
        val rendered = world.boundaryRenderedDiagram!!
        val resolved = world.boundaryResolver!!.resolve(community, "fr")
        assertThat(resolved.strategy).isEqualTo(TranslationStrategy.BORROW)
        assertThat(resolved.translated).isEqualTo(community)
        assertThat(rendered).contains("package \"$community\"")
    }
}