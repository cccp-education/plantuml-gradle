@incremental
Feature: Incremental Processing

  Background:
    Given a prompt file "incremental-test.prompt" with content "Create a diagram"

  @incremental @skip
  Scenario: Skip unchanged prompts on re-run
    Given a prompt file was already processed successfully
    And the prompt file has not been modified
    When I run generatePlantumlDiagrams task again
    Then the prompt should be skipped
    And output should indicate "UP-TO-DATE"

  @incremental @reprocess
  Scenario: Reprocess modified prompts
    Given a prompt file was already processed
    And the prompt file content has been modified
    When I run generatePlantumlDiagrams task again
    Then the modified prompt should be reprocessed
    And new diagram should be generated

  @incremental @cleanup
  Scenario: Cleanup outputs when prompts are deleted
    Given 3 prompts have been processed with outputs generated
    And one prompt file is deleted
    When I run generatePlantumlDiagrams task
    Then the outputs for the deleted prompt should be removed
    And outputs for remaining prompts should be preserved

  @incremental @checksum
  Scenario: Use checksum-based change detection
    Given a prompt file with known content
    When I run generatePlantumlDiagrams task
    Then a checksum should be stored for the prompt
    And on re-run, checksum should be compared
    And processing should be skipped if checksum matches

  @incremental @force
  Scenario: Force reprocessing with clean flag
    Given prompts were already processed
    When I run generatePlantumlDiagrams task with --rerun-tasks
    Then all prompts should be reprocessed regardless of change status

  @incremental-rag @rag-reindex
  Scenario: RAG reindex skips puml when source prompt unchanged
    Given a prompt file "rag-inc-test.prompt" with content "Create a diagram for RAG"
    And a puml file "rag-inc-test.puml" exists in the RAG directory
    And a checksum is stored for the prompt
    When I run collectPlantumlIndex task in simulation mode
    Then the puml file should be skipped in RAG reindex

  @incremental-rag @rag-reindex
  Scenario: RAG reindex processes puml when source prompt changed
    Given a prompt file "rag-inc-test.prompt" with content "Create a diagram for RAG"
    And a puml file "rag-inc-test.puml" exists in the RAG directory
    And a checksum is stored for the prompt
    And the prompt file content is modified to "Create a different diagram for RAG"
    When I run collectPlantumlIndex task in simulation mode
    Then the puml file should be reindexed in RAG
