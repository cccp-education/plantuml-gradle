@plantuml-correction
Feature: PlantUML block correction (EPIC GBL-010 G-3)

  The plantuml leaf corrects the `[plantuml]` blocks that did not converge: a
  deterministic repair first (a raw newline inside a quoted label), then an optional
  AI-assisted correction — always behind the round-trip gate (a block that does not
  parse again is discarded and the source preserved).

  @plantuml-correction @deterministic
  Scenario: A broken quoted label is repaired deterministically
    Given a correction block with a broken quoted label
    When the correction block is corrected against an accepting oracle
    Then the correction strategy is "DETERMINISTIC"
    And the corrected block status is "TRANSLATED"
    And the corrected block has no broken quoted label

  @plantuml-correction @deterministic
  Scenario: An already valid block is preserved
    Given a correction source "@startuml\nclass Car\n@enduml"
    When the correction block is corrected against an accepting oracle
    Then the correction strategy is "PRESERVED"
    And the corrected block status is "TRANSLATED"

  @plantuml-correction @ai
  Scenario: A pending block is corrected by the AI seam
    Given a correction source "@startuml\nclass Broken\n@enduml"
    And an AI repairer proposing "@startuml\nclass FIXED\n@enduml"
    When the correction block is corrected against a fix-only oracle
    Then the correction strategy is "AI"
    And the corrected block status is "TRANSLATED"

  @plantuml-correction @ai
  Scenario: An AI correction that does not parse is discarded
    Given a correction source "@startuml\nclass Broken\n@enduml"
    And an AI repairer proposing "@startuml\nclass Still\n@enduml"
    When the correction block is corrected against a fix-only oracle
    Then the correction strategy is "PRESERVED"
    And the corrected block status is "PENDING"
