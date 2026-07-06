#noinspection CucumberUndefinedStep
Feature: User account signup

  Background:
    Given a list of login, email, password, firstName, lastName

      | login | email          | password | firstName | lastName |
      | admin | admin@acme.com | admin    | admin     | admin    |
      | user  | user@acme.com  | user     | user      | user     |


  Scenario: Create a new user account
    Given the user from the list with login "user"
    When the signup request is sent for "user"
    Then the result is the creation of a new inactive account