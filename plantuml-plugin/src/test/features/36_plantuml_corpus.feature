@plantuml-corpus
Feature: PlantUML corpus gate (EPIC PLT-I18N-OUTPUT, PLT-OUT-1)

  The syntax validator stops being a unit-level guard and becomes a blocking
  gate on the real corpus: every [plantuml] block of every variant is
  validated, and the report names the language, the article, the block id and
  the reason. A named baseline tolerates the known violations of a debt being
  paid down, but a new violation fails the gate.

  Scenario: A valid corpus passes
    Given a corpus document "fr/article.adoc" with a valid diagram "diagram-1"
    When the corpus gate validates the documents
    Then the corpus report has 1 block and 0 blocking finding

  Scenario: A broken diagram is a blocking finding named by article, id and reason
    Given a corpus document "en/article.adoc" with a broken diagram "diagram-2"
    When the corpus gate validates the documents
    Then the corpus report has 1 blocking finding
    And the corpus report names the document "en/article.adoc"
    And the corpus report names the block "diagram-2"
    And the corpus report names the language "en"
    And the corpus report names the reason "Broken diagram"

  Scenario: A named baseline tolerates a known violation
    Given a corpus document "en/article.adoc" with a broken diagram "diagram-2"
    And a corpus baseline accepting "en/article.adoc" block "diagram-2"
    When the corpus gate validates the documents
    Then the corpus report has 0 blocking finding
    And the corpus report has 1 baselined finding

  Scenario: A source plantuml listing is not a diagram
    Given a corpus document "fr/article.adoc" with a source plantuml listing
    When the corpus gate validates the documents
    Then the corpus report has 0 block and 0 blocking finding

  Scenario: The findings are ordered by document
    Given a corpus document "en/b.adoc" with a broken diagram "b"
    And a corpus document "fr/a.adoc" with a broken diagram "a"
    When the corpus gate validates the documents
    Then the corpus findings are ordered by document
