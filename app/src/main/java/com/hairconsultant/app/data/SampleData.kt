package com.hairconsultant.app.data

import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.FaceShape.DIAMOND
import com.hairconsultant.app.domain.model.FaceShape.HEART
import com.hairconsultant.app.domain.model.FaceShape.OVAL
import com.hairconsultant.app.domain.model.FaceShape.ROUND
import com.hairconsultant.app.domain.model.FaceShape.SQUARE
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutGenderStyle
import com.hairconsultant.app.domain.model.HaircutGenderStyle.FEMININE
import com.hairconsultant.app.domain.model.HaircutGenderStyle.MASCULINE
import com.hairconsultant.app.domain.model.HaircutGenderStyle.UNISEX
import com.hairconsultant.app.domain.model.TreatmentPreference

/**
 * Curated haircut catalog: real, commonly-recognized styles for every hair length x texture
 * combination, each paired with the face shapes it's actually recommended for based on standard
 * hairstyling guidance (round faces need added height/angles, square faces need softening,
 * heart/diamond faces need width added back at the jaw or forehead, oval is naturally balanced).
 * Every entry's [Haircut.description] states the specific reason it suits those shapes.
 *
 * [com.hairconsultant.app.data.repository.HaircutRepositoryImpl] migrates this list into
 * Firestore's "haircuts" collection the first time the app runs against an empty project, and
 * falls back to it whenever Firestore/network is unavailable.
 */
object SampleData {

    private data class Seed(
        val name: String,
        val faceShapes: List<FaceShape>,
        val description: String,
        val genderStyle: HaircutGenderStyle = UNISEX,
        /** Optional Coil-compatible image URL (android.resource://… for local thumbs). */
        val imageUrl: String? = null
    )

    /** Catalog IDs whose name/thumbnail were updated for Meshy GLB try-on assets. */
    val MESHY_REPLACED_IDS: Set<String> = setOf(
        "SHORT_STRAIGHT_0", // Boy Cut
        "SHORT_STRAIGHT_1", // Curtains
        "LONG_WAVY_1" // Long Wavy Hair (thumbnail/name; enable AR in HairstyleArCatalog.DEDICATED_GLB_IDS)
    )

    /** New catalog cards added with local drawable thumbnails (merged on refresh). */
    val NEW_CATALOG_IDS: Set<String> = setOf(
        "SHORT_STRAIGHT_6", // Short Bob
        "SHORT_STRAIGHT_7", // Modern Pompadour
        "SHORT_STRAIGHT_8", // Undercut
        "SHORT_STRAIGHT_9", // French Crop
        "SHORT_STRAIGHT_10", // Classic
        "SHORT_STRAIGHT_11", // Textured Pixie
        "MEDIUM_STRAIGHT_7", // Slicked Back
        "MEDIUM_STRAIGHT_8", // Side Bangs
        "MEDIUM_WAVY_6", // Medium Layered
        "MEDIUM_WAVY_7", // Medium Modern Mullet
        "MEDIUM_WAVY_8", // Curtains with Layer
        "LONG_STRAIGHT_6", // Long Bob
        "LONG_WAVY_7", // Shaggy Wolfcut
        "LONG_WAVY_8" // Long Layered Side
    )

    /**
     * Styles shown for both Male and Female filters (UNISEX in [HaircutGenderStyle]).
     * Patched on refresh so Firestore/local DB stay in sync with SampleData.
     */
    val GENDER_UNISEX_PATCH_IDS: Set<String> = setOf(
        "SHORT_STRAIGHT_1", // Curtains
        "MEDIUM_STRAIGHT_8" // Side Bangs
    )

    private const val PKG = "com.hairconsultant.app"
    private val THUMB_BOY_CUT =
        "android.resource://$PKG/drawable/thumb_buzz_cut"
    private val THUMB_CURTAINS =
        "android.resource://$PKG/drawable/thumb_curtains"
    private val THUMB_LONG_WAVY =
        "android.resource://$PKG/drawable/thumb_long_wavy"
    private val THUMB_SHAGGY_WOLFCUT =
        "android.resource://$PKG/drawable/thumb_shaggy_wolfcut"
    private val THUMB_LONG_LAYERED_SIDE =
        "android.resource://$PKG/drawable/thumb_long_layered_side"
    private val THUMB_SHORT_BOB =
        "android.resource://$PKG/drawable/thumb_short_bob"
    private val THUMB_SLICKED_BACK =
        "android.resource://$PKG/drawable/thumb_slicked_back"
    private val THUMB_MEDIUM_LAYERED =
        "android.resource://$PKG/drawable/thumb_medium_layered"
    private val THUMB_MODERN_POMPADOUR =
        "android.resource://$PKG/drawable/thumb_modern_pompadour"
    private val THUMB_UNDERCUT =
        "android.resource://$PKG/drawable/thumb_undercut"
    private val THUMB_FRENCH_CROP =
        "android.resource://$PKG/drawable/thumb_french_crop"
    private val THUMB_CLASSIC =
        "android.resource://$PKG/drawable/thumb_classic"
    private val THUMB_SIDE_BANGS =
        "android.resource://$PKG/drawable/thumb_side_bangs"
    private val THUMB_MEDIUM_MODERN_MULLET =
        "android.resource://$PKG/drawable/thumb_medium_modern_mullet"
    private val THUMB_CURTAINS_WITH_LAYER =
        "android.resource://$PKG/drawable/thumb_curtains_with_layer"
    private val THUMB_LONG_BOB =
        "android.resource://$PKG/drawable/thumb_long_bob"
    private val THUMB_SHORT_FEATHERED_SHAG =
        "android.resource://$PKG/drawable/thumb_short_feathered_shag"
    private val THUMB_WAVY_BOB_SIDE_BANGS =
        "android.resource://$PKG/drawable/thumb_wavy_bob_side_bangs"
    private val THUMB_WAVY_PIXIE =
        "android.resource://$PKG/drawable/thumb_wavy_pixie"
    private val THUMB_WAVY_SHAG =
        "android.resource://$PKG/drawable/thumb_wavy_shag"
    private val THUMB_CURLY_BIXIE =
        "android.resource://$PKG/drawable/thumb_curly_bixie"
    private val THUMB_UNDERCUT_CROP_LENGTH_TOP =
        "android.resource://$PKG/drawable/thumb_undercut_crop_length_top"
    private val THUMB_WAVY_FRINGE_CROP =
        "android.resource://$PKG/drawable/thumb_wavy_fringe_crop"
    private val THUMB_TEXTURED_QUIFF =
        "android.resource://$PKG/drawable/thumb_textured_quiff"
    private val THUMB_CLASSIC_BLUNT_BOB =
        "android.resource://$PKG/drawable/thumb_classic_blunt_bob"
    private val THUMB_SIDE_SWEPT_FRINGE_CROP =
        "android.resource://$PKG/drawable/thumb_side_swept_fringe_crop"
    private val THUMB_TEXTURED_PIXIE =
        "android.resource://$PKG/drawable/thumb_textured_pixie"
    private val THUMB_CURLY_BOB =
        "android.resource://$PKG/drawable/thumb_curly_bob"
    private val THUMB_WASH_AND_GO_TWIST_OUT_CROP =
        "android.resource://$PKG/drawable/thumb_wash_and_go_twist_out_crop"
    private val THUMB_CURLY_WOLF_CUT =
        "android.resource://$PKG/drawable/thumb_curly_wolf_cut"
    private val THUMB_CURLY_FRINGE_CROP =
        "android.resource://$PKG/drawable/thumb_curly_fringe_crop"
    private val THUMB_CURLY_CROP_FADE =
        "android.resource://$PKG/drawable/thumb_curly_crop_fade"
    private val THUMB_V_CUT_LAYERS =
        "android.resource://$PKG/drawable/thumb_v_cut_layers"
    private val THUMB_CURTAIN_BANGS_LOB =
        "android.resource://$PKG/drawable/thumb_curtain_bangs_lob"
    private val THUMB_A_LINE_BOB =
        "android.resource://$PKG/drawable/thumb_a_line_bob"
    private val THUMB_FACE_FRAMING_LAYERED_CUT =
        "android.resource://$PKG/drawable/thumb_face_framing_layered_cut"
    private val THUMB_SIDE_PART_COMB_OVER_MEDIUM =
        "android.resource://$PKG/drawable/thumb_side_part_comb_over_medium"
    private val THUMB_TEXTURED_CROP_MEDIUM_LENGTH =
        "android.resource://$PKG/drawable/thumb_textured_crop_medium_length"
    private val THUMB_LONG_CURLY_LAYERS =
        "android.resource://$PKG/drawable/thumb_long_curly_layers"
    private val THUMB_LONG_CURLY_SHAG =
        "android.resource://$PKG/drawable/thumb_long_curly_shag"
    private val THUMB_LONG_CURLY_V_CUT =
        "android.resource://$PKG/drawable/thumb_long_curly_v_cut"
    private val THUMB_LONG_DEFINED_RINGLETS_SIDE_PART =
        "android.resource://$PKG/drawable/thumb_long_defined_ringlets_side_part"
    private val THUMB_LONG_CURLY_MAN_BUN =
        "android.resource://$PKG/drawable/thumb_long_curly_man_bun"
    private val THUMB_CURLY_SHOULDER_LENGTH_LAYERS =
        "android.resource://$PKG/drawable/thumb_curly_shoulder_length_layers"
    private val THUMB_LONG_BLUNT =
        "android.resource://$PKG/drawable/thumb_long_blunt"
    private val THUMB_LONG_CURTAIN_BANGS =
        "android.resource://$PKG/drawable/thumb_long_curtain_bangs"
    private val THUMB_LONG_LAYERS =
        "android.resource://$PKG/drawable/thumb_long_layers"
    private val THUMB_LONG_SHAG =
        "android.resource://$PKG/drawable/thumb_long_shag"
    private val THUMB_LONG_SLICKED_BACK =
        "android.resource://$PKG/drawable/thumb_long_slicked_back"
    private val THUMB_SLEEK_STRAIGHT_WITH_SIDE_PART =
        "android.resource://$PKG/drawable/thumb_sleek_straight_with_side_part"
    private val THUMB_SLEEK_LOB =
        "android.resource://$PKG/drawable/thumb_sleek_lob"
    private val THUMB_BEACH_WAVY_SHAG =
        "android.resource://$PKG/drawable/thumb_beach_wavy_shag"
    private val THUMB_TEXTURED_SIDE_PART =
        "android.resource://$PKG/drawable/thumb_textured_side_part"
    private val THUMB_WAVY_FACE_FRAMING_LAYERS =
        "android.resource://$PKG/drawable/thumb_wavy_face_framing_layers"
    private val THUMB_WAVY_LOB_WITH_SIDE_PART =
        "android.resource://$PKG/drawable/thumb_wavy_lob_with_side_part"
    private val THUMB_CURTAIN_BANGS_WAVY_LOB =
        "android.resource://$PKG/drawable/thumb_curtain_bangs_wavy_lob"
    private val THUMB_SOFT_LAYERED_WAVES =
        "android.resource://$PKG/drawable/thumb_soft_layered_waves"
    private val THUMB_CURLY_CURTAIN_BANGS =
        "android.resource://$PKG/drawable/thumb_curly_curtain_bangs"
    private val THUMB_CURLY_LOB =
        "android.resource://$PKG/drawable/thumb_curly_lob"
    private val THUMB_CURLY_QUIFF =
        "android.resource://$PKG/drawable/thumb_curly_quiff"
    private val THUMB_CURLY_UNDERCUT =
        "android.resource://$PKG/drawable/thumb_curly_undercut"
    private val THUMB_MEDIUM_CURLY_V_CUT_LAYERS =
        "android.resource://$PKG/drawable/thumb_medium_curly_v_cut_layers"
    private val THUMB_DEVACUT =
        "android.resource://$PKG/drawable/thumb_devacut"
    private val THUMB_LAYERED_CURLY_SHAG =
        "android.resource://$PKG/drawable/thumb_layered_curly_shag"
    private val THUMB_BEACH_WAVE_CROP =
        "android.resource://$PKG/drawable/thumb_beach_wave_crop"
    private val THUMB_FACE_FRAMING_WAVY_LAYERS =
        "android.resource://$PKG/drawable/thumb_face_framing_wavy_layers"
    private val THUMB_V_CUT_WAVY_LAYERS =
        "android.resource://$PKG/drawable/thumb_v_cut_wavy_layers"
    private val THUMB_LONG_WAVY_SHAG =
        "android.resource://$PKG/drawable/thumb_long_wavy_shag"
    private val THUMB_WAVY_LOB_TO_LONG_CURTAIN_FRINGE =
        "android.resource://$PKG/drawable/thumb_wavy_lob_to_long_curtain_fringe"
    private val THUMB_CURLY_CURTAIN_BANGS_LONG =
        "android.resource://$PKG/drawable/thumb_curly_curtain_bangs_long"

    /** Catalog IDs whose thumbnail was corrected from provided reference images. */
    val THUMB_PATCH_IDS: Set<String> = setOf(
        "SHORT_STRAIGHT_2", // Side-Swept Fringe Crop
        "SHORT_STRAIGHT_3", // Short Feathered Shag
        "SHORT_STRAIGHT_4", // Undercut Crop with Length on Top
        "SHORT_STRAIGHT_6", // Short Bob (Classic Blunt Bob image)
        "SHORT_STRAIGHT_11", // Textured Pixie
        "SHORT_WAVY_0", // Wavy Pixie
        "SHORT_WAVY_1", // Wavy Bob with Side Bangs
        "SHORT_WAVY_3", // Wavy Shag
        "SHORT_WAVY_4", // Wavy Fringe Crop
        "SHORT_WAVY_5", // Textured Quiff
        "SHORT_CURLY_0", // Curly Bixie
        "SHORT_CURLY_1", // Curly Bob
        "SHORT_CURLY_2", // Wash and Go Twist Out Crop
        "SHORT_CURLY_3", // Curly Wolf Cut
        "SHORT_CURLY_4", // Curly Fringe Crop
        "SHORT_CURLY_5", // Curly Crop Fade
        "MEDIUM_STRAIGHT_1", // V-Cut Layers
        "MEDIUM_STRAIGHT_2", // Curtain Bangs Lob
        "MEDIUM_STRAIGHT_3", // A-Line Bob
        "MEDIUM_STRAIGHT_4", // Face Framing Layered Cut
        "MEDIUM_STRAIGHT_5", // Side Part Comb Over Medium
        "MEDIUM_STRAIGHT_6", // Textured Crop Medium Length
        "LONG_CURLY_0", // Long Curly Layers
        "LONG_CURLY_1", // Long Curly Shag
        "LONG_CURLY_3", // Long Curly V-Cut
        "LONG_CURLY_4", // Long Defined Ringlets with Side Part
        "LONG_CURLY_5", // Long Curly Man Bun
        "LONG_CURLY_6", // Curly Shoulder Length Layers
        "MEDIUM_STRAIGHT_0", // Sleek Lob
        "LONG_STRAIGHT_0", // Long Layers
        "LONG_STRAIGHT_1", // Long Curtain Bangs
        "LONG_STRAIGHT_2", // Long Blunt
        "LONG_STRAIGHT_3", // Long Shag
        "LONG_STRAIGHT_4", // Sleek Straight with Side Part
        "LONG_STRAIGHT_5", // Long Slicked Back
        "MEDIUM_WAVY_2", // Wavy Lob with Side Part
        "MEDIUM_WAVY_3", // Beachy Wavy Shag
        "MEDIUM_WAVY_4", // Wavy Face-Framing Layers
        "MEDIUM_WAVY_5", // Textured Side Part
        "MEDIUM_WAVY_0", // Curtain Bangs Wavy Lob
        "MEDIUM_WAVY_1", // Soft Layered Waves
        "MEDIUM_CURLY_0", // Curly Lob
        "MEDIUM_CURLY_1", // Layered Curly Shag
        "MEDIUM_CURLY_2", // Curly Curtain Bangs
        "MEDIUM_CURLY_3", // Curly V-Cut Layers
        "MEDIUM_CURLY_4", // Devacut
        "MEDIUM_CURLY_5", // Curly Quiff
        "MEDIUM_CURLY_6", // Curly Undercut with Length on Top
        "SHORT_WAVY_2", // Beach Wave Crop
        "LONG_WAVY_0", // Wavy Lob-to-Long Curtain Fringe
        "LONG_WAVY_2", // Long Wavy Shag
        "LONG_WAVY_3", // V-Cut Wavy Layers
        "LONG_WAVY_4", // Face-Framing Wavy Layers
        "LONG_CURLY_2" // Curly Curtain Bangs Long
    )

    private val catalog: Map<Pair<HairLength, HairTexture>, List<Seed>> = mapOf(
        (HairLength.SHORT to HairTexture.STRAIGHT) to listOf(
            Seed(
                "Boy Cut", listOf(SQUARE, HEART, DIAMOND),
                "A clean short boy cut softens a square jaw with height at the crown, while the close silhouette keeps a wide forehead in proportion — flattering on heart and diamond shapes too.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_BOY_CUT
            ),
            Seed(
                "Curtains", listOf(OVAL, HEART, DIAMOND),
                "A center-parted curtains cut frames the face with soft length at the temples and chin, balancing a heart or diamond face's narrower jaw while an oval face wears the shape with ease.",
                genderStyle = UNISEX,
                imageUrl = THUMB_CURTAINS
            ),
            Seed(
                "Side-Swept Fringe Crop", listOf(ROUND, HEART, DIAMOND),
                "An asymmetrical side fringe breaks up a round face's soft curves and disguises a heart or diamond face's wider forehead with soft coverage.",
                genderStyle = UNISEX,
                imageUrl = THUMB_SIDE_SWEPT_FRINGE_CROP
            ),
            Seed(
                "Short Feathered Shag", listOf(ROUND, SQUARE),
                "Feathered layers build height at the crown to elongate a round face, and the soft, ragged ends blur a square jawline's hard corners.",
                genderStyle = UNISEX,
                imageUrl = THUMB_SHORT_FEATHERED_SHAG
            ),
            Seed(
                "Undercut Crop with Length on Top", listOf(OVAL, ROUND, DIAMOND),
                "Extra length and volume on top draws the eye upward, elongating round proportions and balancing a diamond face's wide cheekbones; clean on an oval face too.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_UNDERCUT_CROP_LENGTH_TOP
            ),
            Seed(
                "Crew Cut", listOf(ROUND, OVAL),
                "Tight sides with slightly longer hair on top add subtle height at the crown, elongating round proportions, while an oval face carries the clean, classic taper with ease.",
                genderStyle = MASCULINE
            ),
            Seed(
                "Short Bob", listOf(OVAL, HEART, DIAMOND),
                "A chin-grazing bob adds soft width at the jaw to balance a heart or diamond face, while an oval face wears the clean, blunt silhouette with ease.",
                genderStyle = FEMININE,
                imageUrl = THUMB_CLASSIC_BLUNT_BOB
            ),
            Seed(
                "Modern Pompadour", listOf(OVAL, ROUND, SQUARE),
                "Volume swept up and back adds height at the crown to elongate round proportions and softens a square jaw, while an oval face suits the polished, structured shape.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_MODERN_POMPADOUR
            ),
            Seed(
                "Undercut", listOf(OVAL, SQUARE, DIAMOND),
                "Short, faded sides keep width in check while longer length on top adds balance for a diamond face and softens a square jawline's hard corners.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_UNDERCUT
            ),
            Seed(
                "French Crop", listOf(ROUND, SQUARE, HEART),
                "A textured fringe and short crop add forward coverage that softens a heart face's wider forehead while keeping round and square outlines clean and compact.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_FRENCH_CROP
            ),
            Seed(
                "Classic", listOf(OVAL, SQUARE, HEART),
                "A timeless short side-parted cut keeps the silhouette neat and balanced on an oval face, softens a square jaw, and stays proportional on a heart shape.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_CLASSIC
            ),
            Seed(
                "Textured Pixie", listOf(SQUARE, HEART, DIAMOND),
                "Choppy, piece-y layers soften a square jaw, while the side-swept fringe covers a wide forehead — flattering on heart and diamond shapes too.",
                imageUrl = THUMB_TEXTURED_PIXIE
            )
        ),
        (HairLength.SHORT to HairTexture.WAVY) to listOf(
            Seed(
                "Wavy Pixie", listOf(SQUARE, HEART),
                "Natural wave texture softens a square jaw's sharp corners, and a wispy, wave-swept fringe minimizes a heart face's broader forehead.",
                genderStyle = FEMININE,
                imageUrl = THUMB_WAVY_PIXIE
            ),
            Seed(
                "Wavy Bob with Side Bangs", listOf(ROUND, OVAL),
                "A chin-grazing wavy bob with side-swept bangs breaks up roundness with gentle asymmetry, while its soft movement flatters an oval face's natural balance.",
                genderStyle = FEMININE,
                imageUrl = THUMB_WAVY_BOB_SIDE_BANGS
            ),
            Seed(
                "Beach Wave Crop", listOf(DIAMOND, SQUARE),
                "Loose, tousled waves add fullness right at the jaw to balance a diamond face's narrow chin, and the soft movement takes the edge off square angles.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_BEACH_WAVE_CROP
            ),
            Seed(
                "Wavy Shag", listOf(ROUND, HEART),
                "Choppy shag layers add lift at the crown to elongate a round face, and a soft fringe keeps a heart face's wider forehead in proportion.",
                genderStyle = UNISEX,
                imageUrl = THUMB_WAVY_SHAG
            ),
            Seed(
                "Wavy Fringe Crop", listOf(DIAMOND, HEART, OVAL),
                "A full wavy fringe adds width at the forehead for diamond and heart shapes, while an oval face can carry the fringe without losing its balance.",
                genderStyle = UNISEX,
                imageUrl = THUMB_WAVY_FRINGE_CROP
            ),
            Seed(
                "Textured Quiff", listOf(ROUND, SQUARE),
                "Wave-swept hair styled up and back adds height at the crown, elongating round proportions, while tousled, textured ends soften a square jaw's hard corners.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_TEXTURED_QUIFF
            )
        ),
        (HairLength.SHORT to HairTexture.CURLY) to listOf(
            Seed(
                "Curly Bixie", listOf(OVAL, DIAMOND),
                "Curl-hugging layers frame and show off the cheekbones — flattering on a diamond face's angles, while an oval face wears the fuller curl volume with ease.",
                genderStyle = FEMININE,
                imageUrl = THUMB_CURLY_BIXIE
            ),
            Seed(
                "Curly Bob", listOf(HEART, DIAMOND),
                "Curls add natural volume right at the jaw, filling out a heart face's narrow chin or a diamond face's angular jawline for a balanced, rounded finish.",
                genderStyle = FEMININE,
                imageUrl = THUMB_CURLY_BOB
            ),
            Seed(
                "Wash and Go Twist Out Crop", listOf(SQUARE, OVAL),
                "Soft, rounded curl definition all over takes the edge off a square jawline, and an oval face can carry the fuller silhouette with balance to spare.",
                genderStyle = UNISEX,
                imageUrl = THUMB_WASH_AND_GO_TWIST_OUT_CROP
            ),
            Seed(
                "Curly Wolf Cut", listOf(ROUND, SQUARE),
                "Heavy internal layering lifts curls at the crown to elongate a round face, and the shaggy, broken-up ends soften a square jaw's corners.",
                genderStyle = UNISEX,
                imageUrl = THUMB_CURLY_WOLF_CUT
            ),
            Seed(
                "Curly Fringe Crop", listOf(DIAMOND, HEART),
                "A curly fringe adds volume across the forehead, balancing a diamond face's narrow brow line and softening a heart face's broader one.",
                genderStyle = UNISEX,
                imageUrl = THUMB_CURLY_FRINGE_CROP
            ),
            Seed(
                "Curly Crop Fade", listOf(OVAL, ROUND),
                "A tight fade on the sides keeps width in check while natural curl volume on top builds height, elongating round proportions; an oval face wears the cropped curls cleanly.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_CURLY_CROP_FADE
            ),
            Seed(
                "High-Top Curl Fade", listOf(DIAMOND, SQUARE),
                "Fuller curl volume stacked high on top narrows the visual width at the sides, balancing diamond cheekbones, while the faded sides keep a square jaw in check.",
                genderStyle = MASCULINE
            )
        ),
        (HairLength.SHORT to HairTexture.COILY) to listOf(
            Seed(
                "Tapered Coily Crop (TWA)", listOf(ROUND, OVAL),
                "Tapered sides keep width in check while a fuller top adds vertical height — a classic way to elongate a round face; an oval face wears the clean taper effortlessly.",
                genderStyle = UNISEX
            ),
            Seed(
                "Coily Fringe Crop", listOf(DIAMOND, HEART),
                "A soft coily fringe fills out the forehead, balancing a diamond face's narrow brow and a heart face's wider one.",
                genderStyle = UNISEX
            ),
            Seed(
                "Twist-Out Bob", listOf(HEART, DIAMOND),
                "Defined twist-outs styled into a jaw-grazing shape add fullness exactly where heart and diamond faces need it most — right at the chin.",
                genderStyle = FEMININE
            ),
            Seed(
                "Coily Shag with Undercut Sides", listOf(SQUARE, ROUND),
                "Closely tapered sides pull width away from the jaw and cheeks, while shaggy layers on top soften square angles and lift round proportions.",
                genderStyle = MASCULINE
            ),
            Seed(
                "Natural Coily Crop", listOf(OVAL, SQUARE),
                "An evenly rounded silhouette of natural coils softens a square jawline's sharp corners, and an oval face carries the fuller shape with natural balance.",
                genderStyle = UNISEX
            ),
            Seed(
                "High-Top Fade", listOf(ROUND, DIAMOND),
                "Height built up on top elongates round proportions and balances diamond cheekbones, while the tightly faded sides keep the silhouette clean.",
                genderStyle = MASCULINE
            ),
            Seed(
                "Wash-and-Go Coily Pixie", listOf(OVAL, HEART),
                "A cropped wash-and-go shape hugs the head with soft coily volume at the crown, flattering an oval face's balance and gently rounding out a heart face's pointed chin.",
                genderStyle = FEMININE
            )
        ),
        (HairLength.MEDIUM to HairTexture.STRAIGHT) to listOf(
            Seed(
                "Sleek Lob", listOf(OVAL, HEART, DIAMOND),
                "A blunt, shoulder-grazing line adds width at the collarbone, balancing a heart face's narrow chin or a diamond face's angular jaw, and suits an oval face's natural symmetry.",
                genderStyle = FEMININE,
                imageUrl = THUMB_SLEEK_LOB
            ),
            Seed(
                "V-cut Layers", listOf(ROUND, SQUARE),
                "The deep V-point draws the eye downward for an elongating effect on round faces, while cascading layered ends soften a square jaw.",
                genderStyle = UNISEX,
                imageUrl = THUMB_V_CUT_LAYERS
            ),
            Seed(
                "Curtain Bangs Lob", listOf(ROUND, SQUARE, DIAMOND),
                "Center-parted curtain bangs frame the temples, adding width for a diamond face and gently softening round or square outlines without hiding the whole forehead.",
                genderStyle = FEMININE,
                imageUrl = THUMB_CURTAIN_BANGS_LOB
            ),
            Seed(
                "A-Line Bob", listOf(ROUND, OVAL),
                "Longer face-framing pieces in front visually stretch a round face, while the angled back keeps the silhouette sleek and balanced on an oval face.",
                genderStyle = FEMININE,
                imageUrl = THUMB_A_LINE_BOB
            ),
            Seed(
                "Face Framing Layered Cut", listOf(HEART, DIAMOND),
                "Layers cut to hit right at the cheekbones add fullness exactly where heart and diamond faces taper in, creating a softer, more balanced outline.",
                genderStyle = UNISEX,
                imageUrl = THUMB_FACE_FRAMING_LAYERED_CUT
            ),
            Seed(
                "Side Part Comb Over Medium", listOf(OVAL, ROUND),
                "A deep side part with length combed across the top adds asymmetry that elongates round proportions, while an oval face suits the classic, polished line.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_SIDE_PART_COMB_OVER_MEDIUM
            ),
            Seed(
                "Textured Crop Medium Length", listOf(SQUARE, DIAMOND),
                "Longer, textured pieces on top soften a square jaw's sharp corners and add volume that balances a diamond face's narrow forehead.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_TEXTURED_CROP_MEDIUM_LENGTH
            ),
            Seed(
                "Slicked Back", listOf(OVAL, SQUARE, DIAMOND),
                "Hair combed straight back keeps the silhouette sleek and reveals the face, softening a square jaw and balancing a diamond face's cheekbones on an oval outline.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_SLICKED_BACK
            ),
            Seed(
                "Side Bangs", listOf(ROUND, HEART, DIAMOND),
                "Asymmetrical side-swept bangs break up round curves and add soft coverage across a heart or diamond face's wider forehead.",
                genderStyle = UNISEX,
                imageUrl = THUMB_SIDE_BANGS
            )
        ),
        (HairLength.MEDIUM to HairTexture.WAVY) to listOf(
            Seed(
                "Curtain Bangs Wavy Lob", listOf(ROUND, DIAMOND),
                "Wavy curtain bangs add soft width at the temples for a diamond face and break up round proportions with movement instead of hard lines.",
                genderStyle = FEMININE,
                imageUrl = THUMB_CURTAIN_BANGS_WAVY_LOB
            ),
            Seed(
                "Soft Layered Waves", listOf(ROUND, HEART),
                "Loose, wave-enhanced layers add soft volume at the jaw for a heart face and elongate round proportions without adding bulk at the cheeks.",
                genderStyle = FEMININE,
                imageUrl = THUMB_SOFT_LAYERED_WAVES
            ),
            Seed(
                "Wavy Lob with Side Part", listOf(SQUARE, DIAMOND),
                "A deep side part combined with loose waves softens a square jaw's angles and adds gentle width at the cheekbone level for a diamond face.",
                genderStyle = FEMININE,
                imageUrl = THUMB_WAVY_LOB_WITH_SIDE_PART
            ),
            Seed(
                "Beachy Wavy Shag", listOf(OVAL, ROUND),
                "Textured shag layers lift the crown and add movement that elongates round proportions, while an oval face wears the tousled shape with ease.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_BEACH_WAVY_SHAG
            ),
            Seed(
                "Wavy Face-Framing Layers", listOf(HEART, DIAMOND),
                "Wave-textured layers concentrated at the jawline build fullness exactly where heart and diamond faces need extra width.",
                genderStyle = UNISEX,
                imageUrl = THUMB_WAVY_FACE_FRAMING_LAYERS
            ),
            Seed(
                "Textured Side Part", listOf(OVAL, SQUARE),
                "Wave-textured hair swept to a side part softens a square jaw's angles, while an oval face carries the classic, polished shape with ease.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_TEXTURED_SIDE_PART
            ),
            Seed(
                "Medium Layered", listOf(OVAL, ROUND, HEART),
                "Soft layers through the mid-length add movement and volume that elongate round proportions and keep a heart face's forehead in balance.",
                genderStyle = UNISEX,
                imageUrl = THUMB_MEDIUM_LAYERED
            ),
            Seed(
                "Medium Modern Mullet", listOf(OVAL, SQUARE, DIAMOND),
                "Shorter front and sides with longer length at the back add edge without bulk at the cheeks, softening square angles and balancing diamond cheekbones.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_MEDIUM_MODERN_MULLET
            ),
            Seed(
                "Curtains with Layer", listOf(OVAL, HEART, DIAMOND),
                "Center-parted curtain layers frame the temples and add soft coverage for a heart or diamond face while an oval face wears the shape easily.",
                genderStyle = UNISEX,
                imageUrl = THUMB_CURTAINS_WITH_LAYER
            )
        ),
        (HairLength.MEDIUM to HairTexture.CURLY) to listOf(
            Seed(
                "Curly Lob", listOf(HEART, DIAMOND),
                "Curls resting at the shoulder naturally flare outward, adding width right at the jaw to balance a heart face's narrow chin or a diamond face's angles.",
                genderStyle = FEMININE,
                imageUrl = THUMB_CURLY_LOB
            ),
            Seed(
                "Layered Curly Shag", listOf(ROUND, SQUARE),
                "Heavily layered curls lift at the crown to elongate a round face, and the broken-up, shaggy ends soften a square jawline.",
                genderStyle = UNISEX,
                imageUrl = THUMB_LAYERED_CURLY_SHAG
            ),
            Seed(
                "Curly Curtain Bangs", listOf(DIAMOND, HEART),
                "Curly curtain bangs part around the face and add coverage across the forehead — balancing a diamond face's narrow brow and a heart face's wider one.",
                genderStyle = FEMININE,
                imageUrl = THUMB_CURLY_CURTAIN_BANGS
            ),
            Seed(
                "Devacut", listOf(OVAL, SQUARE),
                "Cutting curl-by-curl builds an evenly rounded silhouette that softens a square jaw's corners, while an oval face carries the balanced curl shape naturally.",
                genderStyle = UNISEX,
                imageUrl = THUMB_DEVACUT
            ),
            Seed(
                "Curly V-Cut Layers", listOf(ROUND, DIAMOND),
                "A deep V-shaped layering point draws curls downward for an elongating effect on round faces, and adds width at the base to balance diamond cheekbones.",
                genderStyle = FEMININE,
                imageUrl = THUMB_MEDIUM_CURLY_V_CUT_LAYERS
            ),
            Seed(
                "Curly Quiff", listOf(ROUND, OVAL),
                "Curls swept up and back build height at the crown, elongating round proportions, while an oval face wears the voluminous quiff naturally.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_CURLY_QUIFF
            ),
            Seed(
                "Curly Undercut with Length on Top", listOf(DIAMOND, SQUARE),
                "Faded sides keep width in check while longer curls on top add volume that balances diamond cheekbones and softens a square jaw.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_CURLY_UNDERCUT
            )
        ),
        (HairLength.MEDIUM to HairTexture.COILY) to listOf(
            Seed(
                "Shoulder-Length Twist-Out", listOf(HEART, DIAMOND),
                "A twist-out styled to fall at the shoulders adds fullness right at the jawline, balancing a heart face's narrow chin or a diamond face's angular cheekbones.",
                genderStyle = UNISEX
            ),
            Seed(
                "Layered Coily Shag", listOf(ROUND, SQUARE),
                "Internal layers lift coils at the crown to elongate a round face while softening a square jaw with broken-up, shaggy movement.",
                genderStyle = UNISEX
            ),
            Seed(
                "Wash-and-Go Layered Cut", listOf(OVAL, HEART),
                "Face-framing layers left slightly shorter around the forehead soften a heart face's wider brow, while an oval face wears the fuller wash-and-go shape with balance.",
                genderStyle = UNISEX
            ),
            Seed(
                "Braid-Out Curtain Style", listOf(DIAMOND, SQUARE),
                "A center-parted braid-out falls in curtain-like sections that add width at the temples, balancing diamond cheekbones and softening square angles.",
                genderStyle = FEMININE
            ),
            Seed(
                "Tapered Coily Lob", listOf(ROUND, OVAL),
                "Tapering the sides while keeping length through the crown controls width and adds elongation for a round face, with an oval face carrying the balanced taper easily.",
                genderStyle = MASCULINE
            ),
            Seed(
                "High-Top Twist Fade", listOf(DIAMOND, ROUND),
                "Twists styled tall and full on top narrow the visual width at the cheeks for a diamond face and add elongating height for a round face, with tightly faded sides.",
                genderStyle = MASCULINE
            )
        ),
        (HairLength.LONG to HairTexture.STRAIGHT) to listOf(
            Seed(
                "Long Layers", listOf(OVAL, SQUARE),
                "Cascading layers starting below the chin soften a square jawline's hard edges, and an oval face's balanced proportions carry the long, layered length effortlessly.",
                genderStyle = FEMININE,
                imageUrl = THUMB_LONG_LAYERS
            ),
            Seed(
                "Long Curtain Bangs", listOf(ROUND, DIAMOND),
                "Long curtain bangs frame the temples with soft coverage, adding width for a diamond face's narrow brow while breaking up round proportions with movement.",
                genderStyle = FEMININE,
                imageUrl = THUMB_LONG_CURTAIN_BANGS
            ),
            Seed(
                "Long Blunt", listOf(HEART, DIAMOND),
                "A blunt, weighty end line concentrates width lower down, balancing a heart face's narrow chin or a diamond face's angular jaw.",
                genderStyle = FEMININE,
                imageUrl = THUMB_LONG_BLUNT
            ),
            Seed(
                "Long Shag", listOf(ROUND, OVAL),
                "Piece-y, heavily layered shag hair lifts at the crown for an elongating effect on round faces, while an oval face wears the tousled, textured length with ease.",
                genderStyle = UNISEX,
                imageUrl = THUMB_LONG_SHAG
            ),
            Seed(
                "Sleek Straight with Side Part", listOf(OVAL, DIAMOND),
                "A deep side part shifts volume off-center, softening a diamond face's wide cheekbones, while an oval face's balance suits the sleek, undisturbed length.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_SLEEK_STRAIGHT_WITH_SIDE_PART
            ),
            Seed(
                "Long Slicked Back", listOf(OVAL, SQUARE),
                "Hair combed straight back off the face keeps the silhouette sleek, softening a square jaw's angles, while an oval face's balance suits the undisturbed length.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_LONG_SLICKED_BACK
            ),
            Seed(
                "Long Bob", listOf(OVAL, HEART, DIAMOND),
                "A longer blunt bob adds width at the collarbone to balance a heart face's narrow chin or a diamond face's angular jaw, and suits an oval face's symmetry.",
                genderStyle = FEMININE,
                imageUrl = THUMB_LONG_BOB
            )
        ),
        (HairLength.LONG to HairTexture.WAVY) to listOf(
            Seed(
                "Wavy Lob-to-Long Curtain Fringe", listOf(HEART, DIAMOND),
                "Loose waves add soft volume around the cheeks and jaw, while curtain-style fringe covers a heart face's broader forehead and adds fullness to a diamond face's narrower one.",
                genderStyle = FEMININE,
                imageUrl = THUMB_WAVY_LOB_TO_LONG_CURTAIN_FRINGE
            ),
            Seed(
                "Long Wavy Hair", listOf(OVAL, SQUARE),
                "Long wave-enhanced length breaks up a square jawline with soft movement, and an oval face's natural balance suits the flowing wavy silhouette.",
                genderStyle = FEMININE,
                imageUrl = THUMB_LONG_WAVY
            ),
            Seed(
                "Long Wavy Shag", listOf(ROUND, OVAL),
                "Shaggy layering lifts waves at the crown for an elongating effect on round faces, while an oval face wears the tousled, voluminous length easily.",
                genderStyle = UNISEX,
                imageUrl = THUMB_LONG_WAVY_SHAG
            ),
            Seed(
                "V-Cut Wavy Layers", listOf(ROUND, DIAMOND),
                "A deep V-point elongates a round face while the flared, wave-textured ends add width lower down to balance diamond cheekbones.",
                genderStyle = FEMININE,
                imageUrl = THUMB_V_CUT_WAVY_LAYERS
            ),
            Seed(
                "Face-Framing Wavy Layers", listOf(HEART, SQUARE),
                "Wave-textured layers cut to hit at the jaw add fullness exactly where a heart face's chin narrows, and soften a square jawline's corners.",
                genderStyle = UNISEX,
                imageUrl = THUMB_FACE_FRAMING_WAVY_LAYERS
            ),
            Seed(
                "Long Wavy Slick Back", listOf(OVAL, DIAMOND),
                "Waves combed straight back reveal the forehead and cheekbones, balancing a diamond face's angles, while an oval face's symmetry suits the sleek length.",
                genderStyle = MASCULINE
            ),
            Seed(
                "Surfer Shag", listOf(ROUND, SQUARE),
                "Loose, layered waves left long and tousled add movement that breaks up round curves and softens square angles.",
                genderStyle = MASCULINE
            ),
            Seed(
                "Shaggy Wolfcut", listOf(OVAL, ROUND, SQUARE),
                "Heavy shag layers and a wolf-cut silhouette add crown height to elongate a round face while softening square jaw angles; an oval face wears the shape with ease.",
                genderStyle = UNISEX,
                imageUrl = THUMB_SHAGGY_WOLFCUT
            ),
            Seed(
                "Long Layered Side", listOf(HEART, DIAMOND, OVAL),
                "Long side-swept layers add soft width at the jaw and temples, balancing heart and diamond proportions while framing an oval face naturally.",
                genderStyle = FEMININE,
                imageUrl = THUMB_LONG_LAYERED_SIDE
            )
        ),
        (HairLength.LONG to HairTexture.CURLY) to listOf(
            Seed(
                "Long Curly Layers", listOf(OVAL, HEART),
                "Full, bouncy curls left long add width at the jaw to balance a heart face's narrow chin, while an oval face can wear the volume with ease.",
                genderStyle = FEMININE,
                imageUrl = THUMB_LONG_CURLY_LAYERS
            ),
            Seed(
                "Long Curly Shag", listOf(ROUND, SQUARE),
                "Heavy shag layering lifts curls at the crown to elongate a round face and breaks a square jawline into softer, uneven pieces.",
                genderStyle = UNISEX,
                imageUrl = THUMB_LONG_CURLY_SHAG
            ),
            Seed(
                "Curly Curtain Bangs Long", listOf(DIAMOND, HEART),
                "Curly curtain bangs add soft coverage and volume across the forehead, balancing a diamond face's narrow brow and a heart face's wider one.",
                genderStyle = FEMININE,
                imageUrl = THUMB_CURLY_CURTAIN_BANGS_LONG
            ),
            Seed(
                "Long Curly V-Cut", listOf(ROUND, DIAMOND),
                "A deep V-shaped cutting line draws curls downward for an elongating effect on round faces and flares width at the base for diamond cheekbones.",
                genderStyle = UNISEX,
                imageUrl = THUMB_LONG_CURLY_V_CUT
            ),
            Seed(
                "Long Defined Ringlets with Side Part", listOf(OVAL, SQUARE),
                "A deep side part shifts curl volume off-center to soften a square jaw's symmetry, while an oval face suits the fuller, defined ringlet shape naturally.",
                genderStyle = FEMININE,
                imageUrl = THUMB_LONG_DEFINED_RINGLETS_SIDE_PART
            ),
            Seed(
                "Long Curly Man Bun", listOf(OVAL, DIAMOND),
                "Length pulled back and up leaves volume framing the temples, balancing diamond cheekbones, while an oval face suits the pulled-back style effortlessly.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_LONG_CURLY_MAN_BUN
            ),
            Seed(
                "Curly Shoulder Length Layers", listOf(SQUARE, ROUND),
                "Loose curl layers falling past the jaw soften a square jaw's edges and add elongating length for a round face.",
                genderStyle = MASCULINE,
                imageUrl = THUMB_CURLY_SHOULDER_LENGTH_LAYERS
            )
        ),
        (HairLength.LONG to HairTexture.COILY) to listOf(
            Seed(
                "Long Twist-Out", listOf(OVAL, HEART),
                "Full twist-out volume left long adds width at the jaw, balancing a heart face's narrow chin, while an oval face carries the fuller silhouette with ease.",
                genderStyle = UNISEX
            ),
            Seed(
                "Long Layered Coily Shag", listOf(ROUND, SQUARE),
                "Deep internal layers lift coils at the crown for an elongating effect on round faces and soften a square jaw with broken-up movement.",
                genderStyle = UNISEX
            ),
            Seed(
                "Braid-Out with Curtain Fringe", listOf(DIAMOND, HEART),
                "A center-parted braid-out frames the face in curtain-like sections, adding forehead coverage for a heart face and fullness at the temples for a diamond face.",
                genderStyle = FEMININE
            ),
            Seed(
                "Long Coily V-Cut", listOf(ROUND, DIAMOND),
                "A deep V-point elongates a round face, and the flared width at the ends balances a diamond face's angular cheekbones.",
                genderStyle = FEMININE
            ),
            Seed(
                "Long Natural Coils with Side Part", listOf(OVAL, SQUARE),
                "An off-center part shifts coil volume to one side, softening a square jaw's symmetry, while an oval face wears the full, natural coil length with balance.",
                genderStyle = UNISEX
            ),
            Seed(
                "Long Locs", listOf(OVAL, SQUARE),
                "Locs worn long keep the silhouette narrow and elongated, softening a square jaw, while an oval face carries the length with natural balance.",
                genderStyle = MASCULINE
            ),
            Seed(
                "Long Coily Man Bun", listOf(ROUND, DIAMOND),
                "Coils gathered up and back build height at the crown to elongate round proportions, and the pulled-back style balances diamond cheekbones.",
                genderStyle = MASCULINE
            )
        )
    )

    val allHaircuts: List<Haircut> by lazy {
        catalog.flatMap { (lengthAndTexture, seeds) ->
            val (length, texture) = lengthAndTexture
            seeds.mapIndexed { index, seed ->
                val id = "${length.name}_${texture.name}_$index"
                Haircut(
                    id = id,
                    name = seed.name,
                    imageUrl = seed.imageUrl ?: "https://picsum.photos/seed/$id/400/520",
                    length = length,
                    texture = texture,
                    recommendedFaceShapes = seed.faceShapes,
                    genderStyle = seed.genderStyle,
                    treatment = TreatmentPreference.NONE,
                    description = seed.description
                )
            }
        }
    }

    fun forCluster(length: HairLength, texture: HairTexture): List<Haircut> =
        allHaircuts.filter { it.length == length && it.texture == texture }
}
