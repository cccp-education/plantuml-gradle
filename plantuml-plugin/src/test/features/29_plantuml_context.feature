@context
Feature: Augmented context assembly (EPIC PLT-CONTEXT)

  plantuml consumes the N0 `codebase-contracts` CompositeContext and renders it into a
  prompt-ready block (plantuml.context.PlantumlContextBuilder), mirroring the capsule
  patron. The augmentation is fed by the N1 codebase; plantuml never imports an N2 type.

  @context @assemble
  Scenario: Non-empty channels are rendered with their section headers
    Given a composite context with EAGER "rules" and RAG "chunk"
    When the plantuml context builder assembles it
    Then the augmented context contains "==== RÈGLES_EAGER (EAGER/LAZY)"
    And the augmented context contains "chunk"

  @context @assemble
  Scenario: Empty channels are dropped
    Given a composite context with RAG "only"
    When the plantuml context builder assembles it
    Then the augmented context has 1 non-empty channel

  @context @assemble
  Scenario: An all-blank composite context yields an empty context
    Given an empty composite context
    When the plantuml context builder assembles it
    Then the augmented context is empty
