package com.hairconsultant.app.data.recommendation

import android.util.Log
import com.hairconsultant.app.data.remote.gemini.ExtractedPreferences
import com.hairconsultant.app.data.remote.gemini.GeminiChatRepository
import com.hairconsultant.app.data.repository.HaircutRepository
import com.hairconsultant.app.domain.model.ChatMessage
import com.hairconsultant.app.domain.model.Gender
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.ScanResult
import com.hairconsultant.app.domain.model.TreatmentPreference
import com.hairconsultant.app.domain.model.matchingHaircutStyles
import kotlinx.coroutines.flow.first

/**
 * What [HairstyleRecommender.recommend] settled on: the top picks from the catalog (best first),
 * the consultant's explanation of them (null when the AI step failed and the caller should word
 * its own), and the parameters the picks were made against, for the caller to keep and persist.
 */
data class HairstyleRecommendation(
    val haircuts: List<Haircut>,
    val explanation: String?,
    val parameters: ExtractedPreferences
)

/**
 * The Show My Hairstyles pipeline shared by Face Scan and Image Upload:
 *  1. Compile the consultation — extract the parameters the user actually stated
 *     ([GeminiChatRepository.extractPreferences]) and merge them over what was already known.
 *  2. Narrow the catalog to those parameters ([shortlist]).
 *  3. Think — [GeminiChatRepository.rankHaircuts] reasons over the conversation, retrieved
 *     hairstyling knowledge and the narrowed candidates, and picks the top [TOP_PICKS].
 *  4. Return those catalog entries, so the pictures shown are the catalog's own.
 * If the AI steps fail (offline, API error), it falls back to the shortlist's own order, so
 * finalizing never gets blocked.
 */
class HairstyleRecommender(
    private val haircutRepository: HaircutRepository,
    private val chatRepository: GeminiChatRepository
) {

    suspend fun recommend(
        scan: ScanResult,
        known: ExtractedPreferences,
        gender: Gender?,
        consultation: List<ChatMessage>,
        conversationSummary: String?
    ): HairstyleRecommendation {
        val extracted = chatRepository.extractPreferences(consultation)
            .onFailure { Log.w(TAG, "Preference extraction failed; using what's already known", it) }
            .getOrNull() ?: ExtractedPreferences()
        val parameters = ExtractedPreferences(
            length = extracted.length ?: known.length,
            texture = extracted.texture ?: known.texture,
            treatment = extracted.treatment ?: known.treatment
        )
        // Limit to the user's parameters in code, not just in the prompt: when enough styles
        // exactly match the stated length and texture, the model only ever sees those, so it
        // can't pick e.g. a short cut for someone who asked for medium. Near-misses only enter
        // the pool when there aren't enough exact matches to fill the top picks.
        val shortlist = shortlist(scan, parameters, gender, RANKING_POOL_SIZE)
        val exactMatches = shortlist.filter { it.matchesStated(parameters) }
        val pool = if (exactMatches.size >= TOP_PICKS) exactMatches else shortlist
        val ranked = chatRepository.rankHaircuts(
            consultation = consultation,
            conversationSummary = conversationSummary,
            userProfile = describeProfile(scan, parameters, gender),
            candidates = pool,
            pickCount = TOP_PICKS
        ).onFailure { Log.w(TAG, "AI ranking failed; falling back to shortlist order", it) }.getOrNull()
        val byId = pool.associateBy { it.id }
        val aiPicks = ranked?.haircutIds.orEmpty().mapNotNull { byId[it] }.distinct().take(TOP_PICKS)
        if (ranked != null && aiPicks.size < minOf(TOP_PICKS, pool.size)) {
            Log.w(TAG, "AI ranking returned ${aiPicks.size} usable ids of ${ranked.haircutIds}; topping up from shortlist")
        }
        // Top up from the shortlist if the model returned fewer valid ids than asked for.
        val picks = (aiPicks + pool.filterNot { it in aiPicks }).take(TOP_PICKS)
        return HairstyleRecommendation(
            haircuts = picks,
            explanation = ranked?.explanation?.takeIf { aiPicks.size == picks.size },
            parameters = parameters
        )
    }

    /** Every style in the catalog — what chat replies are grounded on, so none is ever out of the model's reach. */
    suspend fun catalog(): List<Haircut> = haircutRepository.observeClusters().first().flatMap { it.haircuts }

    /**
     * The catalog ordered by how well each style matches [parameters] and [scan], best first,
     * limited to [limit]. Stated length and texture weigh most, so exact matches always lead and
     * near-misses only fill in when too few exact matches exist.
     *
     * For a male or female [gender], styles tagged for the other gender are dropped before
     * ranking rather than just scored lower, so the fill-in near-misses are always the closest
     * gender-appropriate styles (e.g. a long wavy men's cut for a man who asked for long straight),
     * never a women's style for a man or the reverse.
     */
    suspend fun shortlist(scan: ScanResult, parameters: ExtractedPreferences, gender: Gender?, limit: Int): List<Haircut> {
        val catalog = catalog()
        // Bald users are choosing a wig, so the scanned length/texture say nothing about what they want.
        val bald = parameters.length == HairLength.BALD
        val length = parameters.length.takeUnless { bald }
        val texture = parameters.texture.takeUnless { bald }
        val allowedStyles = gender?.takeIf { it == Gender.MALE || it == Gender.FEMALE }?.matchingHaircutStyles()
        val eligible = allowedStyles?.let { styles -> catalog.filter { it.genderStyle in styles } }
            ?.takeIf { it.isNotEmpty() } ?: catalog
        return eligible
            .sortedByDescending { haircut ->
                var score = 0
                if (length != null && haircut.length == length) score += LENGTH_WEIGHT
                if (texture != null && haircut.texture == texture) score += TEXTURE_WEIGHT
                when (parameters.treatment) {
                    TreatmentPreference.REBOND -> if (haircut.texture == HairTexture.STRAIGHT) score += TREATMENT_WEIGHT
                    TreatmentPreference.PERM -> if (haircut.texture != HairTexture.STRAIGHT) score += TREATMENT_WEIGHT
                    else -> Unit
                }
                if (scan.faceShape in haircut.recommendedFaceShapes) score += FACE_SHAPE_WEIGHT
                score
            }
            .take(limit)
    }

    /** Bald means a wig, so length/texture don't constrain it; otherwise every stated one must match. */
    private fun Haircut.matchesStated(parameters: ExtractedPreferences): Boolean {
        if (parameters.length == HairLength.BALD) return true
        return (parameters.length == null || length == parameters.length) &&
            (parameters.texture == null || texture == parameters.texture)
    }

    private fun describeProfile(scan: ScanResult, parameters: ExtractedPreferences, gender: Gender?): String = buildString {
        append("Confirmed face shape: ${scan.faceShape.displayName}. ")
        append("Current hair: ${scan.hairLength.displayName}, ${scan.hairTexture.displayName}. ")
        parameters.length?.let { append("Wanted length: ${it.displayName}. ") }
        parameters.texture?.let { append("Wanted texture: ${it.displayName}. ") }
        parameters.treatment?.let { append("Treatment: ${it.displayName}. ") }
        gender?.let { append("Gender: ${it.displayName}.") }
    }

    companion object {
        const val TOP_PICKS = 5
        private const val TAG = "HairstyleRecommender"
        private const val RANKING_POOL_SIZE = 15
        private const val LENGTH_WEIGHT = 8
        private const val TEXTURE_WEIGHT = 6
        private const val FACE_SHAPE_WEIGHT = 3
        private const val TREATMENT_WEIGHT = 2
    }
}
