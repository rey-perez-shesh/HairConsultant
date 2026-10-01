package com.hairconsultant.app.data.recommendation

import com.hairconsultant.app.data.SampleData
import com.hairconsultant.app.data.remote.gemini.ExtractedPreferences
import com.hairconsultant.app.data.remote.gemini.GeminiChatRepository
import com.hairconsultant.app.data.remote.gemini.RankedHaircuts
import com.hairconsultant.app.data.repository.HaircutRepository
import com.hairconsultant.app.domain.model.ChatMessage
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutCluster
import com.hairconsultant.app.domain.model.HaircutGenderStyle
import com.hairconsultant.app.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs [HairstyleRecommender.shortlist] against the real catalog, so it also checks how styles are tagged. */
class HairstyleRecommenderTest {

    private val recommender = HairstyleRecommender(FakeHaircutRepository(SampleData.allHaircuts), UnusedChatRepository)
    private val scan = ScanResult(FaceShape.OVAL, HairLength.SHORT, HairTexture.STRAIGHT)
    private val longStraight = ExtractedPreferences(length = HairLength.LONG, texture = HairTexture.STRAIGHT)

    @Test
    fun maleShortlistNeverContainsFeminineStyles() = runBlocking {
        val result = recommender.shortlist(scan, longStraight, Gender.MALE, limit = 15)

        assertEquals(15, result.size)
        assertTrue(result.none { it.genderStyle == HaircutGenderStyle.FEMININE })
    }

    @Test
    fun femaleShortlistNeverContainsMasculineStyles() = runBlocking {
        val result = recommender.shortlist(scan, longStraight, Gender.FEMALE, limit = 15)

        assertTrue(result.none { it.genderStyle == HaircutGenderStyle.MASCULINE })
    }

    @Test
    fun tooFewExactMatchesAreFilledWithTheClosestGenderAppropriateStyles() = runBlocking {
        // Long & Straight has only 3 styles a man can be offered, fewer than the 5 picks.
        val exact = SampleData.allHaircuts.count {
            it.length == HairLength.LONG && it.texture == HairTexture.STRAIGHT && it.genderStyle != HaircutGenderStyle.FEMININE
        }
        assertTrue(exact < HairstyleRecommender.TOP_PICKS)

        val result = recommender.shortlist(scan, longStraight, Gender.MALE, limit = HairstyleRecommender.TOP_PICKS)

        // Exact matches lead; the rest are near-misses (right length or right texture), never women's styles.
        assertTrue(result.take(exact).all { it.length == HairLength.LONG && it.texture == HairTexture.STRAIGHT })
        assertTrue(result.drop(exact).all { it.length == HairLength.LONG || it.texture == HairTexture.STRAIGHT })
        assertTrue(result.none { it.genderStyle == HaircutGenderStyle.FEMININE })
    }

    @Test
    fun nonBinaryAndUnknownGenderAreNotFiltered() = runBlocking {
        val all = SampleData.allHaircuts.size
        val nonBinary = recommender.shortlist(scan, longStraight, Gender.NON_BINARY, limit = all)
        val unknown = recommender.shortlist(scan, longStraight, null, limit = all)

        assertEquals(all, nonBinary.size)
        assertEquals(all, unknown.size)
    }

    private class FakeHaircutRepository(private val haircuts: List<Haircut>) : HaircutRepository {
        override fun observeClusters(): Flow<List<HaircutCluster>> =
            flowOf(listOf(HaircutCluster(HairLength.SHORT, HairTexture.STRAIGHT, haircuts)))
        override fun observeMatching(faceShape: FaceShape, length: HairLength, texture: HairTexture): Flow<List<Haircut>> =
            flowOf(haircuts)
        override suspend fun refresh() = Unit
    }

    /** [HairstyleRecommender.shortlist] never calls the AI; these exist only to satisfy the constructor. */
    private object UnusedChatRepository : GeminiChatRepository {
        override suspend fun reply(conversation: List<ChatMessage>, userMessage: String, context: String, conversationSummary: String?) =
            error("unused")
        override suspend fun summarizeConversation(previousSummary: String?, newMessages: List<ChatMessage>) = error("unused")
        override suspend fun extractPreferences(conversation: List<ChatMessage>) = error("unused")
        override suspend fun rankHaircuts(
            consultation: List<ChatMessage>,
            conversationSummary: String?,
            userProfile: String,
            candidates: List<Haircut>,
            pickCount: Int
        ): Result<RankedHaircuts> = error("unused")
    }
}
