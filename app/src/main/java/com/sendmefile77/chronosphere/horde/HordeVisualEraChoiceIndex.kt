package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.TechnologyEra

/** Keeps old accumulated breakthroughs from visually overwhelming later eras. */
internal object HordeVisualEraChoiceIndex {
    fun eraForSlug(slug: String): TechnologyEra? = when (slug) {
        "fire", "stone_tools", "predator_hunters", "plant_foragers", "river_fishers", "animal_taming",
        "ritual_culture", "nomadic_migration", "permanent_camp" -> TechnologyEra.TRIBAL

        "plough", "irrigation", "grain_farming", "pastoralism", "land_tenure", "seasonal_fairs",
        "village_network", "frontier_farms" -> TechnologyEra.AGRARIAN

        "writing", "sewers", "urban_markets", "state_granaries", "standing_guard", "merchant_guilds",
        "paved_roads", "colonies" -> TechnologyEra.URBAN

        "iron_tools", "metal_weapons", "iron_plough", "mine_economy", "coinage", "warrior_elite",
        "cavalry", "trade_caravans" -> TechnologyEra.METALLURGIC

        "watermills", "manuscript_schools", "crop_rotation", "lordly_estates", "fortifications", "guild_law",
        "pilgrim_roads", "frontier_castles" -> TechnologyEra.MEDIEVAL

        "steam_power", "mechanized_looms", "enclosures", "urban_food_chain", "factory_discipline",
        "public_clinics", "canals", "turnpikes" -> TechnologyEra.EARLY_INDUSTRIAL

        "railways", "steel", "industrial_agriculture", "processed_food", "mass_schooling", "sanitation",
        "mass_migration", "global_shipping" -> TechnologyEra.INDUSTRIAL

        "power_grid", "radio", "chemical_farming", "cold_chain", "broadcast_society", "public_health",
        "motorization", "electric_transit" -> TechnologyEra.ELECTRIC

        "computing", "biotech", "precision_farming", "synthetic_food", "open_networks",
        "algorithmic_governance", "autonomous_transport", "remote_life" -> TechnologyEra.INFORMATION

        "fusion", "asteroid_mining", "closed_ecologies", "engineered_food", "distributed_governance",
        "ai_coordination", "orbital_habitats", "interplanetary_routes" -> TechnologyEra.SPACEFARING

        else -> null
    }

    fun shouldEmphasizeBreakthrough(slug: String, currentEra: TechnologyEra?): Boolean {
        currentEra ?: return true
        val origin = eraForSlug(slug) ?: return true
        return origin.ordinal >= (currentEra.ordinal - 1).coerceAtLeast(0)
    }
}
