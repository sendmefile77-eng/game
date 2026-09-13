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

    /** A selected century option stores its slug at the end of choiceId. */
    fun eraChoiceFragment(choiceId: String?): String {
        val id = choiceId?.takeIf { it.startsWith("era-") } ?: return ""
        val slug = KNOWN_CHOICE_SLUGS.firstOrNull { candidate -> id.endsWith("-$candidate") } ?: return ""
        return choiceVisual(slug)
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
        "ritual_culture" -> "ritual body paint, carved ceremonial objects and communal sacred space"
        "nomadic_migration" -> "portable hide shelters, packs, travel gear and a visibly mobile camp"
        "permanent_camp" -> "fixed huts, storage pits, permanent hearths and accumulated workshop debris"
        "plough" -> "wooden ploughs, furrowed fields and draft animals"
        "irrigation" -> "irrigation canals, sluices and wet cultivated fields"
        "grain_farming" -> "grain bundles, threshing floors and large storage jars"
        "pastoralism" -> "large managed herds, milk vessels and leather-working gear"
        "writing" -> "scribes, tablets or manuscripts and visible record keeping"
        "sewers" -> "stone drains, channels and planned urban sanitation"
        "state_granaries" -> "large guarded granaries, sacks and measured grain stores"
        "standing_guard" -> "organized city guards, watch posts and controlled gates"
        "merchant_guilds" -> "merchant guild signs, ledgers, scales and specialized market stalls"
        "paved_roads" -> "broad paved roads carrying carts, messengers and organized traffic"
        "iron_tools" -> "iron axes, chisels, agricultural tools and forge scale"
        "metal_weapons" -> "metal weapons, shields and organized armed retainers"
        "coinage" -> "coins, scales and market accounting visible in everyday trade"
        "warrior_elite" -> "distinct warrior elite with superior armor, decorated weapons and status gear"
        "cavalry" -> "horse tack, mounted couriers and cavalry equipment"
        "trade_caravans" -> "pack animals, guarded caravans and long-distance trade goods"
        "watermills" -> "water wheels, mill machinery and flour work"
        "manuscript_schools" -> "scriptoria, manuscripts, ink and trained scribes"
        "crop_rotation" -> "organized strip fields with visibly different crops in rotation"
        "fortifications" -> "stone walls, towers and defensive gates shaping the settlement"
        "guild_law" -> "guild halls, workshop emblems and regulated craft streets"
        "steam_power" -> "steam engines, pistons, belts, soot and coal smoke"
        "mechanized_looms" -> "mechanical looms, textile machinery and dense mill interiors"
        "public_clinics" -> "early public clinics, medical instruments and organized treatment rooms"
        "railways" -> "steam railways, iron tracks, stations and industrial freight"
        "steel" -> "steel beams, furnaces and heavy machine tools"
        "mass_schooling" -> "large public classrooms, books and mass literacy"
        "sanitation" -> "water mains, sewer works and visibly cleaner industrial streets"
        "power_grid" -> "electric lamps, wires, motors and a visibly electrified street"
        "radio" -> "radio sets, antennae and broadcast equipment"
        "public_health" -> "organized hospitals, public health workers and clinical equipment"
        "motorization" -> "motor vehicles, fuel infrastructure and roads redesigned around engines"
        "computing" -> "computers, terminals, screens and digital workstations"
        "biotech" -> "biotechnology labs, sterile equipment and engineered biological materials"
        "precision_farming" -> "sensor-guided farming machinery and tightly managed cultivated land"
        "algorithmic_governance" -> "data-rich civic control rooms and automated public infrastructure"
        "autonomous_transport" -> "driverless vehicles, sensor networks and automated logistics"
        "fusion" -> "fusion infrastructure, advanced energy systems and luminous reactor architecture"
        "asteroid_mining" -> "orbital mining machinery, pressure suits and asteroid material handling"
        "closed_ecologies" -> "sealed habitat farms, recycled water systems and closed ecological loops"
        "ai_coordination" -> "pervasive AI coordination interfaces embedded into everyday infrastructure"
        "orbital_habitats" -> "large orbital habitats, pressure architecture and artificial-gravity living spaces"
        "interplanetary_routes" -> "interplanetary transports, docking infrastructure and off-world cargo"
        else -> "visible material legacy of ${humanize(slug)} in clothing, tools and surroundings"
    }

    private fun humanize(value: String): String = value.replace('_', ' ').replace('-', ' ').replace('+', ' ')

    private val PREFIXES = listOf(
        "cloth:", "jewel:", "hair:", "body-norm:", "arch:", "set-bias:",
        "cosmetic:", "publicness:", "hist:", "foundation:", "policy:", "era-choice:", "era:",
    )

    private val KNOWN_CHOICE_SLUGS = setOf(
        "fire", "stone_tools", "predator_hunters", "plant_foragers", "river_fishers", "animal_taming",
        "ritual_culture", "nomadic_migration", "permanent_camp", "plough", "irrigation", "grain_farming",
        "pastoralism", "land_tenure", "seasonal_fairs", "village_network", "frontier_farms", "writing", "sewers",
        "urban_markets", "state_granaries", "standing_guard", "merchant_guilds", "paved_roads", "colonies",
        "iron_tools", "metal_weapons", "iron_plough", "mine_economy", "coinage", "warrior_elite", "cavalry",
        "trade_caravans", "watermills", "manuscript_schools", "crop_rotation", "lordly_estates", "fortifications",
        "guild_law", "pilgrim_roads", "frontier_castles", "steam_power", "mechanized_looms", "enclosures",
        "urban_food_chain", "factory_discipline", "public_clinics", "canals", "turnpikes", "railways", "steel",
        "industrial_agriculture", "processed_food", "mass_schooling", "sanitation", "mass_migration", "global_shipping",
        "power_grid", "radio", "chemical_farming", "cold_chain", "broadcast_society", "public_health", "motorization",
        "electric_transit", "computing", "biotech", "precision_farming", "synthetic_food", "open_networks",
        "algorithmic_governance", "autonomous_transport", "remote_life", "fusion", "asteroid_mining", "closed_ecologies",
        "engineered_food", "distributed_governance", "ai_coordination", "orbital_habitats", "interplanetary_routes",
    )
}
