@config
Feature: Configuration Edge Cases

  @config @missing
  Scenario: Handle missing configuration file
    Given no plantuml-config.yml exists
    When I run generatePlantumlDiagrams task
    Then a default configuration should be created
    And the task should complete successfully with defaults

  @config @invalid
  Scenario: Handle invalid YAML syntax
    Given plantuml-config.yml contains malformed YAML
    When I run generatePlantumlDiagrams task
    Then the build should fail with clear YAML error
    And indicate the problematic line

  @config @custom-paths
  Scenario: Use custom input/output directories
    Given plantuml-config.yml specifies custom directories:
      | input  | my-prompts/    |
      | output | my-generated/  |
    And a mock LLM that returns a valid PlantUML diagram
    When I run generatePlantumlDiagrams task
    Then diagrams should be generated in "my-generated/"
    And images should be generated in "my-generated/images/"

  @config @env-vars
  Scenario: Override config with environment variables
    Given plantuml-config.yml specifies Ollama as provider
    And a mock LLM that returns a valid PlantUML diagram
    And environment variable PLANTUML_LLM_PROVIDER is set to "openai"
    When I run generatePlantumlDiagrams task
    Then OpenAI should be used instead of Ollama

  @config @cli-override
  Scenario: Override config with CLI properties
    Given plantuml-config.yml specifies maxIterations=3
    When I run generatePlantumlDiagrams task with -Pplantuml.langchain4j.maxIterations=10
    Then 10 iterations should be allowed

  @config @partial
  Scenario: Handle partial configuration
    Given plantuml-config.yml only specifies input directory
    When I run generatePlantumlDiagrams task
    Then default values should be used for unspecified settings
    And the task should complete successfully
