@cucumber @plantuml
Feature: Attempt History Tracking

  Scenario: Track successful diagram generation with corrections
    Given a prompt file "tracking-test.prompt" with content "Create a simple user diagram"
    And a mock LLM that returns a sequence of responses: invalid then valid
    When I run generatePlantumlDiagrams task
    Then attempt history should be tracked with 2 entries
    And the first entry should indicate syntax error
    And the second entry should indicate success
    And a valid diagram should be generated
    And a PNG image should be created

  Scenario: Archive history after max iterations with no success
    Given a prompt file "archive-test.prompt" with content "Create a complex diagram"
    And a mock LLM that always returns invalid PlantUML diagrams
    When I run generatePlantumlDiagrams task with max 5 iterations
    Then attempt history should be archived with 6 entries
    And no diagram should be generated
    And the prompt file should be deleted

  Scenario: Successful generation after multiple corrections
    Given a prompt file "multi-correction-test.prompt" with content "Create an architecture diagram"
    And a mock LLM that returns a sequence of 4 responses: 3 invalid then valid
    When I run generatePlantumlDiagrams task
    Then attempt history should be tracked with 4 entries
    And the first three entries should indicate syntax errors
    And the fourth entry should indicate success
    And a valid diagram should be generated
    And validation feedback should be saved