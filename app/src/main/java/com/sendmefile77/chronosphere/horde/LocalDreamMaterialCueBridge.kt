package com.sendmefile77.chronosphere.horde

/**
 * Preserves a few concrete, non-adult historical details when Illustrious compresses a long Horde
 * prompt. The adult/erotic prompt branches deliberately do not call this bridge.
 */
internal object LocalDreamMaterialCueBridge {
    fun fragment(sourcePrompt: String): String {
        val source = sourcePrompt.lowercase()
        return CUES.asSequence()
            .filter { (needle, _) -> source.contains(needle) }
            .map { (_, cue) -> cue }
            .distinct()
            .take(MAX_CUES)
            .joinToString(", ")
    }

    private const val MAX_CUES = 5

    private val CUES = listOf(
        // Space / information first so modern choices win when old history is also present.
        "orbital habitats" to "orbital habitat, pressure architecture, docking spine",
        "asteroid material" to "asteroid mining machinery, ore containers, pressure suits",
        "fusion reactors" to "fusion reactor infrastructure, high-capacity energy conduits",
        "sealed habitat farms" to "sealed habitat farm, recycled water loop, ecological machinery",
        "interplanetary transports" to "interplanetary transports, docking infrastructure, cargo transfer",
        "civic control rooms" to "data-rich civic control room, automated public infrastructure",
        "telepresence" to "telepresence screens, home workstations, low commuter traffic",
        "connected devices" to "ubiquitous connected devices, public network terminals",
        "driverless vehicles" to "driverless vehicles, sensor beacons, automated logistics",
        "server racks" to "computers, server racks, digital workstations",
        "biotechnology laboratories" to "biotechnology laboratory, sterile benches, engineered materials",
        "sensor-guided" to "sensor-guided farm machinery, field monitors, drones",
        "food bioreactors" to "food bioreactors, controlled production tanks",

        // Electric / industrial.
        "electric lamps" to "electric lamps, overhead wires, motors, transformers",
        "radio sets" to "radio sets, antennae, broadcast equipment",
        "refrigerated warehouses" to "refrigerated warehouses, cold-chain transport",
        "public health workers" to "public health workers, organised clinic equipment",
        "cars and trucks" to "cars, trucks, fuel pumps, repair garages",
        "steam railways" to "steam railway, iron tracks, freight yard, coal smoke",
        "steel beams" to "steel beams, furnaces, cranes, heavy machine tools",
        "mechanical looms" to "mechanical looms, belts, spindles, textile mill",
        "steam engines" to "steam engine, boiler, pistons, coal piles",
        "factory gates" to "factory gates, shift clock, ordered production floor",
        "industrial canals" to "industrial canal, lock, barge, waterside warehouse",

        // Medieval / metallurgic.
        "stone walls" to "stone walls, towers, defensive gatehouse",
        "water wheels" to "water wheel, mill race, grain mill machinery",
        "scriptoria" to "scriptorium, manuscripts, ink, copying desks",
        "guild halls" to "guild hall, workshop emblems, regulated craft street",
        "mine entrances" to "mine entrance, ore carts, timber supports, slag heap",
        "iron axes" to "iron axes, chisels, blacksmith tools",
        "standardised metal weapons" to "metal weapons, shields, armour racks",
        "horse tack" to "horse tack, mounted couriers, cavalry gear",
        "coins" to "coins, balance scales, stamped weights, market accounting",

        // Urban / agrarian.
        "scribes at work" to "scribes, tablets, seal impressions, record keeping",
        "stone drains" to "stone drains, covered channels, planned sanitation",
        "guarded granaries" to "guarded granaries, measured grain stores, distribution queue",
        "city guards" to "city guards, watch posts, controlled gates",
        "guild emblems" to "merchant guild emblems, ledgers, scales, market stalls",
        "paved roads" to "broad paved road, carts, messengers, milestones",
        "irrigation canals" to "irrigation canals, sluice gates, wet cultivated fields",
        "furrowed fields" to "furrowed fields, wooden plough, draft animals",
        "threshing floors" to "grain sheaves, threshing floor, storage jars",
        "managed herds" to "managed herds, corrals, milk vessels",
        "boundary stones" to "boundary stones, measured fields, fenced plots",
        "market rows" to "market rows, awnings, pack animals, scales",
        "dirt roads" to "dirt roads linking villages, carts, small bridges",

        // Tribal.
        "flint knives" to "flint knives, stone axes, knapping debris",
        "big-game hunting camp" to "big-game hunting camp, heavy spears, drying meat",
        "gathering baskets" to "woven baskets, roots, berries, dried herbs",
        "riverbank camp" to "riverbank camp, harpoons, fish traps, nets",
        "tamed dogs" to "tamed dogs, animal pens, daily animal work",
        "ritual posts" to "painted ritual posts, ochre markings, carved ceremonial objects",
        "portable hide shelters" to "portable hide shelters, bundled possessions, travel gear",
        "fixed huts" to "fixed huts, storage pits, permanent hearths",
        "central hearth fires" to "central hearth fire, smoke-darkened shelter, stored fuel",

        // Century dilemma/action cues.
        "winter hunting party" to "winter hunting party, spears, sledges, empty meat racks",
        "measured food portions" to "measured food portions, guarded stores, ration queue",
        "formal council circle" to "formal council circle, arranged seats, authority symbols",
        "emergency food" to "emergency food distribution, temporary shelters, repair crews",
        "surveyed reconstruction" to "surveyed reconstruction, wider streets, rebuilding crews",
        "new furnace construction" to "new furnace construction, improved bellows, ore testing",
        "public diplomatic meeting" to "delegations, exchanged gifts, sealed agreement",
        "mobilised fighters" to "mobilised fighters, supply carts, defensive preparation",
    )
}
