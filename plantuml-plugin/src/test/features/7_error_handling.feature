@error
Feature: Error Handling

  Background:
    Given a prompt file "error-test.prompt" with content "Create a diagram"

  @error @timeout
  Scenario: Handle LLM timeout gracefully
    Given a mock LLM that responds after 30 seconds
    When I run generatePlantumlDiagrams task with timeout 5 seconds
    Then the task should fail with timeout error
    And a retry should be attempted
    And after max retries, a clear error message should be displayed

  @error @ratelimit
  Scenario: Handle API rate limit errors
    Given a mock LLM that returns rate limit errors
    When I run generatePlantumlDiagrams task
    Then the system should implement exponential backoff
    And retry after the rate limit window
    And eventually succeed or fail with clear message

  @error @network
  Scenario: Handle network connectivity errors
    Given the LLM server is unreachable
    When I run generatePlantumlDiagrams task with invalid config
    Then the task should fail with connection error
    And suggest checking network connectivity

  @error @invalid-response
  Scenario: Handle invalid LLM response format
    Given a mock LLM that returns malformed JSON
    When I run generatePlantumlDiagrams task with invalid config
    Then the system should detect the invalid format
    And request correction from the LLM
    And fail with descriptive error after max attempts

  @error @docker
  Scenario: Handle pgvector container startup failure
    Given Docker is available but port 5432 is in use
    When I run collectPlantumlIndex task with port conflict simulation
    Then the task should fail with port conflict error
    And suggest using a different port or stopping existing PostgreSQL

  @error @disk
  Scenario: Handle disk space exhaustion
    Given the output directory has insufficient disk space
    When I run generatePlantumlDiagrams task with invalid config
    Then the task should fail with disk space error
    And clean up any partial outputs

  @error @invalid-config
  Scenario: Handle missing configuration file
    Given the plantuml-config.yml file is missing
    When I run generatePlantumlDiagrams task with invalid config
    Then the task should create a default configuration
    And log a warning about using defaults

  @error @invalid-yaml
  Scenario: Handle invalid YAML configuration
    Given the plantuml-config.yml contains invalid YAML syntax
    When I run generatePlantumlDiagrams task with invalid config
    Then the task should fail with YAML parse error
    And indicate the line and nature of the error
