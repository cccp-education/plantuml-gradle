package plantuml

import java.io.File

/**
 * Business logic for RAG indexing, extracted from CollectPlantumlIndexTask.
 *
 * Receives only the RAG directory — no reference to Project or Task.
 */
class RagIndexer(private val ragDir: File) {
    data class IndexingResult(
        val diagramsFound: Int,
        val historiesFound: Int,
        val indexed: Int,
        val messages: List<String>,
    )

    fun index(): IndexingResult {
        val messages = mutableListOf<String>()

        if (!ragDir.exists()) {
            ragDir.mkdirs()
            messages += "→ Created RAG directory: ${ragDir.absolutePath}"
            return IndexingResult(0, 0, 0, messages)
        }

        if (!ragDir.isDirectory) {
            messages += "✗ RAG path is not a directory: ${ragDir.absolutePath}"
            return IndexingResult(0, 0, 0, messages)
        }

        val diagrams = ragDir.walkTopDown()
            .filter { it.isFile && it.extension == "puml" }
            .toList()

        val histories = ragDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .toList()

        if (diagrams.isEmpty() && histories.isEmpty()) {
            messages += "→ No PlantUML diagrams or training data found in RAG directory"
            return IndexingResult(0, 0, 0, messages)
        }

        messages += "→ Found ${diagrams.size} PlantUML diagrams and ${histories.size} training histories for indexing"

        var indexed = 0
        diagrams.forEach { file ->
            runCatching {
                indexDiagram(file)
                indexed++
            }.onFailure { e ->
                messages += "✗ Failed to index ${file.name}: ${e.message}"
            }
        }

        messages += "→ Indexed $indexed / ${diagrams.size} diagrams"
        return IndexingResult(diagrams.size, histories.size, indexed, messages)
    }

    private fun indexDiagram(file: File) {
        // Real implementation: embeddings, vector storage, etc.
        // Stub so tests verify behavior without DB dependency.
        val content = file.readText()
        if (content.isBlank()) return
        // TODO: call embedding service
    }
}
