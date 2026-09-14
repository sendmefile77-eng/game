package com.sendmefile77.chronosphere.horde

/**
 * Non-adult material visual language for historical choices.
 *
 * This object deliberately describes tools, infrastructure, work, settlement form and social
 * organisation only. Adult/erotic enrichment is a separate layer and must not live here.
 */
internal object HordeDecisionVisualCue {
    fun forChoiceId(choiceId: String?): String {
        val id = choiceId?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
        if (id.startsWith("era-")) {
            val slug = KNOWN_SLUGS.firstOrNull { candidate -> id.endsWith("-$candidate") }
            if (slug != null) return forSlug(slug)
        }
        return dilemmaOrDecisionCue(id)
    }

    fun forHistoricalTag(prefix: String, rawValue: String): String {
        val value = rawValue.trim()
        val slug = LEGACY_ALIASES[value] ?: value
        return if (slug in KNOWN_SLUGS) {
            forSlug(slug)
        } else {
            when (prefix) {
                "foundation:" -> "established material foundation of ${humanize(value)} visible in ordinary tools, buildings and work"
                "policy:" -> "everyday social and economic practice of ${humanize(value)} visible in how people work, travel and organise space"
                "hist:" -> "lasting historical trace of ${humanize(value)} visible as worn infrastructure, habits and material culture"
                else -> "material trace of ${humanize(value)}"
            }
        }
    }

    fun forSlug(slug: String): String = when (slug) {
        // Tribal
        "fire" -> "central hearth fires, smoke-darkened shelters, charred cooking stones, fire-hardened wooden tools and stored fuel"
        "stone_tools" -> "flint knives, stone axes, hide scrapers, knapping debris and people visibly using stone tools"
        "predator_hunters" -> "big-game hunting camp, heavy spears, butchered game, drying meat, animal-hide gear and hunting trophies"
        "plant_foragers" -> "woven gathering baskets, roots, berries, herbs, seed processing stones and racks of dried plants"
        "river_fishers" -> "riverbank camp with harpoons, fish traps, woven nets, drying fish and simple watercraft"
        "animal_taming" -> "tamed dogs or herd animals living beside people, leashes, pens, feed piles and animals assisting daily work"
        "ritual_culture" -> "painted ritual posts, ochre markings, carved ceremonial objects, a communal sacred space and repeated symbolic motifs"
        "nomadic_migration" -> "portable hide shelters, bundled possessions, pack frames, travel gear and a camp designed to be dismantled quickly"
        "permanent_camp" -> "fixed huts, storage pits, fenced work areas, permanent hearths and accumulated workshop debris"

        // Agrarian
        "plough" -> "wooden ploughs, draft animals, long furrowed fields and repaired plough parts beside farmsteads"
        "irrigation" -> "irrigation canals, sluice gates, ditches, water-lifting work and visibly wetter cultivated fields"
        "grain_farming" -> "grain fields, sheaves, threshing floors, querns, sacks and large storage jars dominating village work"
        "pastoralism" -> "managed herds, corrals, milk vessels, leather-working gear and seasonal grazing movement"
        "land_tenure" -> "marked field boundaries, boundary stones, fenced plots, measured strips and visibly unequal farmsteads"
        "seasonal_fairs" -> "temporary market rows, awnings, pack animals, scales, craft booths and crowds exchanging goods"
        "village_network" -> "worn dirt roads linking villages, carts, way markers, small bridges and regular traffic between settlements"
        "frontier_farms" -> "new farmsteads at the settlement edge, freshly cleared fields, rough fences, carts and unfinished houses"

        // Urban
        "writing" -> "scribes at work, tablets or manuscripts, seal impressions, account boards and visible record keeping"
        "sewers" -> "stone drains, covered channels, inspection openings, water conduits and planned street sanitation"
        "urban_markets" -> "dense food markets, warehouse doors, porters, scales, baskets and specialised stalls feeding a large city"
        "state_granaries" -> "large guarded granaries, measured grain stores, sacks, tallying clerks and organised distribution queues"
        "standing_guard" -> "uniformed city guards, watch posts, controlled gates, patrol routes and weapon racks"
        "merchant_guilds" -> "guild emblems, ledgers, scales, specialised market streets, guarded storehouses and prosperous merchant houses"
        "paved_roads" -> "broad paved roads, curbs, carts, messengers, milestones and organised traffic entering the city"
        "colonies" -> "distant-looking outpost architecture, supply depots, docks or roads, survey markers and settlers building a new centre"

        // Metallurgic
        "iron_tools" -> "iron axes, chisels, plough parts, tongs and blacksmith tools visibly replacing stone or bronze implements"
        "metal_weapons" -> "standardised metal weapons, shields, armour repair racks and organised armed retainers"
        "iron_plough" -> "heavy iron ploughshares cutting deep soil, blacksmith repair stations and larger cultivated fields"
        "mine_economy" -> "mine entrances, ore carts, timber supports, sorting yards, slag heaps and settlements organised around extraction"
        "coinage" -> "coins, balance scales, stamped weights, money changing and market accounting visible in everyday trade"
        "warrior_elite" -> "distinct warrior elite with superior armour, decorated weapons, attendants and status equipment"
        "cavalry" -> "horse tack, remount pens, mounted couriers, cavalry weapons and training grounds"
        "trade_caravans" -> "pack animals, guarded caravans, foreign goods, caravan yards and long-distance road traffic"

        // Medieval / estate society
        "watermills" -> "water wheels, mill races, gearing, grain sacks and flour work concentrated around rivers"
        "manuscript_schools" -> "scriptoria or schools with manuscripts, ink, desks, copied books and trained scribes"
        "crop_rotation" -> "organised strip fields with visibly different crops, fallow sections and planned seasonal rotation"
        "lordly_estates" -> "large manor compounds, tenant fields, barns, steward activity and a clear visual hierarchy of landholding"
        "fortifications" -> "stone walls, towers, gatehouses, ditches and defensive works shaping the settlement"
        "guild_law" -> "guild halls, workshop emblems, regulated craft streets, posted measures and inspected goods"
        "pilgrim_roads" -> "busy pilgrimage roads, roadside shelters, shrines, way markers and mixed groups of travellers"
        "frontier_castles" -> "border castles, watch towers, fortified villages, patrol roads and newly settled defensive land"

        // Early industrial
        "steam_power" -> "steam engines, boilers, pistons, drive belts, coal piles and constant white steam around workshops"
        "mechanized_looms" -> "rows of mechanical looms, textile machinery, spindles, belts and dense mill interiors"
        "enclosures" -> "hedged or fenced consolidated fields, straight property lines, displaced cottages and larger commercial farms"
        "urban_food_chain" -> "wholesale food depots, carts, slaughter and produce yards, warehouses and scheduled city deliveries"
        "factory_discipline" -> "factory gates, clocks, shift lines, foremen, numbered work stations and tightly ordered production floors"
        "public_clinics" -> "early public clinics, waiting benches, medical instruments, washing stations and organised treatment rooms"
        "canals" -> "industrial canals, locks, towpaths, barges, warehouses and waterside factories"
        "turnpikes" -> "engineered toll roads, gates, mile markers, stage traffic and maintained road surfaces"

        // Industrial
        "railways" -> "steam railways, iron tracks, stations, signal equipment, freight yards and coal smoke"
        "steel" -> "steel beams, rolling mills, furnaces, cranes and heavy machine tools"
        "industrial_agriculture" -> "large mechanised farms, reapers, threshers, storage silos and organised agricultural labour"
        "processed_food" -> "food factories, canning lines, labelled crates, industrial bakeries and mass-packed provisions"
        "mass_schooling" -> "large public classrooms, rows of desks, textbooks, blackboards and mass literacy"
        "sanitation" -> "water mains, sewer works, pumps, public wash facilities and visibly cleaner dense streets"
        "mass_migration" -> "crowded stations or docks, luggage piles, migrant families, cheap lodging and rapid urban expansion"
        "global_shipping" -> "large ports, cranes, warehouses, steamships, cargo stacks and long-distance commercial traffic"

        // Electric
        "power_grid" -> "electric lamps, poles, wires, transformers, motors and streets visibly reorganised around reliable electricity"
        "radio" -> "radio sets, antennae, microphones, broadcast rooms and crowds listening to shared transmissions"
        "chemical_farming" -> "fertiliser sacks, sprayers, laboratory-labelled farm inputs and high-yield uniform fields"
        "cold_chain" -> "refrigerated warehouses, insulated trucks or rail cars, ice plants and organised perishable-food logistics"
        "broadcast_society" -> "radios, posters, news stands, public loudspeakers and shared mass-media moments in homes and streets"
        "public_health" -> "organised hospitals, vaccination lines, public health workers, ambulances and clinical equipment"
        "motorization" -> "cars and trucks, fuel pumps, repair garages, wider roads and cities adapting to engines"
        "electric_transit" -> "trams or electric trains, overhead wires, stations and dense commuter movement"

        // Information
        "computing" -> "computers, terminals, server racks, screens and digital workstations integrated into ordinary workplaces"
        "biotech" -> "biotechnology laboratories, sterile benches, culture vessels, diagnostic equipment and engineered biological materials"
        "precision_farming" -> "sensor-guided farming machinery, field monitors, drones and tightly managed cultivated land"
        "synthetic_food" -> "food bioreactors, controlled production tanks, nutrient processing lines and compact urban food facilities"
        "open_networks" -> "ubiquitous connected devices, public network terminals, collaborative workspaces and visible peer-to-peer communication"
        "algorithmic_governance" -> "data-rich civic control rooms, sensor dashboards, automated public infrastructure and machine-assisted administration"
        "autonomous_transport" -> "driverless vehicles, sensor beacons, automated freight systems and logistics hubs with minimal human driving"
        "remote_life" -> "homes and small local spaces functioning as workplaces and classrooms, telepresence screens and reduced commuter traffic"

        // Spacefaring
        "fusion" -> "fusion reactors, superconducting power systems, high-capacity energy conduits and luminous but believable reactor architecture"
        "asteroid_mining" -> "orbital mining machinery, pressure suits, ore containers, robotic cutters and asteroid material handling"
        "closed_ecologies" -> "sealed habitat farms, algae or crop bays, recycled water loops, air processing and carefully balanced ecological machinery"
        "engineered_food" -> "controlled growth chambers, engineered crops, nutrient fabrication systems and food designed for off-world habitats"
        "distributed_governance" -> "multiple colony council spaces, local civic terminals and distinct administrative centres linked across distance"
        "ai_coordination" -> "pervasive AI coordination interfaces, autonomous maintenance systems and machine-managed logistics embedded in daily infrastructure"
        "orbital_habitats" -> "large orbital habitats, pressure architecture, rotating living sections, docking spines and artificial-gravity public spaces"
        "interplanetary_routes" -> "interplanetary transports, docking infrastructure, cargo transfer frames, route boards and off-world freight"

        else -> "visible practical material legacy of ${humanize(slug)} in tools, infrastructure, work and settlement form"
    }

    private fun dilemmaOrDecisionCue(id: String): String {
        val normalized = id.lowercase()
        return when {
            normalized.contains("lean-winter") && normalized.endsWith("-ration") ->
                "carefully measured food portions, guarded winter stores and families receiving equal rations around a cold camp"
            normalized.contains("lean-winter") && normalized.endsWith("-hunt") ->
                "a heavily equipped winter hunting party leaving camp with spears, sledges and empty meat racks waiting behind"
            normalized.contains("lean-winter") && (normalized.endsWith("-craft") || normalized.endsWith("-new-tools")) ->
                "winter workshops producing tools, repaired clothing and storage gear beside a strained food reserve"
            normalized.endsWith("-council") -> "a formal council circle with elders or delegates debating around clearly arranged seats and symbols of authority"
            normalized.endsWith("-feast") || normalized.endsWith("-festival") || normalized.endsWith("-procession") ->
                "a large public gathering with shared food, ceremonial movement, banners or ritual objects visibly used to restore cohesion"
            normalized.endsWith("-granary") || normalized.endsWith("-release") || normalized.endsWith("-ration") ->
                "opened public food stores, measured sacks or baskets and organised distribution to waiting households"
            normalized.endsWith("-irrigate") -> "newly dug canals, survey stakes, workers shaping water channels and unfinished hydraulic works"
            normalized.endsWith("-survey") -> "survey ropes, boundary markers, measured plots and officials or elders recording land divisions"
            normalized.endsWith("-frontier") -> "settlers, carts, rough new buildings and freshly cleared land at the edge of controlled territory"
            normalized.endsWith("-fair") -> "a crowded exchange fair with stalls, scales, pack animals and competing households trading goods"
            normalized.endsWith("-order") -> "organised guards or officials controlling a tense public space with visible queues and checkpoints"
            normalized.endsWith("-records") -> "clerks, ledgers or tablets tracing supplies through warehouses and market stalls"
            normalized.endsWith("-relief") -> "emergency food, temporary shelters, organised aid lines and workers repairing damaged homes"
            normalized.endsWith("-rebuild") -> "surveyed reconstruction, wider streets, new structural rules and crews rebuilding damaged blocks"
            normalized.endsWith("-fields") -> "metal tools and scarce resources visibly redirected toward farms, ploughs and agricultural work"
            normalized.endsWith("-furnace") -> "new furnace construction, improved bellows, ore testing and smiths experimenting with hotter metalwork"
            normalized.endsWith("-guild") -> "recognised master craftsmen gathered around a guild hall, marked workshops and controlled material distribution"
            normalized.endsWith("-honor") -> "decorated veteran warriors receiving land, insignia or formal rewards in a public ceremony"
            normalized.endsWith("-works") -> "organised labour crews turning military or state manpower toward roads, walls or major public construction"
            normalized.contains("reserve") || normalized.contains("feed") -> "expanded stores, granaries and visible stockpiles prepared against future scarcity"
            normalized.contains("craft") || normalized.contains("tech") || normalized.contains("reform") ->
                "new workshops, prototypes, measuring tools and organised experimentation visibly changing ordinary work"
            normalized.contains("order") || normalized.contains("stability") || normalized.contains("legitim") ->
                "formal guards, public administration, orderly queues and visible institutions reinforcing authority"
            normalized.contains("peace") || normalized.contains("embassy") || normalized.contains("alliance") ->
                "delegations, exchanged gifts, sealed agreements and a public diplomatic meeting between distinct groups"
            normalized.contains("war") || normalized.contains("raid") ->
                "mobilised fighters, supply carts, defensive preparation and a settlement visibly reorganised for conflict"
            else -> "the concrete practical consequence of the chosen policy '${humanize(id)}' visibly changing work, tools, infrastructure or public space"
        }
    }

    private fun humanize(value: String): String = value.replace('_', ' ').replace('-', ' ').replace('+', ' ')

    private val LEGACY_ALIASES = mapOf(
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

    private val KNOWN_SLUGS = setOf(
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
