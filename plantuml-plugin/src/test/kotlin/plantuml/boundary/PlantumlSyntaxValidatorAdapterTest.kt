package plantuml.boundary

import contracts.plantuml.SyntaxValidationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlantumlSyntaxValidatorAdapterTest {

    private val validator = PlantumlSyntaxValidatorAdapter()

    @Test
    fun `valid plantuml passes`() {
        val result = validator.validate("@startuml\nA --> B\n@enduml")
        assertTrue(result is SyntaxValidationResult.Valid)
    }

    @Test
    fun `missing enduml fails`() {
        val result = validator.validate("@startuml\nA --> B")
        assertTrue(result is SyntaxValidationResult.Invalid)
        assertEquals(
            "Missing @startuml or @enduml tags",
            (result as SyntaxValidationResult.Invalid).errorMessage,
        )
    }

    @Test
    fun `implements the N0 port interface`() {
        val port: contracts.plantuml.PlantUmlSyntaxValidator = validator
        assertTrue(port.validate("@startuml\n@enduml") is SyntaxValidationResult.Valid)
    }
}
