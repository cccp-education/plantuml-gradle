@i18n @errors
Feature: I18N Error Messages — Warn/Error/Exception messages internationalised

  Background:
    Given a new Plantuml project

  @i18n @errors @api-key-pool
  Scenario: ApiKeyPool empty exception message is i18nised
    Given an empty API key pool
    When the pool is asked for the next key
    Then the exception message should match the i18n key "apikey.pool_empty" in language "en"

  @i18n @errors @kg-parser
  Scenario: KnowledgeGraphParser file not found exception is i18nised
    Given a non-existent graph file path
    When the parser is invoked
    Then the exception message should match the i18n format key "kgparser.file_not_found" with args in language "en"

  @i18n @errors @graphify-adapter
  Scenario: GraphifyPromptAdapter no communities exception is i18nised
    Given a graph file without communities field
    When generateAllPrompts is invoked
    Then the exception message should match the i18n key "graphify.no_communities" in language "en"

  @i18n @errors @graphify-adapter
  Scenario: GraphifyPromptAdapter no community found exception is i18nised
    Given a graph file with null communities
    When generatePrompt is invoked for "service layer"
    Then the exception message should match the i18n format key "graphify.no_community" with args "service layer" in language "en"

  @i18n @errors @plantuml-manager
  Scenario: PlantumlManager invalid YAML exception is i18nised
    Given plantuml-context.yml with invalid YAML content
    When the configuration is loaded
    Then the exception message should contain the i18n key prefix "Invalid YAML configuration" in language "en"

  @i18n @errors @diagram-processor
  Scenario: DiagramProcessor invalid JSON exception is i18nised
    Given a JSON response without plantuml or code field
    When extractPlantUmlFromResponse is invoked
    Then the exception message should match the i18n format key "processor.invalid_json" with args in language "en"

  @i18n @errors @diagram-processor
  Scenario: DiagramProcessor malformed JSON exception is i18nised
    Given a malformed JSON response
    When extractPlantUmlFromResponse is invoked
    Then the exception message should match the i18n format key "processor.malformed_json" with args in language "en"