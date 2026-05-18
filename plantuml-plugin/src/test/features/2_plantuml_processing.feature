Feature: PlantUML Processing

  Scenario: Process valid prompt file with mock LLM
    Given a prompt file "simple-diagram.prompt" with content "Create a simple user diagram"
    And a mock LLM that returns a valid PlantUML diagram
    When I run generatePlantumlDiagrams task
    Then a PlantUML diagram should be generated
    And a PNG image should be created
    And the prompt file should be deleted

  Scenario: Handle syntax error correction
    Given a prompt file "complex-diagram.prompt" with content "Create a complex architecture diagram"
    And a mock LLM that returns a valid PlantUML diagram
    When I run generatePlantumlDiagrams task with max 2 iterations
    Then the LLM should correct the syntax after iteration
    And a valid diagram should be generated
    And validation feedback should be saved
#
  Scenario: Process multiple prompt files
    Given a prompt file "diagram1.prompt" with content "Create diagram 1"
    And a prompt file "diagram2.prompt" with content "Create diagram 2"
    And a mock LLM that returns a valid PlantUML diagram
    When I run generatePlantumlDiagrams task
    Then a PlantUML diagram should be generated
    And a PNG image should be created
