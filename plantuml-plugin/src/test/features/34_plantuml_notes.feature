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
