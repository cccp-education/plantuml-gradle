@tiered
Feature: Tiered Rotation Strategy

  Scenario: Enterprise key is selected first when present
    Given a pool with TIERED rotation strategy
    And the pool contains an ENTERPRISE key "ent-1"
    And the pool contains a PRO key "pro-1"
    And the pool contains a FREE key "free-1"
    When I select the next key
    Then the selected key should be "ent-1"

  Scenario: PRO key is selected when no ENTERPRISE available
    Given a pool with TIERED rotation strategy
    And the pool contains a PRO key "pro-1"
    And the pool contains a FREE key "free-1"
    When I select the next key
    Then the selected key should be "pro-1"

  Scenario: FREE key is selected when only FREE available
    Given a pool with TIERED rotation strategy
    And the pool contains a FREE key "free-1"
    And the pool contains a FREE key "free-2"
    When I select the next key
    Then the selected key should be "free-1"

  Scenario: Higher weight wins within the same tier
    Given a pool with TIERED rotation strategy
    And the pool contains an ENTERPRISE key "ent-low" with weight 1
    And the pool contains an ENTERPRISE key "ent-high" with weight 5
    When I select the next key
    Then the selected key should be "ent-high"

  Scenario: Strategy descends to PRO when ENTERPRISE quota is saturated
    Given a pool with TIERED rotation strategy
    And the pool contains an ENTERPRISE key "ent-1" with quota limit 4 and threshold 50
    And the pool contains a PRO key "pro-1" with quota limit 4 and threshold 50
    When I select the next key 3 times
    Then the 1st selected key should be "ent-1"
    And the 2nd selected key should be "ent-1"
    And the 3rd selected key should be "pro-1"

  Scenario: Strategy falls back to FREE when higher tiers are saturated and fallback is enabled
    Given a pool with TIERED rotation strategy with fallback enabled
    And the pool contains an ENTERPRISE key "ent-1" with quota limit 2 and threshold 50
    And the pool contains a PRO key "pro-1" with quota limit 2 and threshold 50
    And the pool contains a FREE key "free-1" with quota limit 10 and threshold 50
    When I select the next key 3 times
    Then the 1st selected key should be "ent-1"
    And the 2nd selected key should be "pro-1"
    And the 3rd selected key should be "free-1"

  Scenario: Strategy does not descend to FREE when fallback is disabled
    Given a pool with TIERED rotation strategy with fallback disabled
    And the pool contains an ENTERPRISE key "ent-1" with quota limit 2 and threshold 50
    And the pool contains a FREE key "free-1" with quota limit 10 and threshold 50
    When I select the next key 3 times
    Then the 1st selected key should be "ent-1"
    And the 2nd selected key should be "ent-1"
    And the 3rd selected key should be "ent-1"