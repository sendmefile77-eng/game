package com.sendmefile77.chronosphere.horde

/**
 * Keeps a few adult/historical intimacy cues when Illustrious compresses a long
 * Horde prompt. Complements [LocalDreamMaterialCueBridge], which is SFW-only.
 */
internal object LocalDreamAdultCueBridge {
    fun fragment(sourcePrompt: String): String {
        val source = sourcePrompt.lowercase()
        return CUES.asSequence()
            .filter { (needle, _) -> source.contains(needle) }
            .map { (_, cue) -> cue }
            .distinct()
            .take(MAX_CUES)
            .joinToString(", ")
    }

    private const val MAX_CUES = 4

    private val CUES = listOf(
        "rotating habitat" to "orbital habitat cabin, viewport, webbing bunk, artificial gravity",
        "pressure-suit dropped" to "dropped pressure suit, lock-adjacent bunk, ore dust",
        "sealed ecology" to "garden-bay bunk, recycled air, planting trays",
        "surveilled-apartment" to "surveilled apartment sex, civic dashboard glow",
        "networked intimacy" to "networked sex, devices face-down, connected apartment",
        "home-office bed" to "daylight home-office bed, screens off",
        "wired-room sex" to "wired room, ceiling bulb, radio cabinet",
        "after-shift sex" to "after-shift tenement sex, brick, coal dust",
        "mill-hand coupling" to "mill loft sex, lint on skin, rented privacy",
        "solar-chamber" to "solar chamber, lord's bed, estate window",
        "elite warrior undress" to "warrior elite undress, armour piled, rank sex",
        "workshop sex after smithing" to "forge-side sex, soot, warm iron tools",
        "market-stall coupling" to "market stall sex, awning, baskets still out",
        "ritual copulation" to "ritual sex, ochre on breasts and genitals, painted posts",
        "post-hunt coupling" to "post-hunt sex, drying meat, spears, smeared thighs",
        "sex on stacked hides" to "hearth sex on hides, woodsmoke, shared heat",
        "harvest-yard sex" to "granary-yard sex, grain sacks, dusty sunlight",
        "household bed" to "household fertility bed, field tools in the doorway",
        "watch-post or gatehouse" to "gatehouse tryst, weapon racks, guard privilege",
        "guild-house patronage" to "guild upstairs sex, ledgers, patronage bed",
        "caravan-yard night sex" to "caravan yard sex, packs, foreign cloth",
        "railway-hotel" to "railway hotel sex, coal smoke, iron tracks outside",
        "port-room" to "port room liaison, dock warehouse, short-stay sex",
        "festival sex" to "festival sex just off the gathering, shared food nearby",
        "homecoming or pre-raid" to "homecoming sex, weapons in reach",
        "tamed dogs" to "adult person in a hide shelter, leather straps on a post, no living animal in frame",
        "animal pen" to "human sleeping shelter, adult nude body as the subject",
        "animal_taming" to "adult camp sex, domestication gear only as background",
        "work with animals" to "work-worn adult person as the subject",
        "herd animals" to "herding tack stored aside, adult person as the subject",
    )
}
