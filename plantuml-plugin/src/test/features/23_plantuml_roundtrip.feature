@plantuml-roundtrip
Feature: PlantUML translation round-trip — a broken translation is never published

  The N0 translation port guarantees that a translated diagram block still
  parses. When a provider returns text that breaks PlantUML syntax, the source
  block is preserved verbatim (EPIC PLT-DIAGRAM-OWNERSHIP US-3, D5).

  Background:
    Given a PlantUML block with a quoted label "Utilisateur"

  @roundtrip @preserve
  Scenario: A translation that breaks syntax is rejected
    Given a label provider returning "User\" broken" for "Utilisateur"
    And a syntax validator rejecting the translated block
    When the PlantUML translation port translates the block from "fr" to "en"
    Then the translation outcome should be Preserved
    And the preservation reason should mention syntax

  @roundtrip @translated
  Scenario: A valid translation is published
    Given a label provider returning "User" for "Utilisateur"
    And a syntax validator accepting the translated block
    When the PlantUML translation port translates the block from "fr" to "en"
    Then the translation outcome should be Translated
    And the translated block should contain "User"

  @roundtrip @escape
  Scenario: A raw newline returned by the provider is escaped
    Given a label provider returning "a\nb" for "Utilisateur"
    And a syntax validator accepting the translated block
    When the PlantUML translation port translates the block from "fr" to "en"
    Then the translated block should contain "\n"
