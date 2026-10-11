@plantuml-repair
Feature: PlantUML label integrity — a broken quoted label is detected and repaired

  CHE-DIAGRAM proved the D2 break: a translation that introduces a real newline
  inside a quoted label breaks PlantUML, the block degrades to a literal listing,
  and the variant ships no diagram. The plantuml borough owns the rule: it detects
  the frozen block at source (GBL-010 / G-3) and re-escapes it (D5).

  Scenario: A quoted label spanning a real newline is broken
    Given a PlantUML block whose quoted label spans a real newline
    Then the label integrity reports the block broken

  Scenario: A quoted label using the escape is intact
    Given a PlantUML block whose quoted label uses the escape
    Then the label integrity reports the block intact

  Scenario: Repairing a broken block re-escapes the label
    Given a PlantUML block whose quoted label spans a real newline
    When the label integrity repairs the block
    Then the repaired block is intact
    And the repaired block contains "a\nb"
