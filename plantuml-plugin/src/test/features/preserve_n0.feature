@preserve
Feature: MEM-I18N-PRESERVE — what a translation must never touch

  The shared N0 catalog (`contracts.i18n.PreserveRule`) defines the AsciiDoc
  markers a translation provider must never receive: a translated `toc::[]`
  broke 142 published files. Two consumers implement the rule on two formats
  (document on article blocks, plantuml on diagram labels) and replay the same
  vectors.

  Scenario: An asciidoc macro is shielded
    Given a preserve label "Voir toc::[] pour la table"
    When the preserve shield protects the label
    Then the preserve shield protected "toc::[]"
    And the preserve label is not fully shielded

  Scenario: An anchor and a url are shielded
    Given a preserve label "Page [[page-10]] see https://example.com/x"
    When the preserve shield protects the label
    Then the preserve shield protected "[[page-10]]"
    And the preserve shield protected "https://example.com/x"

  Scenario: A pure marker fragment is fully shielded
    Given a preserve label "`gradle build`"
    When the preserve shield protects the label
    Then the preserve label is fully shielded

  Scenario: Prose stays translatable
    Given a preserve label "Bonjour le monde"
    When the preserve shield protects the label
    Then the preserve label is not fully shielded
    And the preserve shield protected nothing

  Scenario: Restoring the shielded label is byte-identical
    Given a preserve label "Le type integer est utilisé par toc::[]"
    When the preserve shield protects the label
    Then restoring the shielded label is byte-identical
