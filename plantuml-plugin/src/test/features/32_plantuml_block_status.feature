@plantuml-blocks
Feature: PlantUML block translation status (EPIC GBL-010 G-3b)

  The plantuml borough exposes, for each `[plantuml]` block, a translation status
  (MISSING / PENDING / TRANSLATED / BROKEN) plus the reference toolchain version, so
  the unified translation tree (G-4) can consume the plantuml leaf.

  @plantuml-blocks @status
  Scenario: A blank block is MISSING
    Given a plantuml block source ""
    When the block status is audited against an accepting oracle
    Then the block status is "MISSING"

  @plantuml-blocks @status
  Scenario: A block accepted by the oracle is TRANSLATED
    Given a plantuml block source "@startuml\nclass Car\n@enduml"
    When the block status is audited against an accepting oracle
    Then the block status is "TRANSLATED"

  @plantuml-blocks @status
  Scenario: A block rejected by the oracle is PENDING
    Given a plantuml block source "@startuml\nclass Car\n@enduml"
    When the block status is audited against a rejecting oracle
    Then the block status is "PENDING"

  @plantuml-blocks @status
  Scenario: A block whose quoted label spans a newline is BROKEN
    Given a plantuml block with a broken quoted label
    When the block status is audited against an accepting oracle
    Then the block status is "BROKEN"

  @plantuml-blocks @report
  Scenario: The report exposes the owner and the toolchain version
    Given a plantuml block source "@startuml\nclass Car\n@enduml"
    When the block status is audited against an accepting oracle
    And the audited block is added to the report under ref "diagram-1"
    Then the report JSON contains the owner "plantuml"
    And the report JSON exposes the toolchain version
