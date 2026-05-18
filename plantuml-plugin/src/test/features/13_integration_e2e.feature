@wip @integration @e2e
Feature: End-to-End Integration Tests

  @e2e @full-pipeline
  Scenario: Full pipeline with real Ollama instance
    Given a real Ollama instance is running with smollm:135m model
    And a prompt file "e2e-test.prompt" with content:
      """
      Create a complete architecture diagram for a banking system with:
      - User authentication service
      - Account management service
      - Transaction processing service
      - Notification service
      Show the relationships and data flow between services.
      """
    When I run generatePlantumlDiagrams task with provider "ollama"
    And I run validatePlantumlSyntax task
    And I run collectPlantumlIndex task
    Then a valid PlantUML diagram should be generated
    And a PNG image should be created
    And embeddings should be stored in pgvector
    And the prompt file should be deleted

  @e2e @multi-provider
  Scenario: Switch between multiple LLM providers
    Given prompts configured for different providers
    And API keys are configured for OpenAI and Gemini
    When I run generatePlantumlDiagrams with provider "ollama"
    And I run generatePlantumlDiagrams with provider "openai"
    And I run generatePlantumlDiagrams with provider "gemini"
    Then all three executions should succeed
    And each should use the correct provider
    And outputs should be in separate directories

  @e2e @ci-cd
  Scenario: Run in CI/CD environment
    Given a clean checkout with no cached Gradle files
    And Docker is available for pgvector
    When I run ./gradlew check --no-daemon
    Then all tests should pass
    And cucumber tests should complete
    And no manual intervention should be required

  @e2e @upgrade
  Scenario: Handle plugin version upgrade
    Given a project was built with plugin version 0.0.4
    When the project is upgraded to version 0.0.5
    And generatePlantumlDiagrams task is run
    Then the upgrade should complete without errors
    And existing outputs should be preserved or migrated
    And new features should be available
