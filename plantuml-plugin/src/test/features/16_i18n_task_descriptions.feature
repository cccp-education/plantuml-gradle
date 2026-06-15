@i18n @task-descriptions
Feature: I18N Task Descriptions — Gradle tasks output

  Background:
    Given a new Plantuml project

  @i18n @task-descriptions-fr
  Scenario: Task descriptions are i18n-ized in gradle tasks output (French)
    Given plantuml-context.yml specifies language "fr"
    When I run the "tasks" task
    Then the build should succeed
    And the output should contain the i18n task description for "generatePlantumlDiagrams" in language "fr"

  @i18n @task-descriptions-en
  Scenario: Task descriptions are i18n-ized in gradle tasks output (English default)
    When I run the "tasks" task
    Then the build should succeed
    And the output should contain the i18n task description for "generatePlantumlDiagrams" in language "en"
