package plantuml

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response

class StubEmbeddingModel : EmbeddingModel {
    override fun embed(text: String): Response<Embedding> {
        val vector = FloatArray(384) { index -> index.toFloat() / 384f }
        return Response.from(Embedding.from(vector))
    }

    override fun embedAll(segments: List<TextSegment>): Response<List<Embedding>> = Response.from(segments.map { embed(it.text()).content() })

    override fun dimension(): Int = 384
}
