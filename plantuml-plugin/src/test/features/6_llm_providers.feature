@llm
Feature: LLM Providers

  Background:
    Given a prompt file "provider-test.prompt" with content "Create a simple class diagram"

  @llm @ollama
  Scenario: Generate diagram with Ollama (local model)
    Given Ollama is running locally with model "smollm:135m"
    When I run generatePlantumlDiagrams task with provider "ollama"
    Then a PlantUML diagram should be generated
    And the generation should complete without API key

  @llm @openai
  Scenario: Generate diagram with OpenAI
    Given OpenAI API key is configured
    And a prompt file "openai-test.prompt" with content "Create a sequence diagram"
    When I run generatePlantumlDiagrams task with provider "openai" and model "gpt-4o-mini"
    Then a PlantUML diagram should be generated
    And the API usage should be logged

  @llm @gemini
  Scenario: Generate diagram with Google Gemini
    Given Gemini API key is configured
    And a prompt file "gemini-test.prompt" with content "Create a component diagram"
    When I run generatePlantumlDiagrams task with provider "gemini" and model "gemini-2.5-flash"
    Then a PlantUML diagram should be generated
    And the response time should be under 10 seconds

  @llm @mistral
  Scenario: Generate diagram with Mistral AI
    Given Mistral API key is configured
    And a prompt file "mistral-test.prompt" with content "Create a use case diagram"
    When I run generatePlantumlDiagrams task with provider "mistral" and model "mistral-small-latest"
    Then a PlantUML diagram should be generated

  @llm @claude
  Scenario: Generate diagram with Anthropic Claude
    Given Claude API key is configured
    And a prompt file "claude-test.prompt" with content "Create an activity diagram"
    When I run generatePlantumlDiagrams task with provider "claude" and model "claude-3-haiku-20240307"
    Then a PlantUML diagram should be generated

  @llm @fallback
  Scenario: Fallback to next provider when one fails
    Given primary provider "openai" is unavailable
    And fallback provider "ollama" is configured
    When I run generatePlantumlDiagrams task
    Then the system should fallback to ollama
    And a PlantUML diagram should still be generated
