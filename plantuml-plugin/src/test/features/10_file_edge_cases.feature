@files
Feature: File Edge Cases

  @files @utf8
  Scenario: Handle UTF-8 encoded prompt files
    Given a prompt file "utf8-test.prompt" with UTF-8 content:
      """
      Créer un diagramme avec des caractères spéciaux: é à ü ñ 中文 日本語
      """
    When I run generatePlantumlDiagrams task
    Then the task should complete successfully
    And the generated diagram should preserve UTF-8 characters

  @files @large
  Scenario: Handle large prompt files
    Given a prompt file "large-test.prompt" with 10000+ characters
    When I run generatePlantumlDiagrams task
    Then the task should complete within reasonable time
    And the generated diagram should reflect all requirements

  @files @special-chars
  Scenario: Handle filenames with special characters
    Given a prompt file "my-complex diagram (v1.0).prompt" exists
    When I run generatePlantumlDiagrams task
    Then the task should handle the filename correctly
    And output files should use sanitized names

  @files @empty
  Scenario: Handle empty prompt files
    Given an empty prompt file "empty.prompt"
    When I run generatePlantumlDiagrams task
    Then the task should skip the empty file
    And log a warning about empty prompt

  @files @whitespace
  Scenario: Handle prompt files with only whitespace
    Given a prompt file containing only spaces and newlines
    When I run generatePlantumlDiagrams task
    Then the task should treat it as empty
    And skip processing

  @files @missing-newline
  Scenario: Handle files without trailing newline
    Given a prompt file without trailing newline
    When I run generatePlantumlDiagrams task
    Then the content should be read correctly
    And processing should succeed
