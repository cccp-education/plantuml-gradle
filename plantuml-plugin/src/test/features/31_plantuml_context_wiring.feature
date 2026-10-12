@context
Feature: Augmented context wiring into the generation prompt (EPIC PLT-CONTEXT CTX-04)

  The generation task reads the collected augmented context
  (build/plantuml/augmented-context.txt) and injects it into the LLM prompt, while
  a missing context leaves the prompt untouched (offline fallback, D-3).

  @context @wiring
  Scenario: The collected context is injected ahead of the generation prompt
    Given an augmented context "EAGER rules: only valid PlantUML"
    When the generation prompt "Generate a class diagram" is composed
    Then the composed prompt contains the context header
    And the composed prompt contains "EAGER rules: only valid PlantUML" before "Generate a class diagram"

  @context @wiring
  Scenario: A missing context leaves the generation prompt untouched
    Given an empty augmented context
    When the generation prompt "Generate a class diagram" is composed
    Then the composed prompt does not contain the context header
    And the composed prompt is "Generate a class diagram"
