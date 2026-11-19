package com.hairconsultant.app.data.recommendation

import com.hairconsultant.app.data.HaircutLengths
import com.hairconsultant.app.data.SampleData
import com.hairconsultant.app.data.analysis.HairLengthScale
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
import com.hairconsultant.app.domain.model.MeasuredHairLength
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
        // On the men's scale (under 2 in on top) only a couple of straight styles are Short, fewer than the 5 picks.
        val shortStraight = ExtractedPreferences(length = HairLength.SHORT, texture = HairTexture.STRAIGHT)
        fun Haircut.mensLength() = HairLengthScale.label(this, Gender.MALE)
        val exact = SampleData.allHaircuts.count {
            it.mensLength() == HairLength.SHORT && it.texture == HairTexture.STRAIGHT && it.genderStyle != HaircutGenderStyle.FEMININE
        }
        assertTrue(exact in 1 until HairstyleRecommender.TOP_PICKS)

        val result = recommender.shortlist(scan, shortStraight, Gender.MALE, limit = HairstyleRecommender.TOP_PICKS)

        // Exact matches lead; the rest are near-misses (right length or right texture), never women's styles.
        assertTrue(result.take(exact).all { it.mensLength() == HairLength.SHORT && it.texture == HairTexture.STRAIGHT })
        assertTrue(result.drop(exact).all { it.mensLength() == HairLength.SHORT || it.texture == HairTexture.STRAIGHT })
        assertTrue(result.none { it.genderStyle == HaircutGenderStyle.FEMININE })
    }

    @Test
    fun aManWantingMediumGetsStylesMediumOnTheMensScaleWhateverTheirCatalogLabel() = runBlocking {
        val medium = ExtractedPreferences(length = HairLength.MEDIUM, texture = HairTexture.STRAIGHT)
        val result = recommender.shortlist(scan, medium, Gender.MALE, limit = HairstyleRecommender.TOP_PICKS)

        assertTrue(result.all { HairLengthScale.label(it, Gender.MALE) == HairLength.MEDIUM })
        // e.g. Undercut needs 3-6 in on top: medium for a man, though the catalog files it under Short.
        assertTrue(recommender.shortlist(scan, medium, Gender.MALE, limit = 40).any { it.name == "Undercut" })
    }

    @Test
    fun stylesNeedingMoreHairThanTheScanMeasuredAreLeftOut() = runBlocking {
        val shortTop = scan.copy(measuredHairLength = MeasuredHairLength(1f, 2f, 1.5f, "on top"))
        val all = SampleData.allHaircuts.size
        val result = recommender.shortlist(shortTop, ExtractedPreferences(), Gender.MALE, limit = all)

        assertTrue(result.isNotEmpty())
        assertTrue(result.all { HaircutLengths.forHaircut(it).minIn <= 2f })
        assertTrue(result.any { it.name == "Crew Cut" })
        assertTrue(result.none { it.name == "Undercut" || it.name == "Modern Pompadour" })
    }

    @Test
    fun aWigSearchIsNotLimitedByTheHairOnTheHead() = runBlocking {
        val buzzed = scan.copy(measuredHairLength = MeasuredHairLength(0.1f, 0.5f, 0.2f, "on top"))
        val all = SampleData.allHaircuts.size
        val wig = recommender.shortlist(buzzed, ExtractedPreferences(length = HairLength.BALD), null, limit = all)

        assertEquals(all, wig.size)
    }

    @Test
    fun hairShorterThanEveryStyleGetsTheNearestStyleToGrowInto() = runBlocking {
        val offline = HairstyleRecommender(FakeHaircutRepository(SampleData.allHaircuts), OfflineChatRepository)
        val buzzed = scan.copy(measuredHairLength = MeasuredHairLength(0.1f, 0.5f, 0.2f, "on top"))
        val result = offline.recommend(buzzed, ExtractedPreferences(), Gender.FEMALE, emptyList(), null)

        assertTrue(result.haircuts.isEmpty())
        // The shortest feminine/unisex styles need 2 in: 1.5 in to grow at half an inch a month.
        assertTrue(result.explanation!!, result.explanation!!.contains("3 more months"))
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
        override suspend fun seedIfEmpty() = Unit
        override suspend fun syncWithRemote() = Unit
    }

    /** No network: preference extraction and ranking fail, as they do offline. */
    private object OfflineChatRepository : GeminiChatRepository {
        override suspend fun reply(conversation: List<ChatMessage>, userMessage: String, context: String, conversationSummary: String?) =
            error("unused")
        override suspend fun summarizeConversation(previousSummary: String?, newMessages: List<ChatMessage>) = error("unused")
        override suspend fun extractPreferences(conversation: List<ChatMessage>): Result<ExtractedPreferences> =
            Result.failure(IllegalStateException("offline"))
        override suspend fun rankHaircuts(
            consultation: List<ChatMessage>,
            conversationSummary: String?,
            userProfile: String,
            candidates: List<Haircut>,
            pickCount: Int
        ): Result<RankedHaircuts> = Result.failure(IllegalStateException("offline"))
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
