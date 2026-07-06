@cross-provider
Feature: Cross-provider Fallback

  Scenario: Primary provider key is returned when its pool is not saturated
    Given a cross-provider orchestrator with fallback order "gemini, mistral, ollama"
    And the "gemini" provider pool contains a FREE key "gemini-1"
    When I select the next cross-provider key
    Then the selected cross-provider key should be "gemini-1"

  Scenario: Orchestrator falls back to the next provider when the primary pool is saturated
    Given a cross-provider orchestrator with fallback order "gemini, mistral, ollama"
    And the "gemini" provider pool contains a FREE key "gemini-1" with quota limit 4 and threshold 50
    And the "mistral" provider pool contains a FREE key "mistral-1"
    When I select the next cross-provider key 3 times
    Then the 1st cross-provider key should be "gemini-1"
    And the 2nd cross-provider key should be "gemini-1"
    And the 3rd cross-provider key should be "mistral-1"

  Scenario: Orchestrator falls back across two saturated providers to the third
    Given a cross-provider orchestrator with fallback order "gemini, mistral, ollama"
    And the "gemini" provider pool contains a FREE key "gemini-1" with quota limit 4 and threshold 50
    And the "mistral" provider pool contains a FREE key "mistral-1" with quota limit 4 and threshold 50
    And the "ollama" provider pool contains a FREE key "ollama-1"
    When I select the next cross-provider key 5 times
    Then the 1st cross-provider key should be "gemini-1"
    And the 2nd cross-provider key should be "gemini-1"
    And the 3rd cross-provider key should be "mistral-1"
    And the 4th cross-provider key should be "mistral-1"
    And the 5th cross-provider key should be "ollama-1"

  Scenario: Orchestrator returns no key when every provider pool is saturated
    Given a cross-provider orchestrator with fallback order "gemini, mistral"
    And the "gemini" provider pool contains a FREE key "gemini-1" with quota limit 4 and threshold 50
    And the "mistral" provider pool contains a FREE key "mistral-1" with quota limit 4 and threshold 50
    When I select the next cross-provider key 5 times
    Then the 5th cross-provider key should be absent

  Scenario: Orchestrator skips providers absent from the pools map
    Given a cross-provider orchestrator with fallback order "gemini, mistral, ollama"
    And the "ollama" provider pool contains a FREE key "ollama-1"
    When I select the next cross-provider key
    Then the selected cross-provider key should be "ollama-1"