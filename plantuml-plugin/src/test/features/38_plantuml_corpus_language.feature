@plantuml-corpus-language
Feature: PlantUML corpus language measurement (EPIC PLT-I18N-OUTPUT, PLT-OUT-4)

  The corpus gate measures the blocks whose prose still looks like the source
  language. The measurement is deterministic, never blocking, and the syntax
  gate keeps its verdict.

  @plantuml-corpus-language
  Scenario: A French label in an English document is measured
    Given a language corpus document "en/article.adoc" with body "class \"Utilisateur\""
    When the corpus language gate runs
    Then the corpus language report has 1 finding
    And the corpus syntax gate is clean
    And the corpus language finding has evidence "word:utilisateur"

  @plantuml-corpus-language
  Scenario: A clean English document is not measured
    Given a language corpus document "en/article.adoc" with body "class \"User\""
    When the corpus language gate runs
    Then the corpus language report has 0 findings

  @plantuml-corpus-language
  Scenario: A French document is never measured
    Given a language corpus document "fr/article.adoc" with body "class \"Utilisateur\""
    When the corpus language gate runs
    Then the corpus language report has 0 findings

  @plantuml-corpus-language
  Scenario: A source language without a profile is never measured
    Given a language corpus document "en/article.adoc" with body "class \"Utilisateur\""
    And the corpus language source is "de"
    When the corpus language gate runs
    Then the corpus language report has 0 findings

  @plantuml-corpus-language
  Scenario: The language report exposes the measured document in JSON
    Given a language corpus document "en/article.adoc" with body "class \"Utilisateur\""
    When the corpus language gate runs
    Then the corpus language report JSON names "en/article.adoc"
    And the corpus language report JSON has a total of 1
