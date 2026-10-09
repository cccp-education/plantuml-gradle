@provider-model
Feature: Configurable HuggingFace / Groq model names (GBL-007 PLT-CR3-1)

  HuggingFace and Groq were the only providers ignoring the configured model
  name (`langchain4j.<provider>.modelName`). This feature proves the model is
  now configurable, with backward-compatible defaults.

  @provider-model @huggingface @configured
  Scenario: HuggingFace honours the configured model
    Given a HuggingFace provider configured with model "HuggingFaceH4/zephyr-7b-beta"
    Then the resolved HuggingFace model should be "HuggingFaceH4/zephyr-7b-beta"

  @provider-model @huggingface @default
  Scenario: HuggingFace falls back to the historical default
    Given a HuggingFace provider with no model configured
    Then the resolved HuggingFace model should be "gpt2"

  @provider-model @groq @configured
  Scenario: Groq honours the configured model
    Given a Groq provider configured with model "llama-3.3-70b-versatile"
    Then the resolved Groq model should be "llama-3.3-70b-versatile"

  @provider-model @groq @default
  Scenario: Groq falls back to the historical default
    Given a Groq provider with no model configured
    Then the resolved Groq model should be "llama3-8b-8192"
