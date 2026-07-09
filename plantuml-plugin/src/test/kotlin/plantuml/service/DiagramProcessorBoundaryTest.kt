package plantuml.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import plantuml.boundary.IdiomaticGlossary
import plantuml.boundary.TextClassifier
import plantuml.boundary.TranslationResolver
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagramProcessorBoundaryTest {

    private val logger = LoggerFactory.getLogger(DiagramProcessorBoundaryTest::class.java)
    private lateinit var plantumlService: PlantumlService
    private lateinit var processor: DiagramProcessor

    @BeforeEach
    fun setUp() {
        plantumlService = Mockito.mock(PlantumlService::class.java)
        Mockito.`when`(plantumlService.validateSyntax(Mockito.anyString()))
            .thenReturn(PlantumlService.SyntaxValidationResult.Valid)
        processor = DiagramProcessor(plantumlService, null, null)
    }

    @Test
    fun `should translate Generated Diagram title when resolver provided`() {
        val resolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = IdiomaticGlossary(),
            messageResolver = { key, _ ->
                when (key) {
                    "label.generated.diagram" -> "Diagramme généré"
                    else -> null
                }
            }
        )

        val result = processor.processPrompt(
            prompt = "",
            logger = logger,
            resolver = resolver,
            language = "fr"
        )

        assertNotNull(result)
        assertTrue(result.plantuml.code.contains("Diagramme généré"))
    }

    @Test
    fun `should translate System rectangle label when resolver provided`() {
        val resolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = IdiomaticGlossary(),
            messageResolver = { _, _ -> null }
        )

        val result = processor.processPrompt(
            prompt = "Create a user diagram",
            logger = logger,
            resolver = resolver,
            language = "fr"
        )

        assertNotNull(result)
        assertTrue(result.plantuml.code.contains("rectangle \"System\""))
    }

    @Test
    fun `should preserve prompt text in generated diagram when resolver provided`() {
        val resolver = TranslationResolver(
            classifier = TextClassifier(),
            glossary = IdiomaticGlossary(),
            messageResolver = { _, _ -> null }
        )

        val result = processor.processPrompt(
            prompt = "Create a user diagram",
            logger = logger,
            resolver = resolver,
            language = "fr"
        )

        assertNotNull(result)
        assertTrue(result.plantuml.description.contains("Create a user diagram"))
    }
}