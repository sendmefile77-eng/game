package com.sendmefile77.chronosphere.horde

/** Maps historical visual and century-choice tags into concise renderer language. */
internal object HordeHistoricalVisualPrompt {
    fun fragment(tags: Set<String>): String {
        fun value(prefix: String): String? = tags.asSequence()
            .filter { it.startsWith(prefix) && it.length > prefix.length }
            .map { it.removePrefix(prefix) }
            .sorted()
            .firstOrNull()

        val parts = buildList {
            value("cloth:")?.let { add("clothing/material tradition ${humanize(it)}") }
            value("jewel:")?.let { add("jewellery tradition ${humanize(it)}") }
            value("hair:")?.let { add("historical hairstyle ${humanize(it)}") }
            value("body-norm:")?.let { add("body presentation norm ${humanize(it)}") }
            value("arch:")?.let { add("architecture ${humanize(it)}") }
            value("set-bias:")?.let { add("preferred setting ${humanize(it)}") }
            value("cosmetic:")?.let { add("cosmetic tradition ${humanize(it)}") }
            value("publicness:")?.let { add("social visibility ${humanize(it)}") }
            tags.asSequence().filter { it.startsWith("hist:") }.map { it.removePrefix("hist:") }.sorted().take(2)
                .forEach { add("historical pressure ${humanize(it)}") }
            tags.asSequence().filter { it.startsWith("foundation:") }.map { it.removePrefix("foundation:") }.sorted().take(2)
                .forEach { add("civilizational foundation ${humanize(it)}") }
            tags.asSequence().filter { it.startsWith("policy:") }.map { it.removePrefix("policy:") }.sorted().take(2)
                .forEach { add("visible everyday lifestyle ${humanize(it)}") }
            tags.asSequence().filter { it.startsWith("era-choice:") }.map { it.substringAfterLast(':') }.sorted().take(3)
                .forEach { add(choiceVisual(it)) }
        }
        return parts.distinct().joinToString(", ")
    }

    fun signature(tags: Set<String>): String = tags.asSequence()
        .filter { tag -> PREFIXES.any(tag::startsWith) }
        .sorted()
        .joinToString(";")

    private fun choiceVisual(slug: String): String = when (slug) {
        "fire" -> "hearth fire as a central technology, soot on skin, charred wood and fire-hardened tools"
        "stone_tools" -> "flint knives, stone axes and hide scrapers visibly carried and used"
        "predator_hunters" -> "feral big-game hunter culture, blood-stained hides and hands, claw-like hunting trophies, bone tools"
        "plant_foragers" -> "woven gathering baskets, roots, berries, leaves and plant dyes, clean herb-focused camp"
        "river_fishers" -> "harpoons, fish traps, nets and wet riverbank working gear"
        "animal_taming" -> "tamed dogs or herd animals living beside people and assisting daily work"
        "nomadic_migration" -> "portable hide shelters, packs, travel gear and a visibly mobile camp"
        "permanent_camp" -> "fixed huts, storage pits, permanent hearths and accumulated workshop debris"
        "plough" -> "wooden ploughs, furrowed fields and draft animals"
        "irrigation" -> "irrigation canals, sluices and wet cultivated fields"
        "grain_farming" -> "grain bundles, threshing floors and large storage jars"
        "pastoralism" -> "large managed herds, milk vessels and leather-working gear"
        "writing" -> "scribes, tablets or manuscripts and visible record keeping"
        "sewers" -> "stone drains, channels and planned urban sanitation"
        "iron_tools" -> "iron axes, chisels, agricultural tools and forge scale"
        "metal_weapons" -> "metal weapons, shields and organized armed retainers"
        "coinage" -> "coins, scales and market accounting visible in everyday trade"
        "cavalry" -> "horse tack, mounted couriers and cavalry equipment"
        "watermills" -> "water wheels, mill machinery and flour work"
        "fortifications" -> "stone walls, towers and defensive gates shaping the settlement"
        "steam_power" -> "steam engines, pistons, belts, soot and coal smoke"
        "mechanized_looms" -> "mechanical looms, textile machinery and dense mill interiors"
        "railways" -> "steam railways, iron tracks, stations and industrial freight"
        "steel" -> "steel beams, furnaces and heavy machine tools"
        "power_grid" -> "electric lamps, wires, motors and a visibly electrified street"
        "radio" -> "radio sets, antennae and broadcast equipment"
        "computing" -> "computers, terminals, screens and digital workstations"
        "biotech" -> "biotechnology labs, sterile equipment and engineered biological materials"
        "fusion" -> "fusion infrastructure, advanced energy systems and luminous reactor architecture"
        "asteroid_mining" -> "orbital mining machinery, pressure suits and asteroid material handling"
        "orbital_habitats" -> "large orbital habitats, pressure architecture and artificial-gravity living spaces"
        else -> "visible material legacy of ${humanize(slug)} in clothing, tools and surroundings"
    }

    private fun humanize(value: String): String = value.replace('_', ' ').replace('-', ' ').replace('+', ' ')

    private val PREFIXES = listOf(
        "cloth:", "jewel:", "hair:", "body-norm:", "arch:", "set-bias:",
        "cosmetic:", "publicness:", "hist:", "foundation:", "policy:", "era-choice:", "era:",
    )
}
