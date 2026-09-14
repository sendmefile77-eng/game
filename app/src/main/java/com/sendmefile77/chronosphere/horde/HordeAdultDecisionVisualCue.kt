package com.sendmefile77.chronosphere.horde

/**
 * Adult/erotic visual language for the same historical slugs used by
 * [HordeDecisionVisualCue]. Material tools, work and architecture stay in the
 * non-adult layer. This object only describes how bodies, intimacy, rank and
 * sexual custom inhabit that already-prepared world.
 */
internal object HordeAdultDecisionVisualCue {
    fun forChoiceId(choiceId: String?): String {
        val id = choiceId?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
        if (id.startsWith("era-")) {
            val slug = KNOWN_SLUGS.firstOrNull { candidate -> id.endsWith("-$candidate") }
            if (slug != null) return forSlug(slug)
        }
        return dilemmaCue(id)
    }

    fun forHistoricalTag(prefix: String, rawValue: String): String {
        val value = rawValue.trim()
        val slug = HordeHistoricalTagEra.canonical(value)
        return if (slug in KNOWN_SLUGS) {
            forSlug(slug)
        } else {
            when (prefix) {
                "foundation:" ->
                    "intimate custom grown from the established foundation of ${humanize(value)}, " +
                        "bodies using the same tools and rooms as daily work"
                "policy:" ->
                    "sexual custom organised by the living policy of ${humanize(value)}, " +
                        "rank and access to partners visible in who may undress where"
                "hist:" ->
                    "worn erotic memory of ${humanize(value)} still shaping how adults touch, " +
                        "display skin and take partners"
                else -> "intimate trace of ${humanize(value)} kept inside the existing settlement"
            }
        }
    }

    fun forSlug(slug: String): String = when (slug) {
        "fire" -> "sex on stacked hides beside the communal hearth, sweat and woodsmoke on bare skin, bodies sharing warmth after the fire is banked"
        "stone_tools" -> "flint knives and scrapers left at arm's reach, sex on scraped hides, calloused hunter-gatherer hands on bare hips"
        "predator_hunters" -> "post-hunt coupling among drying meat and heavy spears, muscular bodies, smears of ochre or game-blood on thighs, sex as recovery after the kill"
        "plant_foragers" -> "slow close sex among gathering baskets and drying herbs, plant-stained fingers, intimacy during the pause in foraging"
        "river_fishers" -> "wet-skinned sex on the riverbank beside nets and drying fish, water beading on breasts and genitals, silt on knees"
        "animal_taming" -> "sex in the animal pen margin of camp, hides and leashes nearby, bodies marked by daily work with animals, not a studio nude"
        "ritual_culture" -> "ritual copulation before painted posts, ochre on breasts, belly and genitals, carved charms hanging above the joining, sex treated as ceremony"
        "nomadic_migration" -> "hurried intimate coupling inside a portable hide shelter, packed gear around the furs, bodies that must move again at dawn"
        "permanent_camp" -> "habitual camp sex in a fixed hut, stored tools around the pallet, neighbours close enough to hear, no private bedroom"
        "plough" -> "fertility sex after field work, soil on calves, a plough leaning in the doorway, the household bed as the place where the landholding line continues"
        "irrigation" -> "damp-skinned coupling beside a canal or wet field edge, wet linen pulled aside, irrigation mud on knees and buttocks"
        "grain_farming" -> "harvest-yard sex against a granary post or on grain sacks, dusty sunlight, swollen work bodies, fertility as household business"
        "pastoralism" -> "herder intimacy in a corral-side shelter, leather and milk vessels nearby, seasonal bodies, sex between moves of the flock"
        "land_tenure" -> "ranked household sex: the landholder's pallet versus field-hand coupling outdoors, status visible in who gets the indoor bed"
        "seasonal_fairs" -> "fair-night liaisons behind awnings, borrowed blankets, strangers and neighbours mixing, sex as part of the seasonal crowd"
        "village_network" -> "road-side or cart-yard trysts between villages, travel-stained bodies, intimacy that follows the dirt road network"
        "frontier_farms" -> "rough new-house sex on unfinished timber, a half-built frontier bed, bodies founding a household at the settlement edge"
        "writing" -> "scribe-quarter liaisons among tablets and seals, ink-stained fingers on skin, private rooms rented above the record house"
        "sewers" -> "city sex that knows drains and back lanes, damp masonry, intimacy using the planned city's hidden channels"
        "urban_markets" -> "market-stall coupling under an awning after trade, baskets and scales still out, public work continuing around undressed adults"
        "state_granaries" -> "sex in the shadow of guarded grain stores, clerks and queues nearby, bodies whose access to food shapes who may be taken as a partner"
        "standing_guard" -> "watch-post or gatehouse trysts, armor and weapon racks beside bare skin, guard privilege visible in who is taken off the street"
        "merchant_guilds" -> "guild-house patronage sex, ledgers and strongboxes in the same room, wealth buying time, wine and an upstairs pallet"
        "paved_roads" -> "road-inn and milestone liaisons, travel-dust on skin, bodies arriving with the organised traffic"
        "colonies" -> "outpost founding sex in a new depot or dock shed, mixed origin bodies, intimacy that plants a household in a colony"
        "iron_tools" -> "workshop sex after smithing, iron tools still warm on the bench, soot and sweat, calloused metalworker's hands"
        "metal_weapons" -> "armory undress, standardised weapons stacked beside the pallet, sex among retainers after inspection of blades"
        "iron_plough" -> "deep-field fertility coupling, heavier farm bodies, an iron ploughshare visible through the doorway"
        "mine_economy" -> "miners' coupling in a timber-supported lodging, ore dust on skin, exhausted bodies, settlement organised around the pit"
        "coinage" -> "paid or gift-marked intimacy, coins and scales on the same table as discarded cloth, sex that can be accounted"
        "warrior_elite" -> "elite warrior undress, superior armour piled, attendants outside, dominance sex that displays rank on the body"
        "cavalry" -> "stable-side or tack-room coupling, horse sweat and leather, rider's thighs, sex after the remount"
        "trade_caravans" -> "caravan-yard night sex among packs and foreign cloth, travelers taking partners before the next stage"
        "watermills" -> "miller's loft sex above the turning wheel, flour on skin, the mill race audible through the floor"
        "manuscript_schools" -> "cloistered or scriptorium-adjacent intimacy, ink, desks and copied books nearby, learned bodies using a side chamber"
        "crop_rotation" -> "season-timed household sex, different crops visible through the shutter, fertility kept to the estate calendar"
        "lordly_estates" -> "solar-chamber coupling on the lord's bed, tenant world visible through the window, rank in who may enter the room undressed"
        "fortifications" -> "wall-walk or gatehouse sex, stone, torchlight, a drop beyond the parapet, bodies using the fortress as cover"
        "guild_law" -> "regulated craft-street liaisons, workshop emblems, inspected rooms, sex allowed only where the guild permits privacy"
        "pilgrim_roads" -> "wayside-shrine or hostel coupling, mixed travellers, dusty cloaks dropped, sex as a night on the pilgrim road"
        "frontier_castles" -> "border-keep bedding, patrol gear on the chest, harsh frontier bodies founding a household behind new walls"
        "steam_power" -> "after-shift sex in a boiler-side lodging, steam, coal dust and sweat, bodies shaking off the engine day"
        "mechanized_looms" -> "mill-hand coupling in a loft above the looms, lint on skin, shift-sore shoulders, cramped rented privacy"
        "enclosures" -> "displaced-cottage sex on the edge of consolidated fields, a lost commons outside, bodies squeezed into smaller rooms"
        "urban_food_chain" -> "depot and lodging-house trysts after the food carts leave, warehouse privacy, city hunger shaping who stays the night"
        "factory_discipline" -> "clock-watched coupling after the whistle, numbered lodging, sex stolen between shifts against brick"
        "public_clinics" -> "clinic-adjacent rented rooms, washed bodies, medical-age frankness, intimacy that knows public hygiene"
        "canals" -> "barge-cabin or towpath-warehouse sex, damp wood, canal smell, bodies living on the water industrial belt"
        "turnpikes" -> "toll-road inn rooms, stage-dusty travellers, a purchased night between engineered miles"
        "railways" -> "railway-hotel or freight-yard coupling, coal smoke, iron tracks outside, bodies arriving and leaving with the train"
        "steel" -> "steel-town tenement sex, furnace glow on wet skin, heavy industrial bodies, peeling wallpaper"
        "industrial_agriculture" -> "mechanised-farm bunkhouse intimacy, harvest machinery outside, seasonal labourer sex after the field day"
        "processed_food" -> "cannery-shift rooms, labelled crates stacked, factory-food age bodies using a rented bed"
        "mass_schooling" -> "cheap urban rooms of newly literate adults, textbooks on a chair, private sex as a right the classroom generation claims"
        "sanitation" -> "tenement intimacy in a city that has water mains, washed skin, closer crowds but cleaner rooms"
        "mass_migration" -> "crowded migrant lodging sex, luggage piles, thin partitions, bodies far from origin households"
        "global_shipping" -> "port-room and dock-warehouse liaisons, foreign goods stacked, sailors' and stevedores' short-stay sex"
        "power_grid" -> "wired-room sex under a ceiling bulb, motors humming outside, electric light making bodies fully visible"
        "radio" -> "apartment coupling with a radio still playing, antennae on the sill, shared broadcast in the same room as the act"
        "chemical_farming" -> "modernised farmhouse sex, labelled inputs in the porch, high-yield fields outside the window"
        "cold_chain" -> "rooms behind refrigerated warehouses, night-shift intimacy, cold air leaking from the loading dock"
        "broadcast_society" -> "sex that knows it can be talked about, posters and a radio cabinet, bodies performing a little for an imagined public"
        "public_health" -> "clinic-age frank nudity, washed linen, contraceptive or wash-stand props, bodies treated as medical facts as well as lovers"
        "motorization" -> "garage-adjacent or roadside-lodge sex, fuel smell, a parked car outside, bodies using the new mobility for trysts"
        "electric_transit" -> "rooms over a tram line, overhead wires in the window, commuter adults taking a short private hour"
        "computing" -> "sex in a room of terminals and server hum, screens dimmed but present, bodies that work digitally and undress in the same space"
        "biotech" -> "lab-adjacent apartment intimacy, sterile sheen, engineered materials, bodies discussed in biological language"
        "precision_farming" -> "sensor-farm dwelling sex, monitors glowing, drones docked outside, rural privacy under data collection"
        "synthetic_food" -> "compact urban-food-facility housing, tanks elsewhere in the building, bodies no longer tied to harvest seasons"
        "open_networks" -> "networked intimacy, devices face-down but connected, sex that can be shared, streamed or merely overheard by the net"
        "algorithmic_governance" -> "surveilled-apartment coupling, civic dashboards elsewhere in the building, bodies aware a system may log presence"
        "autonomous_transport" -> "rooms beside automated freight, no driver coming home, sex on a schedule set by machines as much as people"
        "remote_life" -> "home-office bed used in daylight, telepresence screens off, domestic sex replacing the commuter affair"
        "fusion" -> "habitat coupling near a power spine, soft reactor hum, bodies lit by believable high-energy architecture"
        "asteroid_mining" -> "pressure-suit dropped on the cabin floor, ore-dusted miners having sex in a cramped lock-adjacent bunk"
        "closed_ecologies" -> "sex in a garden-bay bunk, recycled air, damp planting trays nearby, bodies living inside a sealed ecology"
        "engineered_food" -> "growth-chamber housing intimacy, nutrient-clean skin, off-world diet showing in lean habitat bodies"
        "distributed_governance" -> "colony-council members using a private module, civic terminals outside, intimacy negotiated across distant habitats"
        "ai_coordination" -> "sex in a cabin whose lights and climate adjust themselves, machine-managed privacy, human bodies still doing the act"
        "orbital_habitats" -> "webbing or bunk sex in a rotating habitat cabin, viewport curvature, artificial gravity, neighbours one bulkhead away"
        "interplanetary_routes" -> "transit-cabin coupling between worlds, docking spine outside the porthole, short-term partners on a long route"
        else -> "intimate custom shaped by ${humanize(slug)}, using the settlement's real rooms, tools and work rather than an empty studio"
    }

    private fun dilemmaCue(id: String): String {
        val normalized = id.lowercase()
        return when {
            normalized.contains("lean-winter") && normalized.endsWith("-ration") ->
                "scarce-winter sex after measured rations, bodies thinner, intimacy as shared heat in a guarded store-camp"
            normalized.contains("lean-winter") && normalized.endsWith("-hunt") ->
                "pre-hunt or returned-hunt coupling, cold air, spears stacked, sex before the winter party leaves or after meat comes back"
            normalized.contains("lean-winter") && (normalized.endsWith("-craft") || normalized.endsWith("-new-tools")) ->
                "workshop-winter intimacy among repaired clothing and tools, hands busy all day, sex as the only idle hour"
            normalized.endsWith("-council") ->
                "after-council liaison of adults who sat in the circle, authority still in the room, sex used to bind a decision"
            normalized.endsWith("-feast") || normalized.endsWith("-festival") || normalized.endsWith("-procession") ->
                "festival sex in or just off the gathering, shared food nearby, bodies using the public rite as cover and invitation"
            normalized.endsWith("-granary") || normalized.endsWith("-release") || normalized.endsWith("-ration") ->
                "intimacy after public food is released, relief in the body, sex among sacks and the people who waited"
            normalized.endsWith("-honor") ->
                "reward-night coupling of a decorated veteran, insignia still on the chest-piece, sex as a public honour that continues in a private room"
            normalized.contains("war") || normalized.contains("raid") ->
                "homecoming or pre-raid sex, weapons in reach, bodies using the last private hour before or after violence"
            normalized.contains("peace") || normalized.contains("embassy") || normalized.contains("alliance") ->
                "diplomatic-night intimacy after gifts and seals, distinct groups sharing a bed as part of the settlement"
            else ->
                "adult intimacy taking place inside the practical consequence of '${humanize(id)}', not in a generic bedroom"
        }
    }

    private fun humanize(value: String): String =
        value.replace('_', ' ').replace('-', ' ').replace('+', ' ')

    internal val KNOWN_SLUGS = setOf(
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
