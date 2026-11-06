@plantuml-notes
Feature: PlantUML note extraction (EPIC GBL-010 G-3)

  The plantuml leaf surfaces the translatable note texts (neither quoted labels nor
  directives), so the translation / unified tree (G-4) can handle them.

  @plantuml-notes
  Scenario: A single-line note is extracted
    Given a note block containing "note left of Car : This is a note"
    When the note texts are extracted
    Then the note texts contain "This is a note"

  @plantuml-notes
  Scenario: A multi-line note body is extracted
    Given a multi-line note block
    When the note texts are extracted
    Then the note texts contain "line one"

  @plantuml-notes
  Scenario: A block without a note yields no note text
    Given a note block containing "class Car"
    When the note texts are extracted
    Then the note texts are empty

  @plantuml-notes
  Scenario: A quoted floating note is one single-line note
    Given a quoted note block followed by a sequence diagram
    When the note texts are extracted
    Then the note texts contain "Warning: see below"
    And the note extraction reports a quoted note

  @plantuml-notes @roundtrip
  Scenario: A note translation that breaks syntax is never published
    Given a PlantUML block with a multi-line note "Bonjour"
    And a label provider returning "end note" for "Bonjour"
    And a syntax validator rejecting the translated block
    When the PlantUML translation port translates the block from "fr" to "en"
    Then the translation outcome should be Preserved
    And the preservation reason should mention syntax

  @plantuml-notes @roundtrip
  Scenario: A literal newline escape in a note survives the translation
    Given a PlantUML block with a single-line note "query\nmodèles"
    And a label provider returning "query__PLT0__models" for "query__PLT0__modèles"
    And a syntax validator accepting the translated block
    When the PlantUML translation port translates the block from "fr" to "en"
    Then the translated block should contain "query\nmodels"
