@i18n @language
Feature: I18N Language Switch — Configuration Resolution

  Background:
    Given a new Plantuml project
    And a mock LLM that returns a valid PlantUML diagram

  @i18n @default
  Scenario: Default language is English
    When I run generatePlantumlDiagrams task
    Then the build should succeed
    And the resolved language should be "en"

  @i18n @cli-override
  Scenario: Override language via CLI property
    When I run generatePlantumlDiagrams task with language override "fr"
    Then the build should succeed
    And the resolved language should be "fr"

  @i18n @yaml-config
  Scenario: Language from YAML configuration
    Given plantuml-context.yml specifies language "zh"
    And a prompt file exists in "prompts/diagram.prompt"
    When I run generatePlantumlDiagrams task
    Then the build should succeed
    And the resolved language should be "zh"

  @i18n @gradle-properties
  Scenario: Language from gradle.properties
    Given gradle.properties specifies plantuml.language=es
    And a prompt file exists in "prompts/diagram.prompt"
    When I run generatePlantumlDiagrams task
    Then the build should succeed
    And the resolved language should be "es"

  @i18n @env-var
  Scenario: Language from environment variable
    Given environment variable PLANTUML_LANGUAGE is set to "hi"
    And a prompt file exists in "prompts/diagram.prompt"
    When I run generatePlantumlDiagrams task
    Then the build should succeed
    And the resolved language should be "hi"

  @i18n @fallback
  Scenario: Unsupported language falls back to English
    When I run generatePlantumlDiagrams task with language override "xx"
    Then the build should succeed
    And the resolved language should be "en"

  @i18n @fallback-yaml
  Scenario: Unsupported language in YAML falls back to English
    Given plantuml-context.yml specifies language "invalid"
    And a prompt file exists in "prompts/diagram.prompt"
    When I run generatePlantumlDiagrams task
    Then the build should succeed
    And the resolved language should be "en"

  @i18n @supported-langs
  Scenario: Supported languages list is validated
    Given plantuml-context.yml specifies supportedLanguages "fr,es,zh"
    And a prompt file exists in "prompts/diagram.prompt"
    When I run generatePlantumlDiagrams task
    Then the build should succeed
    And the supported languages should include "fr", "es", and "zh"
