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
        val description: String
    )

    private val catalog: Map<Pair<HairLength, HairTexture>, List<Seed>> = mapOf(
        (HairLength.SHORT to HairTexture.STRAIGHT) to listOf(
            Seed(
                "Textured Pixie", listOf(SQUARE, HEART, DIAMOND),
                "Choppy, piece-y layers soften a square jaw, while the side-swept fringe covers a wide forehead — flattering on heart and diamond shapes too."
            ),
            Seed(
                "Classic Blunt Bob", listOf(OVAL, HEART, DIAMOND),
                "A crisp jaw-length line adds width right at the chin, balancing a heart or diamond face's narrower jaw, while an oval face wears the graphic shape with ease."
            ),
            Seed(
                "Side-Swept Fringe Crop", listOf(ROUND, HEART, DIAMOND),
                "An asymmetrical side fringe breaks up a round face's soft curves and disguises a heart or diamond face's wider forehead with soft coverage."
            ),
            Seed(
                "Short Feathered Shag", listOf(ROUND, SQUARE),
                "Feathered layers build height at the crown to elongate a round face, and the soft, ragged ends blur a square jawline's hard corners."
            ),
            Seed(
                "Undercut Crop with Length on Top", listOf(OVAL, ROUND, DIAMOND),
                "Extra length and volume on top draws the eye upward, elongating round proportions and balancing a diamond face's wide cheekbones; clean on an oval face too."
            )
        ),
        (HairLength.SHORT to HairTexture.WAVY) to listOf(
            Seed(
                "Wavy Pixie", listOf(SQUARE, HEART),
                "Natural wave texture softens a square jaw's sharp corners, and a wispy, wave-swept fringe minimizes a heart face's broader forehead."
            ),
            Seed(
                "Wavy Bob with Side Bangs", listOf(ROUND, OVAL),
                "A chin-grazing wavy bob with side-swept bangs breaks up roundness with gentle asymmetry, while its soft movement flatters an oval face's natural balance."
            ),
            Seed(
                "Beach Wave Crop", listOf(DIAMOND, SQUARE),
                "Loose, tousled waves add fullness right at the jaw to balance a diamond face's narrow chin, and the soft movement takes the edge off square angles."
            ),
            Seed(
                "Wavy Shag", listOf(ROUND, HEART),
                "Choppy shag layers add lift at the crown to elongate a round face, and a soft fringe keeps a heart face's wider forehead in proportion."
            ),
            Seed(
                "Wavy Fringe Crop", listOf(DIAMOND, HEART, OVAL),
                "A full wavy fringe adds width at the forehead for diamond and heart shapes, while an oval face can carry the fringe without losing its balance."
            )
        ),
        (HairLength.SHORT to HairTexture.CURLY) to listOf(
            Seed(
                "Bixie", listOf(OVAL, DIAMOND),
                "Curl-hugging layers frame and show off the cheekbones — flattering on a diamond face's angles, while an oval face wears the fuller curl volume with ease."
            ),
            Seed(
                "Curly Bob", listOf(HEART, DIAMOND),
                "Curls add natural volume right at the jaw, filling out a heart face's narrow chin or a diamond face's angular jawline for a balanced, rounded finish."
            ),
            Seed(
                "Wash-and-Go Twist-Out Crop", listOf(SQUARE, OVAL),
                "Soft, rounded curl definition all over takes the edge off a square jawline, and an oval face can carry the fuller silhouette with balance to spare."
            ),
            Seed(
                "Curly Wolf Cut", listOf(ROUND, SQUARE),
                "Heavy internal layering lifts curls at the crown to elongate a round face, and the shaggy, broken-up ends soften a square jaw's corners."
            ),
            Seed(
                "Curly Fringe Crop", listOf(DIAMOND, HEART),
                "A curly fringe adds volume across the forehead, balancing a diamond face's narrow brow line and softening a heart face's broader one."
            )
        ),
        (HairLength.SHORT to HairTexture.COILY) to listOf(
            Seed(
                "Tapered Coily Crop (TWA)", listOf(ROUND, OVAL),
                "Tapered sides keep width in check while a fuller top adds vertical height — a classic way to elongate a round face; an oval face wears the clean taper effortlessly."
            ),
            Seed(
                "Coily Fringe Crop", listOf(DIAMOND, HEART),
                "A soft coily fringe fills out the forehead, balancing a diamond face's narrow brow and a heart face's wider one."
            ),
            Seed(
                "Twist-Out Bob", listOf(HEART, DIAMOND),
                "Defined twist-outs styled into a jaw-grazing shape add fullness exactly where heart and diamond faces need it most — right at the chin."
            ),
            Seed(
                "Coily Shag with Undercut Sides", listOf(SQUARE, ROUND),
                "Closely tapered sides pull width away from the jaw and cheeks, while shaggy layers on top soften square angles and lift round proportions."
            ),
            Seed(
                "Natural Coily Crop", listOf(OVAL, SQUARE),
                "An evenly rounded silhouette of natural coils softens a square jawline's sharp corners, and an oval face carries the fuller shape with natural balance."
            )
        ),
        (HairLength.MEDIUM to HairTexture.STRAIGHT) to listOf(
            Seed(
                "Sleek Lob", listOf(OVAL, HEART, DIAMOND),
                "A blunt, shoulder-grazing line adds width at the collarbone, balancing a heart face's narrow chin or a diamond face's angular jaw, and suits an oval face's natural symmetry."
            ),
            Seed(
                "V-Cut Layers", listOf(ROUND, SQUARE),
                "The deep V-point draws the eye downward for an elongating effect on round faces, while cascading layered ends soften a square jaw."
            ),
            Seed(
                "Curtain Bangs Lob", listOf(ROUND, SQUARE, DIAMOND),
                "Center-parted curtain bangs frame the temples, adding width for a diamond face and gently softening round or square outlines without hiding the whole forehead."
            ),
            Seed(
                "A-Line Bob", listOf(ROUND, OVAL),
                "Longer face-framing pieces in front visually stretch a round face, while the angled back keeps the silhouette sleek and balanced on an oval face."
            ),
            Seed(
                "Face-Framing Layered Cut", listOf(HEART, DIAMOND),
                "Layers cut to hit right at the cheekbones add fullness exactly where heart and diamond faces taper in, creating a softer, more balanced outline."
            )
        ),
        (HairLength.MEDIUM to HairTexture.WAVY) to listOf(
            Seed(
                "Curtain Bangs Wavy Lob", listOf(ROUND, DIAMOND),
                "Wavy curtain bangs add soft width at the temples for a diamond face and break up round proportions with movement instead of hard lines."
            ),
            Seed(
                "Soft Layered Waves", listOf(ROUND, HEART),
                "Loose, wave-enhanced layers add soft volume at the jaw for a heart face and elongate round proportions without adding bulk at the cheeks."
            ),
            Seed(
                "Wavy Lob with Side Part", listOf(SQUARE, DIAMOND),
                "A deep side part combined with loose waves softens a square jaw's angles and adds gentle width at the cheekbone level for a diamond face."
            ),
            Seed(
                "Beachy Wavy Shag", listOf(OVAL, ROUND),
                "Textured shag layers lift the crown and add movement that elongates round proportions, while an oval face wears the tousled shape with ease."
            ),
            Seed(
                "Wavy Face-Framing Layers", listOf(HEART, DIAMOND),
                "Wave-textured layers concentrated at the jawline build fullness exactly where heart and diamond faces need extra width."
            )
        ),
        (HairLength.MEDIUM to HairTexture.CURLY) to listOf(
            Seed(
                "Curly Lob", listOf(HEART, DIAMOND),
                "Curls resting at the shoulder naturally flare outward, adding width right at the jaw to balance a heart face's narrow chin or a diamond face's angles."
            ),
            Seed(
                "Layered Curly Shag", listOf(ROUND, SQUARE),
                "Heavily layered curls lift at the crown to elongate a round face, and the broken-up, shaggy ends soften a square jawline."
            ),
            Seed(
                "Curly Curtain Bangs", listOf(DIAMOND, HEART),
                "Curly curtain bangs part around the face and add coverage across the forehead — balancing a diamond face's narrow brow and a heart face's wider one."
            ),
            Seed(
                "Devacut", listOf(OVAL, SQUARE),
                "Cutting curl-by-curl builds an evenly rounded silhouette that softens a square jaw's corners, while an oval face carries the balanced curl shape naturally."
            ),
            Seed(
                "Curly V-Cut Layers", listOf(ROUND, DIAMOND),
                "A deep V-shaped layering point draws curls downward for an elongating effect on round faces, and adds width at the base to balance diamond cheekbones."
            )
        ),
        (HairLength.MEDIUM to HairTexture.COILY) to listOf(
            Seed(
                "Shoulder-Length Twist-Out", listOf(HEART, DIAMOND),
                "A twist-out styled to fall at the shoulders adds fullness right at the jawline, balancing a heart face's narrow chin or a diamond face's angular cheekbones."
            ),
            Seed(
                "Layered Coily Shag", listOf(ROUND, SQUARE),
                "Internal layers lift coils at the crown to elongate a round face while softening a square jaw with broken-up, shaggy movement."
            ),
            Seed(
                "Wash-and-Go Layered Cut", listOf(OVAL, HEART),
                "Face-framing layers left slightly shorter around the forehead soften a heart face's wider brow, while an oval face wears the fuller wash-and-go shape with balance."
            ),
            Seed(
                "Braid-Out Curtain Style", listOf(DIAMOND, SQUARE),
                "A center-parted braid-out falls in curtain-like sections that add width at the temples, balancing diamond cheekbones and softening square angles."
            ),
            Seed(
                "Tapered Coily Lob", listOf(ROUND, OVAL),
                "Tapering the sides while keeping length through the crown controls width and adds elongation for a round face, with an oval face carrying the balanced taper easily."
            )
        ),
        (HairLength.LONG to HairTexture.STRAIGHT) to listOf(
            Seed(
                "Long Layers", listOf(OVAL, SQUARE),
                "Cascading layers starting below the chin soften a square jawline's hard edges, and an oval face's balanced proportions carry the long, layered length effortlessly."
            ),
            Seed(
                "Long Curtain Bangs", listOf(ROUND, DIAMOND),
                "Long curtain bangs frame the temples with soft coverage, adding width for a diamond face's narrow brow while breaking up round proportions with movement."
            ),
            Seed(
                "Long Blunt with Face-Framing Layers", listOf(HEART, DIAMOND),
                "A blunt, weighty end line concentrates width lower down, balancing a heart face's narrow chin or a diamond face's angular jaw."
            ),
            Seed(
                "Long Shag", listOf(ROUND, OVAL),
                "Piece-y, heavily layered shag hair lifts at the crown for an elongating effect on round faces, while an oval face wears the tousled, textured length with ease."
            ),
            Seed(
                "Sleek Straight with Side Part", listOf(OVAL, DIAMOND),
                "A deep side part shifts volume off-center, softening a diamond face's wide cheekbones, while an oval face's balance suits the sleek, undisturbed length."
            )
        ),
        (HairLength.LONG to HairTexture.WAVY) to listOf(
            Seed(
                "Wavy Lob-to-Long Curtain Fringe", listOf(HEART, DIAMOND),
                "Loose waves add soft volume around the cheeks and jaw, while curtain-style fringe covers a heart face's broader forehead and adds fullness to a diamond face's narrower one."
            ),
            Seed(
                "Long Layers with Waves", listOf(OVAL, SQUARE),
                "Wave-enhanced layers break up a square jawline with soft movement, and an oval face's natural balance suits the long, layered wave pattern."
            ),
            Seed(
                "Long Wavy Shag", listOf(ROUND, OVAL),
                "Shaggy layering lifts waves at the crown for an elongating effect on round faces, while an oval face wears the tousled, voluminous length easily."
            ),
            Seed(
                "V-Cut Wavy Layers", listOf(ROUND, DIAMOND),
                "A deep V-point elongates a round face while the flared, wave-textured ends add width lower down to balance diamond cheekbones."
            ),
            Seed(
                "Face-Framing Wavy Layers", listOf(HEART, SQUARE),
                "Wave-textured layers cut to hit at the jaw add fullness exactly where a heart face's chin narrows, and soften a square jawline's corners."
            )
        ),
        (HairLength.LONG to HairTexture.CURLY) to listOf(
            Seed(
                "Long Curly Layers", listOf(OVAL, HEART),
                "Full, bouncy curls left long add width at the jaw to balance a heart face's narrow chin, while an oval face can wear the volume with ease."
            ),
            Seed(
                "Long Curly Shag", listOf(ROUND, SQUARE),
                "Heavy shag layering lifts curls at the crown to elongate a round face and breaks a square jawline into softer, uneven pieces."
            ),
            Seed(
                "Curly Curtain Bangs (Long)", listOf(DIAMOND, HEART),
                "Curly curtain bangs add soft coverage and volume across the forehead, balancing a diamond face's narrow brow and a heart face's wider one."
            ),
            Seed(
                "Long Curly V-Cut", listOf(ROUND, DIAMOND),
                "A deep V-shaped cutting line draws curls downward for an elongating effect on round faces and flares width at the base for diamond cheekbones."
            ),
            Seed(
                "Long Defined Ringlets with Side Part", listOf(OVAL, SQUARE),
                "A deep side part shifts curl volume off-center to soften a square jaw's symmetry, while an oval face suits the fuller, defined ringlet shape naturally."
            )
        ),
        (HairLength.LONG to HairTexture.COILY) to listOf(
            Seed(
                "Long Twist-Out", listOf(OVAL, HEART),
                "Full twist-out volume left long adds width at the jaw, balancing a heart face's narrow chin, while an oval face carries the fuller silhouette with ease."
            ),
            Seed(
                "Long Layered Coily Shag", listOf(ROUND, SQUARE),
                "Deep internal layers lift coils at the crown for an elongating effect on round faces and soften a square jaw with broken-up movement."
            ),
            Seed(
                "Braid-Out with Curtain Fringe", listOf(DIAMOND, HEART),
                "A center-parted braid-out frames the face in curtain-like sections, adding forehead coverage for a heart face and fullness at the temples for a diamond face."
            ),
            Seed(
                "Long Coily V-Cut", listOf(ROUND, DIAMOND),
                "A deep V-point elongates a round face, and the flared width at the ends balances a diamond face's angular cheekbones."
            ),
            Seed(
                "Long Natural Coils with Side Part", listOf(OVAL, SQUARE),
                "An off-center part shifts coil volume to one side, softening a square jaw's symmetry, while an oval face wears the full, natural coil length with balance."
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
                    imageUrl = "https://picsum.photos/seed/$id/400/520",
                    length = length,
                    texture = texture,
                    recommendedFaceShapes = seed.faceShapes,
                    treatment = TreatmentPreference.NONE,
                    description = seed.description
                )
            }
        }
    }

    fun forCluster(length: HairLength, texture: HairTexture): List<Haircut> =
        allHaircuts.filter { it.length == length && it.texture == texture }
}
