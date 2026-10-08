@toolchain
Feature: PlantUML toolchain governance — a single source of truth (GBL-007 US-5)

  The plantuml borough exposes the reference asciidoctor-diagram / plantuml
  versions (D6). Consumers (bakery, runner) align on it; the local catalog must
  never drift from it — the class of defect that produced the white-SVG D1.

  Background:
    Given the governed PlantUML toolchain

  @toolchain @reference
  Scenario: The reference exposes non-blank versions
    Then the reference toolchain versions should not be blank

  @toolchain @anti-drift
  Scenario: The local catalog matches the reference toolchain
    Then the local catalog should match the reference toolchain
