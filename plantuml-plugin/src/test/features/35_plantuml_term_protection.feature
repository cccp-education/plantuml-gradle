@plantuml-term-protection
Feature: PlantUML term protection (EPIC PLT-TERM-PROTECTION)

  The article translation must translate the prose of a diagram label while
  keeping code and business tokens verbatim (primitive types, file names,
  identifiers). A provider that drops a placeholder never corrupts the block:
  the source label is preserved.

  @plantuml-term-protection
  Scenario: A primitive type is protected in a translated sequence message
    Given a protected sequence message "A -> B : Le type integer est utilisé"
    And the term translator maps "Le type __PLT0__ est utilisé" to "The type __PLT0__ is used"
    When the block is translated with term protection
    Then the protected block contains "A -> B : The type integer is used"

  @plantuml-term-protection
  Scenario: A file name is protected in a translated activity label
    Given a protected activity label ":Créer opencode.json;"
    And the term translator maps "Créer __PLT0__" to "Create __PLT0__"
    When the block is translated with term protection
    Then the protected block contains ":Create opencode.json;"

  @plantuml-term-protection
  Scenario: A dropped placeholder preserves the source label
    Given a protected sequence message "A -> B : Le type integer est utilisé"
    And the term translator maps "Le type __PLT0__ est utilisé" to "The type is used"
    When the block is translated with term protection
    Then the block translation is preserved

  @plantuml-term-protection
  Scenario: A use-case actor and use case are translated
    Given a protected use-case block ":Utilisateur: --> (Rédiger documentation)"
    And the term translator maps "Utilisateur" to "User"
    And the term translator maps "Rédiger documentation" to "Write documentation"
    When the block is translated with term protection
    Then the protected block contains ":User: --> (Write documentation)"

  @plantuml-term-protection
  Scenario: A mindmap node is translated and a product name is preserved
    Given a protected mindmap block
    And the term translator maps "Syntaxe" to "Syntax"
    When the block is translated with term protection
    Then the protected block contains "* Syntax"
    Then the protected block contains "** PlantUML"
