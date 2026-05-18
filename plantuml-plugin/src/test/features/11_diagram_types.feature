@diagrams
Feature: Diagram Types

  @diagrams @sequence
  Scenario: Generate sequence diagram
    Given a prompt file "sequence.prompt" with content:
      """
      Create a sequence diagram showing user login flow
      """
    When I run generatePlantumlDiagrams task
    Then the generated PlantUML should use sequence diagram syntax
    And contain "participant" or "actor" keywords

  @diagrams @class
  Scenario: Generate class diagram
    Given a prompt file "class.prompt" with content:
      """
      Create a class diagram for a library management system
      """
    When I run generatePlantumlDiagrams task
    Then the generated PlantUML should use class diagram syntax
    And contain "class" definitions with relationships

  @diagrams @component
  Scenario: Generate component diagram
    Given a prompt file "component.prompt" with content:
      """
      Create a component diagram for microservices architecture
      """
    When I run generatePlantumlDiagrams task
    Then the generated PlantUML should use component diagram syntax
    And contain "component" or "[component]" notation

  @diagrams @usecase
  Scenario: Generate use case diagram
    Given a prompt file "usecase.prompt" with content:
      """
      Create a use case diagram for an e-commerce system
      """
    When I run generatePlantumlDiagrams task
    Then the generated PlantUML should use use case diagram syntax
    And contain "usecase" and "actor" definitions

  @diagrams @activity
  Scenario: Generate activity diagram
    Given a prompt file "activity.prompt" with content:
      """
      Create an activity diagram for order processing workflow
      """
    When I run generatePlantumlDiagrams task
    Then the generated PlantUML should use activity diagram syntax
    And contain "start", "stop", and activity nodes

  @diagrams @state
  Scenario: Generate state diagram
    Given a prompt file "state.prompt" with content:
      """
      Create a state diagram for a traffic light system
      """
    When I run generatePlantumlDiagrams task
    Then the generated PlantUML should use state diagram syntax
    And contain state definitions and transitions

  @diagrams @deployment
  Scenario: Generate deployment diagram
    Given a prompt file "deployment.prompt" with content:
      """
      Create a deployment diagram for cloud infrastructure
      """
    When I run generatePlantumlDiagrams task
    Then the generated PlantUML should use deployment diagram syntax
    And contain "node" and deployment artifacts
