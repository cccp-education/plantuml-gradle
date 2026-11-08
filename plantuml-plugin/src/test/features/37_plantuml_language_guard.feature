@plantuml-language
Feature: PlantUML source-language residue guard (EPIC PLT-I18N-OUTPUT, PLT-OUT-3)

  A provider that leaves a label in the source language must never be reported as
  translated. The guard is deterministic (no LLM), the retry is bounded, and a
  shield placeholder is never written into a diagram.

  @plantuml-language
  Scenario: A short label left in French is never accepted as translated
    Given a language guard block "class \"Utilisateur\""
    And the language guard translator always answers "Utilisateur."
    When the language guard translates the block
    Then the language guard block is preserved
    And the language guard used 2 provider attempts

  @plantuml-language
  Scenario: An accented French label left untouched is preserved
    Given a language guard block "A -> B : Créer le fichier"
    And the language guard translator always answers "Créer le fichier."
    When the language guard translates the block
    Then the language guard block is preserved

  @plantuml-language
  Scenario: A bounded retry recovers a real translation
    Given a language guard block "A -> B : Créer le fichier"
    And the language guard translator answers "Créer le fichier." then "Create the file"
    When the language guard translates the block
    Then the language guard block contains "A -> B : Create the file"
    And the language guard used 2 provider attempts

  @plantuml-language
  Scenario: A permanent residue stops at the bounded retry
    Given a language guard block "A -> B : Le fichier est prêt"
    And the language guard translator always answers "Le fichier est prêt."
    When the language guard translates the block
    Then the language guard block is preserved
    And the language guard used 2 provider attempts

  @plantuml-language
  Scenario: An English source translated to French is not flagged
    Given a language guard English block "A -> B : Create the file"
    And the language guard translator always answers "Créer le fichier"
    When the language guard translates the block
    Then the language guard block contains "A -> B : Créer le fichier"

  @plantuml-language
  Scenario: A provider-invented placeholder is never written
    Given a language guard block "class \"Bonjour\""
    And the language guard translator always answers "Hello __PLT0__"
    When the language guard translates the block
    Then the language guard block is preserved
    And the language guard reason mentions "placeholder"
