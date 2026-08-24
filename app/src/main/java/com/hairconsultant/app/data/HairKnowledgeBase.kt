package com.hairconsultant.app.data

/**
 * Curated, source-grounded hairstyling knowledge fed to the AI consultant chatbot
 * ([com.hairconsultant.app.data.remote.gemini.GeminiChatRepository]) as its system instruction,
 * so its suggestions are reasoned from real hairstyling/dermatology guidance instead of a
 * hard-coded decision tree. Covers: face-shape principles, the Andre Walker hair-type system
 * and its care needs, the two chemical treatments this app tracks (rebonding, perming), and
 * lifestyle-fit considerations (maintenance load, climate, workplace norms).
 */
object HairKnowledgeBase {

    val referenceText: String = """
        FACE SHAPE PRINCIPLES
        - Oval: naturally balanced proportions; almost any length, texture, or layering works.
        - Round: soft curves, similar width and length. Needs added height/angles — layers that
          lift the crown, side-swept asymmetry, or an elongating V-cut/deep side part. Avoid
          styles that add width at the cheeks (e.g. a blunt bob landing exactly at the jaw).
        - Square: strong, angular jawline. Needs softening — waves, soft layers, side-swept
          fringe, or rounded curl shapes. Avoid sharp geometric cuts or blunt straight-across
          lines that echo the jaw's hard corners.
        - Heart: wider forehead, narrower chin. Needs width added back at the jaw — chin-length
          bobs, waves/curls that flare at the jaw, or curtain bangs/fringe to soften and cover
          the wider forehead. Avoid extra volume piled at the crown, which exaggerates the taper.
        - Diamond: narrow forehead and chin, wide cheekbones. Needs width at the forehead (fringe,
          curtain bangs) and at the jaw (soft waves/layers), while avoiding styles that add more
          width at the cheekbone line (e.g. slicked-back, ear-exposing cuts).

        HAIR TYPE / TEXTURE GUIDE (Andre Walker system, 1-4 with A/B/C sub-types for pattern size)
        - Type 1 (Straight): no natural curl pattern. Shows sharp, clean lines well; tends to look
          flat at the crown without layers or a blunt cut for visual weight. Gets oily faster since
          scalp oil travels the length easily.
        - Type 2 (Wavy, A-C): loose S-waves. Naturally adds movement/volume without heaviness;
          holds styling reasonably well but frizzes in humidity, especially the looser-to-frizzier
          2C end of the range.
        - Type 3 (Curly, A-C): defined spring/corkscrew curls, from marker-width (3A) to pencil-
          width or tighter (3C). Curls shrink hair's visible length, add natural volume/width, and
          need extra moisture — curly hair is drier by nature since scalp oil struggles to travel
          down the curl's bends. Cutting dry, curl-by-curl, avoids cutting the shape too short.
        - Type 4 (Coily, A-C): tight coils to a Z-pattern with minimal visible pattern (4C) when
          not stretched. The most fragile and driest texture with the highest shrinkage (a coil can
          look far shorter than its stretched length); benefits most from low-manipulation styles
          (twist-outs, braid-outs, tapered crops) and consistent moisture/sealing.
        General cutting note: curly and coily hair (types 3-4) is best cut dry on its natural,
        defined pattern — cutting it wet stretches the strand and can remove more shape than
        intended once it dries and shrinks back.

        CHEMICAL TREATMENTS
        - Rebonding: a permanent chemical straightening process (a relaxing cream loosens the
          hair's natural bonds, a flat iron reshapes it straight, a neutralizer locks the new
          structure in). Effects last roughly 6-12 months as hair grows out, but the treated
          portion never reverts. Meaningfully increases breakage risk and dryness because the
          hair shaft is chemically weakened; needs sulfate-free shampoo, frequent conditioning,
          limited heat styling, and trims every 6-8 weeks to manage split ends. Not advisable on
          hair that is already very thin, bleached, colored, or damaged, or on a sensitive scalp.
        - Perming: chemically (or, for a digital perm, heat-assisted) restructures hair to curl
          rather than straighten it. A cold perm gives tighter, more defined curls and lasts about
          2-4 months; a digital perm gives looser, more natural-looking waves and lasts about 4-8
          months, generally with an easier air-dry routine afterward. Both are riskier on hair
          that is already bleached, over-processed, or chemically relaxed/rebonded — virgin,
          unprocessed hair holds a perm's curl best and suffers the least damage.
        - Either treatment adds an ongoing maintenance step (special shampoo, regular trims,
          reduced heat styling) on top of whatever a haircut itself requires, and neither should
          be layered on top of the other or on already-compromised hair without a stylist's
          in-person assessment.

        LIFESTYLE FIT
        - Maintenance load scales with length and how much daily styling a cut needs to hold its
          shape: short, tapered, or heavily layered cuts need the least daily effort and the most
          frequent trims (every 4-6 weeks) to stay sharp; medium and long lengths need less
          frequent trims but more daily styling time (blow-drying, product) to look intentional.
        - Humid or hot climates make loose waves, curls, and coils more prone to frizz and
          volume expansion; shorter, tighter, or more heavily layered/tapered shapes hold their
          silhouette better in humidity than long, loosely-layered lengths.
        - Active/athletic routines (frequent workouts, sweat, helmets) favor shorter or easily
          tied-back lengths that need minimal daily restyling; chemically treated hair (rebonded
          or permed) needs extra care around chlorine and heavy sweating since both accelerate
          the treatment's breakdown and dryness.
        - Workplaces with a conservative dress code generally read shorter, cleanly-shaped cuts
          (or long hair simply tied back) as polished with minimal daily effort; more elaborate
          layering or curl-heavy styles usually need more active daily styling to look intentional
          rather than undone.
    """.trimIndent()
}
