@i18n @lifecycle-messages
Feature: I18N Lifecycle Messages — Logger output i18n

  Background:
    Given a new Plantuml project
    And a mock LLM that returns a valid PlantUML diagram

  @i18n @lifecycle-fr
  Scenario: Lifecycle messages are i18n-ized in French
    Given plantuml-context.yml specifies language "fr"
    And a prompt file exists in "prompts/diagram.prompt"
    When I run generatePlantumlDiagrams task
    Then the build should succeed
    And the output should contain the i18n lifecycle message "generate.processing" with args "[1]" in language "fr"

  @i18n @lifecycle-en
  Scenario: Lifecycle messages are i18n-ized in English (default)
    And a prompt file exists in "prompts/diagram.prompt"
    When I run generatePlantumlDiagrams task
    Then the build should succeed
    And the output should contain the i18n lifecycle message "generate.processing" with args "[1]" in language "en"
