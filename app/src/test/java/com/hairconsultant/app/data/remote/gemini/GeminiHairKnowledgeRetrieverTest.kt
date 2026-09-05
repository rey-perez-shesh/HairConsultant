package com.hairconsultant.app.data.remote.gemini

import com.hairconsultant.app.data.HairKnowledgeBase.KnowledgeChunk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiHairKnowledgeRetrieverTest {

    /** Deterministic stand-in for a real embedding model: each chunk is its own basis vector. */
    private val chunks = listOf(
        KnowledgeChunk("round", "round face shape guidance"),
        KnowledgeChunk("square", "square face shape guidance"),
        KnowledgeChunk("curly", "curly hair type guidance")
    )
    private val axisVectors = mapOf(
        "round face shape guidance" to floatArrayOf(1f, 0f, 0f),
        "square face shape guidance" to floatArrayOf(0f, 1f, 0f),
        "curly hair type guidance" to floatArrayOf(0f, 0f, 1f)
    )
    private val embedder = TextEmbedder { text -> axisVectors[text] ?: floatArrayOf(0f, 0f, 0f) }

    @Test
    fun returnsTheChunkClosestToTheQuery() = runBlocking {
        val retriever = GeminiHairKnowledgeRetriever(embedder, chunks)

        val result = retriever.retrieve("round face shape guidance", topK = 1)

        assertEquals(listOf("round face shape guidance"), result.getOrThrow())
    }

    @Test
    fun ranksChunksByDescendingSimilarity() = runBlocking {
        val retriever = GeminiHairKnowledgeRetriever(embedder, chunks)
        // A query embedding closer to "round" than to "square", with "curly" orthogonal.
        val mixedQueryEmbedder = TextEmbedder { floatArrayOf(0.9f, 0.1f, 0f) }
        val mixedRetriever = GeminiHairKnowledgeRetriever(mixedQueryEmbedder, chunks)

        val result = mixedRetriever.retrieve("something about a round-ish square face", topK = 2)

        assertEquals(listOf("round face shape guidance", "square face shape guidance"), result.getOrThrow())
        assertTrue(retriever.retrieve("round face shape guidance", topK = 3).getOrThrow().size == 3)
    }

    @Test
    fun cachesChunkEmbeddingsAcrossCalls() = runBlocking {
        var chunkEmbedCalls = 0
        val countingEmbedder = TextEmbedder { text ->
            val knownChunkText = axisVectors[text]
            if (knownChunkText != null) chunkEmbedCalls++
            knownChunkText ?: floatArrayOf(0.9f, 0f, 0f)
        }
        val retriever = GeminiHairKnowledgeRetriever(countingEmbedder, chunks)

        retriever.retrieve("first query, not a chunk's own text", topK = 1)
        retriever.retrieve("second query, not a chunk's own text either", topK = 1)

        assertEquals("chunk embeddings should only be computed once, not per query", chunks.size, chunkEmbedCalls)
    }
}
