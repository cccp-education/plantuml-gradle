@file:JvmName("TranslationStrategyAlias")
package plantuml.boundary

/**
 * Backward-compat typealias — the translation strategy vocabulary is unified
 * with the N0 contract `contracts.plantuml.PlantUmlStrategy` (D4, EPIC
 * PLT-DIAGRAM-OWNERSHIP US-2).
 *
 * There is now ONE enum type (`TRANSLATE` / `BORROW` / `PRESERVE`) shared by
 * plantuml, document and the contract — never a second, divergent enum.
 * Existing `plantuml.boundary.TranslationStrategy.*` references resolve
 * transparently through the alias.
 */
typealias TranslationStrategy = contracts.plantuml.PlantUmlStrategy
