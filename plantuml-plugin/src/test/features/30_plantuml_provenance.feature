@context
Feature: Augmented context provenance (EPIC PLT-CONTEXT CTX-03)

  The plantuml context collector tracks where each surviving context channel came
  from (its sources), so a generated diagram can be traced back to the governance
  files / injected channels that fed it (anti-hallucination, decision D-6).

  @context @provenance
  Scenario: The provenance lists the channel that survived the budget
    Given a provenance tracker with EAGER source "INDEX.adoc" and RAG source "rag-injected"
    When the plantuml context is assembled from RAG "chunk" with the provenance tracker
    Then the provenance contains the channel "RAG"
    And the provenance does not contain the channel "EAGER"

  @context @provenance
  Scenario: The provenance totals derive from the surviving sources
    Given a provenance tracker with EAGER source "INDEX.adoc" and RAG source "rag-injected"
    When the plantuml context is assembled from RAG "chunk" with the provenance tracker
    Then the provenance total character count is 12

  @context @provenance
  Scenario: The provenance serialises as JSON
    Given a provenance tracker with EAGER source "INDEX.adoc" and RAG source "rag-injected"
    When the plantuml context is assembled from RAG "chunk" with the provenance tracker
    Then the provenance JSON contains "rag-injected"
