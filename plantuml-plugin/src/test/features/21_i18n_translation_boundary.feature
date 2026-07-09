@i18n @boundary
Feature: I18N Translation Boundary — Diagram labels translated, identifiers preserved

  The translation boundary distinguishes three natures of text:
  - Presentation labels (translated word-for-word)
  - Semantic identity identifiers (preserved as-is)
  - Lexical field terms (translated idiomatically or borrowed)

  Background:
    Given a translation resolver with a FR glossary

  @boundary @presentation
  Scenario: Presentation label "Classes" is translated in FR
    When the resolver resolves "Classes" in language "fr"
    Then the translated text should be "Classes" in FR
    And the strategy should be TRANSLATE
    And the category should be PRESENTATION

  @boundary @presentation
  Scenario: Presentation label "Empty Knowledge Graph" falls back to source when no FR message
    When the resolver resolves "Empty Knowledge Graph" in language "fr"
    Then the translated text should be "Empty Knowledge Graph"
    And the strategy should be TRANSLATE
    And the category should be PRESENTATION

  @boundary @semantic-identity
  Scenario: Semantic identity "LlmService" is preserved in FR
    When the resolver resolves "LlmService" in language "fr"
    Then the translated text should be "LlmService"
    And the strategy should be PRESERVE
    And the category should be SEMANTIC_IDENTITY

  @boundary @semantic-identity
  Scenario: Semantic identity edge label "calls" is preserved in FR
    When the resolver resolves "calls" in language "fr"
    Then the translated text should be "calls"
    And the strategy should be PRESERVE
    And the category should be SEMANTIC_IDENTITY

  @boundary @lexical-field
  Scenario: Lexical field "pipeline" is borrowed in FR
    When the resolver resolves "pipeline" in language "fr"
    Then the translated text should be "pipeline"
    And the strategy should be BORROW
    And the category should be LEXICAL_FIELD

  @boundary @lexical-field
  Scenario: Lexical field "dependency injection" is translated in FR
    When the resolver resolves "dependency injection" in language "fr"
    Then the translated text should be "injection de dépendances"
    And the strategy should be TRANSLATE
    And the category should be LEXICAL_FIELD

  @boundary @lexical-field
  Scenario: Lexical field "rollback" is borrowed in FR
    When the resolver resolves "rollback" in language "fr"
    Then the translated text should be "rollback"
    And the strategy should be BORROW
    And the category should be LEXICAL_FIELD

  @boundary @semantic-identity
  Scenario: Mathematical formula is preserved in FR
    When the resolver resolves "E = mc^2" in language "fr"
    Then the translated text should be "E = mc^2"
    And the strategy should be PRESERVE
    And the category should be SEMANTIC_IDENTITY

  @boundary @diagram-processor
  Scenario: DiagramProcessor translates structural labels via resolver
    Given a diagram processor in test mode with a FR resolver
    When the diagram processor processes prompt "" in language "fr"
    Then the generated diagram title should be translated to "Generated Diagram" in FR
    And the generated diagram should contain the translated rectangle label "System"

  @boundary @non-translatable
  Scenario: Non-translatable term "REAC" is preserved even when classifier says presentation
    Given a translation resolver with a FR glossary and non-translatable term "REAC"
    When the resolver resolves "REAC" in language "fr"
    Then the translated text should be "REAC"
    And the strategy should be PRESERVE

  @boundary @non-translatable
  Scenario: Non-translatable term "AFNOR" is preserved even when glossary would borrow
    Given a translation resolver with a FR glossary registering "AFNOR" as BORROW and non-translatable term "AFNOR"
    When the resolver resolves "AFNOR" in language "fr"
    Then the translated text should be "AFNOR"
    And the strategy should be PRESERVE