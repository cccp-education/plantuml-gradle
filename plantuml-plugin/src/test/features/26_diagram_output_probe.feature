@diagram-probe
Feature: Diagram output probe — a white/failed diagram is detected (GBL-007 PLT-CR3-2)

  `PlantumlService.generateImage` silently writes its textual fallback into the
  image on failure. The probe verifies the artifact is a real, non-empty image
  whose PlantUML source still parses.

  @diagram-probe @rendered
  Scenario: A valid PNG whose source parses is accepted
    Given a diagram image whose bytes start with the PNG signature
    And the diagram source "@startuml\nA --> B\n@enduml" parses
    When the diagram output probe checks the image
    Then the probe verdict should be Rendered

  @diagram-probe @empty
  Scenario: An empty image is rejected
    Given an empty diagram image
    When the diagram output probe checks the image
    Then the probe verdict should be EmptyImage

  @diagram-probe @fallback
  Scenario: The textual fallback written by generateImage is detected
    Given a diagram image containing the generateImage textual fallback
    When the diagram output probe checks the image
    Then the probe verdict should be NotAnImage

  @diagram-probe @invalid-syntax
  Scenario: A PNG whose source no longer parses is rejected
    Given a diagram image whose bytes start with the PNG signature
    And the diagram source "not plantuml" does not parse
    When the diagram output probe checks the image
    Then the probe verdict should be InvalidSyntax
