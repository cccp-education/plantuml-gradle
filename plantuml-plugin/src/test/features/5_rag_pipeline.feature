@rag
Feature: RAG Pipeline

  Background:
    Given a prompt file "rag-test.prompt" with content "Create a microservices architecture diagram"
    And a PlantUML configuration with RAG enabled

  @rag
  Scenario: Reindex RAG creates embeddings in pgvector
    Given a running pgvector container
    When I run collectPlantumlIndex task
    Then embeddings should be stored in pgvector
    And the embedding count should match prompt count

  @rag
  Scenario: RAG context is injected in LLM prompts
    Given a running pgvector container with existing embeddings
    And a mock LLM that captures the user message
    When I run generatePlantumlDiagrams task
    Then the LLM request should contain RAG context chunks
    And the RAG similarity score should be logged

  @rag
  Scenario: Incremental reindex skips unchanged prompts
    Given a running pgvector container with existing embeddings
    And the prompt file has not been modified
    When I run collectPlantumlIndex task
    Then unchanged prompts should be skipped
    And only new or modified prompts should be indexed

  @rag
  Scenario: RAG cleanup removes deleted prompt embeddings
    Given a running pgvector container with embeddings for 3 prompts
    And one prompt file is deleted
    When I run collectPlantumlIndex task
    Then the deleted prompt embeddings should be removed
    And the embedding count should decrease by one
