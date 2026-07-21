@api-key-pool
Feature: API Key Pool Management

  Scenario: Initialize empty API key pool
    Given an API key pool configuration with no keys
    When I initialize the pool
    Then the pool should be empty
    And the default rotation strategy should be ROUND_ROBIN

  Scenario: Add API key to pool
    Given an API key pool configuration
    When I add an API key for provider GOOGLE
    Then the pool should contain 1 key
    And the key should have provider GOOGLE

  Scenario: Add multiple API keys for same provider
    Given an API key pool configuration
    When I add 3 API keys for provider GOOGLE
    Then the pool should contain 3 keys
    And all keys should have provider GOOGLE

  Scenario: Add API keys for multiple providers
    Given an API key pool configuration
    When I add 2 keys for provider GOOGLE
    And I add 2 keys for provider HUGGINGFACE
    And I add 1 key for provider GROQ
    Then the pool should contain 5 keys
    And the pool should have 3 providers

  Scenario: Select next key with ROUND_ROBIN strategy
    Given a pool with 3 GOOGLE keys
    When I select the next pool key 3 times
    Then each selection should return a different key
    And the 4th selection should return the first key

  Scenario: Select key respects quota threshold
    Given a pool with 2 GOOGLE keys
    And the first key has consumed 85% of its quota
    When I select the next pool key
    Then the second key should be selected

  Scenario: API key with expiration date
    Given an API key with expiration date in the past
    When I check key validity
    Then the key should be marked as expired

  Scenario: API key without expiration date
    Given an API key without expiration date
    When I check key validity
    Then the key should be valid

  Scenario: Quota configuration with custom threshold
    Given an API key with quota threshold of 90%
    When the consumed value reaches 89%
    Then the key should still be available
    When the consumed value reaches 90%
    Then the key should be marked as threshold exceeded

  Scenario: API key metadata storage
    Given an API key with metadata
    When I retrieve the key
    Then the metadata should be preserved

  Scenario: Service type filtering
    Given an API key configured for TEXT_GENERATION service
    When I request a key for TEXT_GENERATION
    Then the key should be eligible for selection
    Given an API key NOT configured for IMAGE_GENERATION service
    When I request a key for IMAGE_GENERATION
    Then the key should NOT be eligible for selection

  Scenario: Fallback enabled configuration
    Given a pool with fallback enabled
    When the selected key fails
    Then the next available key should be tried

  Scenario: Fallback disabled configuration
    Given a pool with fallback disabled
    When the selected key fails
    Then no fallback should occur

  Scenario: Audit logging enabled
    Given a pool with audit logging enabled
    When I select a key
    Then an audit log entry should be created

  Scenario: Audit logging disabled
    Given a pool with audit logging disabled
    When I select a key
    Then no audit log entry should be created

  Scenario: Pool configuration versioning
    Given a pool configuration with version "2.0"
    When I load the configuration
    Then the version should be "2.0"

  Scenario: Reset policy DAILY
    Given an API key with DAILY reset policy
    When a new day starts
    Then the quota should reset

  Scenario: Reset policy MANUAL
    Given an API key with MANUAL reset policy
    When I trigger manual reset
    Then the quota should reset

  @freemium
  Scenario: Freemium ratio disabled by default
    Given a pool with 2 FREE keys and 1 ENTERPRISE key
    And freemium ratio is 0.0
    And the pool is initialized with TIERED strategy
    When I select the next pool key
    Then the ENTERPRISE key should be selected

  @freemium
  Scenario: Freemium ratio 0.5 balances free and paid
    Given a pool with 2 FREE keys and 1 ENTERPRISE key
    And freemium ratio is 0.5
    And the pool is initialized with TIERED strategy
    When I select the next pool key
    Then the ENTERPRISE key should be selected

  @freemium
  Scenario: Freemium ratio 1.0 prioritizes free keys
    Given a pool with 2 FREE keys and 1 ENTERPRISE key
    And freemium ratio is 1.0
    And the pool is initialized with TIERED strategy
    When I select the next pool key
    Then a FREE key should be selected

  @freemium
  Scenario: Freemium weight decreases as usage increases
    Given a pool with only 2 FREE keys
    And freemium ratio is 0.5
    And the pool is initialized with TIERED strategy
    And the first key has consumed 50% of its quota
    When I select the next pool key
    Then the second key should be selected
