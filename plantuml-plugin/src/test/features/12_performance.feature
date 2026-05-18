@wip @performance
Feature: Performance and Load Testing

  @performance @batch
  Scenario: Process batch of 10 prompts
    Given 10 prompt files exist in the prompts directory
    When I run generatePlantumlDiagrams task for batch processing
    Then all 10 prompts should be processed
    And the total execution time should be under 5 minutes
    And memory usage should remain stable

  @performance @concurrent
  Scenario: Handle concurrent task execution
    Given multiple Gradle processes invoke generatePlantumlDiagrams
    When tasks run in parallel
    Then each process should complete without conflicts
    And outputs should not be corrupted

  @performance @memory
  Scenario: Manage memory with large prompt batches
    Given 20 prompt files with complex requirements
    When I run generatePlantumlDiagrams task for batch processing
    Then memory should be released between prompts
    And no OutOfMemoryError should occur

  @performance @cache
  Scenario: Utilize Gradle build cache effectively
    Given prompts were processed and cached
    When I run generatePlantumlDiagrams task with --build-cache
    Then the task should be loaded from cache
    And execution time should be under 10 seconds

  @performance @cleanup
  Scenario: Clean up temporary files after batch processing
    Given a batch of 10 prompts was processed
    When processing completes
    Then no temporary files should remain in /tmp
    And only final outputs should exist in generated/
