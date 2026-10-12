@agent-loop
Feature: Agentic loop hardening (EPIC PLT-AGENT)

  Portable techniques ported from docToolchain/diag-agent, applied on HTOWN's
  native infrastructure — no Kroki, no MCP. See
  `.agents/CADRAGE_EPIC_PLT_AGENT.adoc` and `.agents/CADRAGE_EPIC_PLT_AGENT_VISION.adoc`.

  @agent-loop @extract
  Scenario: A fenced code block is stripped
    Given the LLM response is "```plantuml\n@startuml\nA --> B\n@enduml\n```"
    When the LLM code extractor strips fences
    Then the extracted code is "@startuml\nA --> B\n@enduml"

  @agent-loop @extract
  Scenario: A fence surrounded by prose is stripped
    Given the LLM response is "Here:\n```\n@startuml\nX\n@enduml\n```\nDone"
    When the LLM code extractor strips fences
    Then the extracted code is "@startuml\nX\n@enduml"

  @agent-loop @extract
  Scenario: Raw PlantUML is cut from surrounding prose
    Given the LLM response is "Sure:\n@startuml\nA --> B\n@enduml\nDone"
    When the LLM code extractor cuts raw PlantUML
    Then the extracted code is "@startuml\nA --> B\n@enduml"

  @agent-loop @budget
  Scenario: Under both limits the budget allows another correction
    Given a generation budget of "5" iterations and "60" seconds
    When "4" corrections have been done after "59" seconds
    Then the budget stop reason is "none"

  @agent-loop @budget
  Scenario: The iteration limit stops the loop
    Given a generation budget of "5" iterations and "0" seconds
    When "5" corrections have been done after "1" seconds
    Then the budget stop reason is "MAX_ITERATIONS"

  @agent-loop @budget
  Scenario: The wall-clock limit stops the loop
    Given a generation budget of "10" iterations and "30" seconds
    When "1" corrections have been done after "30" seconds
    Then the budget stop reason is "MAX_TIME"

  @agent-loop @subtype
  Scenario: A C4 context description is detected
    Given the diagram description "C4 context diagram for the API gateway"
    Then the detected subtype is "CONTEXT"

  @agent-loop @subtype
  Scenario: A sequence description is detected
    Given the diagram description "Sequence diagram of the login flow"
    Then the detected subtype is "SEQUENCE"

  @agent-loop @subtype
  Scenario: An unknown description yields no subtype
    Given the diagram description "just draw something"
    Then the detected subtype is "UNKNOWN"
