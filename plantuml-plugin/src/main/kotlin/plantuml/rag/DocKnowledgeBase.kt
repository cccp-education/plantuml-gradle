package plantuml.rag

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class DocKnowledgeBase(resourcePaths: List<String>) {

    private val chunks: List<DocChunk>

    init {
        chunks = resourcePaths.flatMap { path ->
            val text = loadResource(path)
            chunkDocument(path, text)
        }
    }

    fun queryContext(text: String, topK: Int = 3): String {
        if (chunks.isEmpty()) return ""
        val queryTerms = text.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        if (queryTerms.isEmpty()) return ""

        val scored = chunks.map { chunk ->
            val score = queryTerms.count { term -> chunk.text.lowercase().contains(term) }
            chunk to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)

        if (scored.isEmpty()) return ""

        return scored.joinToString("\n\n") { (chunk, _) ->
            "### ${chunk.section}\n${chunk.text}"
        }
    }

    private fun chunkDocument(path: String, text: String): List<DocChunk> {
        val sections = text.split(Regex("(?m)^== "))
        return sections.mapNotNull { section ->
            val lines = section.lines()
            if (lines.isEmpty()) return@mapNotNull null
            val title = lines.first().trim()
            val body = lines.drop(1).joinToString("\n").trim()
            if (body.isBlank()) return@mapNotNull null
            DocChunk(
                source = path.substringAfterLast("/"),
                section = title,
                text = body.take(2000)
            )
        }
    }

    private fun loadResource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: error("Resource not found: $path")
        return InputStreamReader(stream, StandardCharsets.UTF_8).use { it.readText() }
    }

    data class DocChunk(
        val source: String,
        val section: String,
        val text: String
    )

    companion object {
        fun forPlantUML(): DocKnowledgeBase = DocKnowledgeBase(
            listOf("docs/plantuml-reference.adoc")
        )
    }
}
