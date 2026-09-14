package com.sendmefile77.chronosphere.horde

/** Canonicalises legacy visual tags only for era-relevance filtering. */
internal object HordeHistoricalTagEra {
    fun canonical(raw: String): String = aliases[raw] ?: raw

    private val aliases = mapOf(
        "fire_mastery" to "fire",
        "hearth_culture" to "fire",
        "tool_bearers" to "stone_tools",
        "blood_hunt" to "predator_hunters",
        "gatherer_culture" to "plant_foragers",
        "harpoon_fishing" to "river_fishers",
        "animal_companions" to "animal_taming",
        "tamed_animals" to "animal_taming",
        "ritual_markings" to "ritual_culture",
        "portable_camp" to "nomadic_migration",
        "fixed_settlement" to "permanent_camp",
        "plough_fields" to "plough",
        "canal_fields" to "irrigation",
        "grain_stores" to "grain_farming",
        "herd_culture" to "pastoralism",
        "field_boundaries" to "land_tenure",
        "market_fair" to "seasonal_fairs",
        "road_network" to "village_network",
        "dirt_roads" to "village_network",
        "frontier_homesteads" to "frontier_farms",
        "scribes" to "writing",
        "drains" to "sewers",
        "market_stalls" to "urban_markets",
        "granary_rows" to "state_granaries",
        "city_guard" to "standing_guard",
        "guild_markets" to "merchant_guilds",
        "stone_roads" to "paved_roads",
        "colonial_outposts" to "colonies",
    )
}
