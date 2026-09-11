package com.sendmefile77.chronosphere.civilization

import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.ResourceDeposit
import com.sendmefile77.chronosphere.worldgen.ResourceKind
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldTile
import kotlin.math.abs
import kotlin.math.roundToLong

class CivilizationEngine(
    private val world: WorldMap,
    resources: List<ResourceDeposit>,
) {
    private val resourcesByTile = resources.groupBy { it.x to it.y }

    fun initialize(civilizationCount: Int = 8): LivingPlanetState {
        require(civilizationCount in 1..24)
        val candidates = world.tiles.asSequence().filter { isHabitable(it) }.sortedByDescending { settlementScore(it) }.toList()
        val selected = ArrayList<WorldTile>()
        for (tile in candidates) {
            if (selected.all { distance(it, tile) >= 8 }) selected += tile
            if (selected.size == civilizationCount) break
        }
        require(selected.isNotEmpty()) { "World contains no habitable settlement sites" }

        val civilizations = ArrayList<Civilization>()
        val settlements = ArrayList<Settlement>()
        val events = ArrayList<SimulationEvent>()
        selected.forEachIndexed { index, tile ->
            val civId = "civ-${index + 1}"
            val settlementId = "settlement-${index + 1}"
            val civName = civilizationName(index)
            val cityName = settlementName(index)
            val population = 520L + (hash01(index.toLong() + world.seed.value, tile.x, tile.y) * 620.0).roundToLong()
            civilizations += Civilization(
                id = civId,
                name = civName,
                population = population,
                stability = 0.64 + hash01(world.seed.value + 17, tile.x, tile.y) * 0.22,
                technology = 0.03,
                treasury = 80.0,
                cultureTags = initialCulture(tile),
            )
            settlements += Settlement(settlementId, cityName, civId, tile.x, tile.y, population, population * 0.65, 60.0, 0)
            events += SimulationEvent(
                id = "founding-$index", tick = 0, code = "SETTLEMENT_FOUNDED",
                actorIds = listOf(civId), locationId = settlementId,
                numbers = mapOf("population" to population.toDouble()),
                facts = mapOf("civilization" to civName, "settlement" to cityName),
            )
        }

        return LivingPlanetState(
            worldSeed = world.seed.value,
            tick = 0L,
            civilizations = civilizations,
            settlements = settlements,
            recentEvents = events.takeLast(32),
            relations = initialRelations(civilizations),
        )
    }

    fun advance(state: LivingPlanetState, months: Int): LivingPlanetState {
        require(months in 1..12_000)
        var current = normalizeDiplomacy(state)
        repeat(months) { current = step(current) }
        return current
    }

    private fun normalizeDiplomacy(state: LivingPlanetState): LivingPlanetState =
        if (state.relations.isEmpty() && state.civilizations.size > 1) state.copy(relations = initialRelations(state.civilizations)) else state

    private fun step(state: LivingPlanetState): LivingPlanetState {
        val nextTick = state.tick + 1
        val events = ArrayList<SimulationEvent>()
        var settlements = growSettlements(state.settlements, nextTick, events).toMutableList()
        if (nextTick % 240L == 0L) foundColonies(settlements, nextTick, events)
        if (nextTick % 12L == 0L) migratePopulation(settlements, nextTick, events)

        val diplomacy = updateDiplomacy(state, nextTick, events)
        val warResult = applyWarEffects(settlements, diplomacy.wars, state.civilizations, nextTick, events)
        settlements = warResult.settlements.toMutableList()
        val settlementResult = settleWarsAndAlliances(
            diplomacy.relations,
            warResult.wars,
            diplomacy.alliances,
            state.civilizations,
            nextTick,
            events,
        )

        val populations = settlements.groupBy { it.civilizationId }.mapValues { (_, list) -> list.sumOf { it.population } }
        val wealth = settlements.groupBy { it.civilizationId }.mapValues { (_, list) -> list.sumOf { it.wealth } }
        val updatedCivilizations = state.civilizations.map { civ ->
            val pop = populations[civ.id] ?: 0L
            civ.copy(
                population = pop,
                treasury = (civ.treasury * 0.998 + (wealth[civ.id] ?: 0.0) * 0.002).coerceAtLeast(0.0),
                technology = (civ.technology + 0.0000025 * (1.0 + pop / 10_000.0)).coerceAtMost(1.0),
                stability = (civ.stability + stabilityDrift(civ.id, nextTick) - warResult.stabilityPenalty(civ.id)).coerceIn(0.15, 0.95),
            )
        }

        return state.copy(
            tick = nextTick,
            civilizations = updatedCivilizations,
            settlements = settlements,
            recentEvents = (state.recentEvents + events).takeLast(96),
            relations = settlementResult.relations,
            wars = settlementResult.wars,
            alliances = settlementResult.alliances,
        )
    }

    private fun growSettlements(settlements: List<Settlement>, nextTick: Long, events: MutableList<SimulationEvent>): List<Settlement> = settlements.map { settlement ->
        val tile = world.tiles[settlement.y * world.width + settlement.x]
        val resourceBonus = resourceBonus(settlement.x, settlement.y)
        val habitability = habitability(tile)
        val carryingPressure = (settlement.population / 45_000.0).coerceIn(0.0, 0.85)
        val foodProduction = settlement.population * (0.018 + habitability * 0.013 + resourceBonus * 0.004)
        val foodUse = settlement.population * 0.023
        val nextFood = (settlement.foodStock + foodProduction - foodUse).coerceAtLeast(0.0)
        val hungerPenalty = if (nextFood < settlement.population * 0.08) -0.0032 else 0.0
        val monthlyGrowth = (0.0011 + (habitability - 0.5) * 0.0013 + resourceBonus * 0.00035 - carryingPressure * 0.0018 + hungerPenalty)
            .coerceIn(-0.0045, 0.0035)
        val nextPopulation = (settlement.population * (1.0 + monthlyGrowth)).roundToLong().coerceAtLeast(40L)
        val nextWealth = (settlement.wealth + nextPopulation * (0.0008 + resourceBonus * 0.0006)).coerceAtLeast(0.0)
        if (populationBand(nextPopulation) > populationBand(settlement.population)) {
            events += SimulationEvent(
                id = "growth-${settlement.id}-$nextTick", tick = nextTick, code = "SETTLEMENT_GROWTH",
                actorIds = listOf(settlement.civilizationId), locationId = settlement.id,
                numbers = mapOf("population" to nextPopulation.toDouble()), facts = mapOf("settlement" to settlement.name),
            )
        }
        if (hungerPenalty < 0.0 && nextTick % 12L == 0L) {
            events += SimulationEvent(
                id = "shortage-${settlement.id}-$nextTick", tick = nextTick, code = "FOOD_SHORTAGE",
                actorIds = listOf(settlement.civilizationId), locationId = settlement.id,
                numbers = mapOf("population" to nextPopulation.toDouble()), facts = mapOf("settlement" to settlement.name),
            )
        }
        settlement.copy(population = nextPopulation, foodStock = nextFood, wealth = nextWealth)
    }

    private data class DiplomacyResult(
        val relations: List<DiplomaticRelation>,
        val wars: List<WarState>,
        val alliances: List<AllianceState>,
    )

    private fun updateDiplomacy(state: LivingPlanetState, tick: Long, events: MutableList<SimulationEvent>): DiplomacyResult {
        if (tick % 12L != 0L) return DiplomacyResult(state.relations, state.wars, state.alliances)
        val names = state.civilizations.associate { it.id to it.name }
        val wars = state.wars.toMutableList()
        val alliances = state.alliances.toMutableList()
        val relations = state.relations.map { relation ->
            val atWar = wars.any { it.matches(relation.civilizationA, relation.civilizationB) }
            val allied = alliances.any { it.matches(relation.civilizationA, relation.civilizationB) }
            val noise = (hash01(world.seed.value xor tick, relation.civilizationA.hashCode(), relation.civilizationB.hashCode()) - 0.5) * 0.045
            val pull = when {
                atWar -> -0.010
                allied -> 0.012
                else -> -relation.value * 0.006
            }
            relation.copy(value = (relation.value + noise + pull).coerceIn(-1.0, 1.0), lastUpdatedTick = tick)
        }.toMutableList()

        for (relation in relations) {
            if (relation.value > -0.58) continue
            if (wars.any { it.matches(relation.civilizationA, relation.civilizationB) }) continue
            if (alliances.any { it.matches(relation.civilizationA, relation.civilizationB) }) continue
            val chance = hash01(world.seed.value xor (tick * 31), relation.civilizationA.hashCode(), relation.civilizationB.hashCode())
            if (chance < 0.025) {
                val war = WarState(
                    id = "war-${relation.civilizationA}-${relation.civilizationB}-$tick",
                    civilizationA = relation.civilizationA,
                    civilizationB = relation.civilizationB,
                    startedTick = tick,
                )
                wars += war
                events += SimulationEvent(
                    id = "war-start-${war.id}", tick = tick, code = "WAR_STARTED",
                    actorIds = listOf(war.civilizationA, war.civilizationB),
                    facts = mapOf("a" to (names[war.civilizationA] ?: war.civilizationA), "b" to (names[war.civilizationB] ?: war.civilizationB)),
                )
            }
        }
        return DiplomacyResult(relations, wars, alliances)
    }

    private data class WarResult(
        val settlements: List<Settlement>,
        val wars: List<WarState>,
        val penalties: Map<String, Double>,
    ) {
        fun stabilityPenalty(civilizationId: String): Double = penalties[civilizationId] ?: 0.0
    }

    private fun applyWarEffects(
        input: List<Settlement>,
        wars: List<WarState>,
        civilizations: List<Civilization>,
        tick: Long,
        events: MutableList<SimulationEvent>,
    ): WarResult {
        val settlements = input.toMutableList()
        val updatedWars = ArrayList<WarState>(wars.size)
        val penalties = hashMapOf<String, Double>()
        val civs = civilizations.associateBy { it.id }
        for (war in wars) {
            val a = settlements.filter { it.civilizationId == war.civilizationA }
            val b = settlements.filter { it.civilizationId == war.civilizationB }
            val pair = closestPair(a, b)
            if (pair == null) {
                updatedWars += war
                continue
            }
            val distance = abs(pair.first.x - pair.second.x) + abs(pair.first.y - pair.second.y)
            val proximity = (1.0 - distance / 35.0).coerceIn(0.15, 1.0)
            val powerA = warPower(civs[war.civilizationA], a)
            val powerB = warPower(civs[war.civilizationB], b)
            val totalPower = (powerA + powerB).coerceAtLeast(0.001)
            val shareA = powerA / totalPower
            val shareB = powerB / totalPower
            val lossA = (pair.first.population * proximity * (0.00010 + shareB * 0.00026)).roundToLong().coerceAtLeast(0L)
            val lossB = (pair.second.population * proximity * (0.00010 + shareA * 0.00026)).roundToLong().coerceAtLeast(0L)
            replacePopulation(settlements, pair.first.id, lossA)
            replacePopulation(settlements, pair.second.id, lossB)

            val momentum = (shareA - shareB) * 0.10 + (hash01(world.seed.value xor tick, war.id.hashCode(), tick.toInt()) - 0.5) * 0.05
            var nextWar = war.copy(
                casualtiesA = war.casualtiesA + lossA,
                casualtiesB = war.casualtiesB + lossB,
                scoreA = war.scoreA + momentum + lossB / 150_000.0,
                scoreB = war.scoreB - momentum + lossA / 150_000.0,
            )
            penalties[war.civilizationA] = (penalties[war.civilizationA] ?: 0.0) + 0.00012
            penalties[war.civilizationB] = (penalties[war.civilizationB] ?: 0.0) + 0.00012

            if (tick % 12L == 0L && lossA + lossB > 0L) {
                events += SimulationEvent(
                    id = "battle-${war.id}-$tick", tick = tick, code = "WAR_CASUALTIES",
                    actorIds = listOf(war.civilizationA, war.civilizationB),
                    numbers = mapOf("casualties" to (lossA + lossB).toDouble()),
                    facts = mapOf("settlementA" to pair.first.name, "settlementB" to pair.second.name),
                )
            }

            if (tick % 12L == 0L && tick - war.startedTick >= 12L) {
                nextWar = attemptCapture(nextWar, settlements, tick, events)
            }
            updatedWars += nextWar
        }
        return WarResult(settlements, updatedWars, penalties)
    }

    private fun warPower(civ: Civilization?, settlements: List<Settlement>): Double {
        if (civ == null) return 0.01
        val population = settlements.sumOf { it.population }.coerceAtLeast(1L)
        return population.toDouble() * (0.45 + civ.stability) * (0.65 + civ.technology * 2.2)
    }

    private fun attemptCapture(
        war: WarState,
        settlements: MutableList<Settlement>,
        tick: Long,
        events: MutableList<SimulationEvent>,
    ): WarState {
        val advantage = war.scoreA - war.scoreB
        if (abs(advantage) < 0.18) return war
        val attacker = if (advantage > 0) war.civilizationA else war.civilizationB
        val defender = if (advantage > 0) war.civilizationB else war.civilizationA
        val attackerCities = settlements.filter { it.civilizationId == attacker }
        val defenderCities = settlements.filter { it.civilizationId == defender }
        if (attackerCities.isEmpty() || defenderCities.size <= 1) return war
        val target = defenderCities.minByOrNull { city -> attackerCities.minOf { abs(it.x - city.x) + abs(it.y - city.y) } } ?: return war
        val nearest = attackerCities.minOf { abs(it.x - target.x) + abs(it.y - target.y) }
        if (nearest > 18) return war
        val threshold = (0.11 + abs(advantage) * 0.10).coerceAtMost(0.38)
        val chance = hash01(world.seed.value xor tick, war.id.hashCode(), target.id.hashCode())
        if (chance >= threshold) return war

        val index = settlements.indexOfFirst { it.id == target.id }
        if (index < 0) return war
        settlements[index] = target.copy(
            civilizationId = attacker,
            population = (target.population * 0.84).roundToLong().coerceAtLeast(40L),
            foodStock = target.foodStock * 0.70,
            wealth = target.wealth * 0.78,
        )
        events += SimulationEvent(
            id = "capture-${war.id}-${target.id}-$tick", tick = tick, code = "CITY_CAPTURED",
            actorIds = listOf(attacker, defender), locationId = target.id,
            facts = mapOf("settlement" to target.name, "attacker" to attacker, "defender" to defender),
        )
        return if (advantage > 0) war.copy(scoreA = war.scoreA + 0.45, capturesA = war.capturesA + 1)
        else war.copy(scoreB = war.scoreB + 0.45, capturesB = war.capturesB + 1)
    }

    private data class SettlementResult(
        val relations: List<DiplomaticRelation>,
        val wars: List<WarState>,
        val alliances: List<AllianceState>,
    )

    private fun settleWarsAndAlliances(
        inputRelations: List<DiplomaticRelation>,
        inputWars: List<WarState>,
        inputAlliances: List<AllianceState>,
        civilizations: List<Civilization>,
        tick: Long,
        events: MutableList<SimulationEvent>,
    ): SettlementResult {
        if (tick % 12L != 0L) return SettlementResult(inputRelations, inputWars, inputAlliances)
        val names = civilizations.associate { it.id to it.name }
        val relations = inputRelations.toMutableList()
        val wars = inputWars.toMutableList()
        val alliances = inputAlliances.toMutableList()

        val endedWars = wars.filter { war ->
            val age = tick - war.startedTick
            val decisive = abs(war.scoreA - war.scoreB) >= 1.20
            val exhaustion = age >= 36L && hash01(world.seed.value + tick, war.id.hashCode(), age.toInt()) < 0.10
            decisive || exhaustion || age >= 180L
        }
        endedWars.forEach { war ->
            wars.remove(war)
            val diff = war.scoreA - war.scoreB
            val winnerId = when {
                diff > 0.20 -> war.civilizationA
                diff < -0.20 -> war.civilizationB
                else -> ""
            }
            val winner = names[winnerId] ?: "No clear victor"
            val relationIndex = relations.indexOfFirst { it.matches(war.civilizationA, war.civilizationB) }
            if (relationIndex >= 0) relations[relationIndex] = relations[relationIndex].copy(value = -0.22, lastUpdatedTick = tick)
            events += SimulationEvent(
                id = "peace-${war.id}-$tick", tick = tick, code = "PEACE_TREATY",
                actorIds = listOf(war.civilizationA, war.civilizationB),
                facts = mapOf(
                    "a" to (names[war.civilizationA] ?: war.civilizationA),
                    "b" to (names[war.civilizationB] ?: war.civilizationB),
                    "winner" to winner,
                ),
                numbers = mapOf(
                    "casualties" to (war.casualtiesA + war.casualtiesB).toDouble(),
                    "captures" to (war.capturesA + war.capturesB).toDouble(),
                ),
            )
        }

        val brokenAlliances = alliances.filter { alliance ->
            val relation = relations.firstOrNull { it.matches(alliance.civilizationA, alliance.civilizationB) }
            relation == null || relation.value < 0.18 || wars.any { it.matches(alliance.civilizationA, alliance.civilizationB) }
        }
        brokenAlliances.forEach { alliance ->
            alliances.remove(alliance)
            events += SimulationEvent(
                id = "alliance-end-${alliance.id}-$tick", tick = tick, code = "ALLIANCE_ENDED",
                actorIds = listOf(alliance.civilizationA, alliance.civilizationB),
                facts = mapOf("a" to (names[alliance.civilizationA] ?: alliance.civilizationA), "b" to (names[alliance.civilizationB] ?: alliance.civilizationB)),
            )
        }

        for (relation in relations) {
            if (relation.value < 0.72) continue
            if (wars.any { it.matches(relation.civilizationA, relation.civilizationB) }) continue
            if (alliances.any { it.matches(relation.civilizationA, relation.civilizationB) }) continue
            val chance = hash01(world.seed.value xor (tick * 73), relation.civilizationA.hashCode(), relation.civilizationB.hashCode())
            if (chance < 0.055) {
                val alliance = AllianceState(
                    id = "alliance-${relation.civilizationA}-${relation.civilizationB}-$tick",
                    civilizationA = relation.civilizationA,
                    civilizationB = relation.civilizationB,
                    startedTick = tick,
                )
                alliances += alliance
                events += SimulationEvent(
                    id = "alliance-start-${alliance.id}", tick = tick, code = "ALLIANCE_FORMED",
                    actorIds = listOf(alliance.civilizationA, alliance.civilizationB),
                    facts = mapOf("a" to (names[alliance.civilizationA] ?: alliance.civilizationA), "b" to (names[alliance.civilizationB] ?: alliance.civilizationB)),
                )
            }
        }
        return SettlementResult(relations, wars, alliances)
    }

    private fun replacePopulation(settlements: MutableList<Settlement>, id: String, losses: Long) {
        val index = settlements.indexOfFirst { it.id == id }
        if (index >= 0 && losses > 0) {
            val current = settlements[index]
            settlements[index] = current.copy(population = (current.population - losses).coerceAtLeast(40L))
        }
    }

    private fun closestPair(a: List<Settlement>, b: List<Settlement>): Pair<Settlement, Settlement>? {
        if (a.isEmpty() || b.isEmpty()) return null
        var best: Pair<Settlement, Settlement>? = null
        var bestDistance = Int.MAX_VALUE
        for (left in a) for (right in b) {
            val d = abs(left.x - right.x) + abs(left.y - right.y)
            if (d < bestDistance) {
                bestDistance = d
                best = left to right
            }
        }
        return best
    }

    private fun migratePopulation(settlements: MutableList<Settlement>, tick: Long, events: MutableList<SimulationEvent>) {
        val snapshot = settlements.toList()
        snapshot.forEach { source ->
            if (source.population < 600L || source.foodStock >= source.population * 0.12) return@forEach
            val destination = snapshot.asSequence()
                .filter { it.id != source.id && it.civilizationId == source.civilizationId }
                .filter { it.foodStock > it.population * 0.30 }
                .minByOrNull { abs(it.x - source.x) + abs(it.y - source.y) } ?: return@forEach
            val moved = (source.population * 0.018).roundToLong().coerceAtLeast(25L)
            val sourceIndex = settlements.indexOfFirst { it.id == source.id }
            val destinationIndex = settlements.indexOfFirst { it.id == destination.id }
            if (sourceIndex < 0 || destinationIndex < 0) return@forEach
            val liveSource = settlements[sourceIndex]
            val liveDestination = settlements[destinationIndex]
            settlements[sourceIndex] = liveSource.copy(population = (liveSource.population - moved).coerceAtLeast(40L))
            settlements[destinationIndex] = liveDestination.copy(population = liveDestination.population + moved)
            events += SimulationEvent(
                id = "migration-${source.id}-${destination.id}-$tick", tick = tick, code = "MIGRATION",
                actorIds = listOf(source.civilizationId), numbers = mapOf("people" to moved.toDouble()),
                facts = mapOf("from" to source.name, "to" to destination.name),
            )
        }
    }

    private fun foundColonies(settlements: MutableList<Settlement>, tick: Long, events: MutableList<SimulationEvent>) {
        val founders = settlements.toList()
        founders.forEach { founder ->
            if (founder.population < 2_500L) return@forEach
            if (hash01(world.seed.value xor tick, founder.id.hashCode(), tick.toInt()) >= 0.22) return@forEach
            val target = findExpansionTile(founder, settlements) ?: return@forEach
            val founderIndex = settlements.indexOfFirst { it.id == founder.id }
            if (founderIndex < 0) return@forEach
            val liveFounder = settlements[founderIndex]
            val transfer = (liveFounder.population * 0.12).roundToLong().coerceIn(250L, liveFounder.population / 3)
            val colonyId = "${founder.civilizationId}-colony-$tick-${target.x}-${target.y}"
            val colonyName = settlementNameFor(world.seed.value xor tick xor (target.x.toLong() shl 32) xor target.y.toLong())
            settlements[founderIndex] = liveFounder.copy(population = liveFounder.population - transfer, foodStock = (liveFounder.foodStock * 0.88).coerceAtLeast(0.0))
            settlements += Settlement(colonyId, colonyName, founder.civilizationId, target.x, target.y, transfer, transfer * 0.52, liveFounder.wealth * 0.08, tick)
            events += SimulationEvent(
                id = "colony-$colonyId", tick = tick, code = "COLONY_FOUNDED", actorIds = listOf(founder.civilizationId), locationId = colonyId,
                numbers = mapOf("population" to transfer.toDouble()), facts = mapOf("settlement" to colonyName, "parent" to founder.name),
            )
        }
    }

    private fun findExpansionTile(founder: Settlement, settlements: List<Settlement>): WorldTile? = world.tiles.asSequence()
        .filter { isHabitable(it) }
        .filter { (abs(it.x - founder.x) + abs(it.y - founder.y)) in 5..14 }
        .filter { tile -> settlements.none { abs(it.x - tile.x) + abs(it.y - tile.y) < 4 } }
        .maxByOrNull { settlementScore(it) }

    private fun initialRelations(civs: List<Civilization>): List<DiplomaticRelation> {
        val result = ArrayList<DiplomaticRelation>()
        for (i in civs.indices) for (j in i + 1 until civs.size) {
            val value = (hash01(world.seed.value + 991L, civs[i].id.hashCode(), civs[j].id.hashCode()) * 1.30 - 0.65).coerceIn(-1.0, 1.0)
            result += DiplomaticRelation(civs[i].id, civs[j].id, value, 0L)
        }
        return result
    }

    private fun isHabitable(tile: WorldTile): Boolean = tile.biome !in setOf(Biome.DEEP_OCEAN, Biome.OCEAN, Biome.ICE, Biome.MOUNTAIN)
    private fun settlementScore(tile: WorldTile): Double = habitability(tile) + resourceBonus(tile.x, tile.y) * 0.16 + hash01(world.seed.value, tile.x, tile.y) * 0.08

    private fun habitability(tile: WorldTile): Double {
        val temperatureFit = (1.0 - abs(tile.temperature - 0.58) * 1.45).coerceIn(0.0, 1.0)
        val moistureFit = (1.0 - abs(tile.moisture - 0.56) * 1.25).coerceIn(0.0, 1.0)
        val elevationFit = (1.0 - (tile.elevation - 0.54).coerceAtLeast(0.0) * 1.2).coerceIn(0.0, 1.0)
        return (temperatureFit * 0.42 + moistureFit * 0.36 + elevationFit * 0.22).coerceIn(0.0, 1.0)
    }

    private fun resourceBonus(x: Int, y: Int): Double = resourcesByTile[x to y].orEmpty().sumOf {
        when (it.kind) {
            ResourceKind.FERTILE_LAND -> it.richness * 1.15
            ResourceKind.TIMBER -> it.richness * 0.65
            ResourceKind.METALS -> it.richness * 0.8
            ResourceKind.STONE -> it.richness * 0.45
            ResourceKind.FUEL -> it.richness * 0.9
        }
    }.coerceIn(0.0, 1.5)

    private fun populationBand(population: Long): Int = when {
        population >= 25_000 -> 5
        population >= 10_000 -> 4
        population >= 5_000 -> 3
        population >= 2_000 -> 2
        population >= 1_000 -> 1
        else -> 0
    }

    private fun stabilityDrift(civId: String, tick: Long): Double = (hash01(world.seed.value xor tick, civId.hashCode(), tick.toInt()) - 0.5) * 0.0008
    private fun distance(a: WorldTile, b: WorldTile): Int = abs(a.x - b.x) + abs(a.y - b.y)

    private fun initialCulture(tile: WorldTile): Set<String> = buildSet {
        add(if (tile.temperature > 0.65) "warm-climate" else "temperate-climate")
        if (tile.moisture > 0.65) add("river-and-rain")
        if (tile.elevation > 0.70) add("highland")
        if (tile.biome == Biome.COAST) add("coastal")
    }

    private fun civilizationName(index: Int): String {
        val a = listOf("Ar", "Vel", "Tor", "Mer", "Ka", "Sol", "Ner", "Ily", "Var", "Tal")
        val b = listOf("dan", "ria", "on", "eth", "kar", "ium", "ara", "or", "esh", "en")
        val n = positiveIndex(world.seed.value + index * 37L, a.size * b.size)
        return a[n % a.size] + b[(n / a.size) % b.size]
    }

    private fun settlementName(index: Int): String = settlementNameFor(world.seed.value xor (index * 7919L))
    private fun settlementNameFor(key: Long): String {
        val a = listOf("Astra", "Bren", "Cala", "Daro", "Eren", "Fara", "Galen", "Hara", "Istra", "Kora", "Lume", "Mira")
        val b = listOf("ford", "mere", "polis", "haven", "grad", "port", "vale", "hold", "reach", "gate")
        val n = positiveIndex(key, a.size * b.size)
        return a[n % a.size] + b[(n / a.size) % b.size]
    }

    private fun positiveIndex(value: Long, bound: Int): Int = ((value xor (value ushr 32)) and Long.MAX_VALUE).rem(bound.toLong()).toInt()
    private fun hash01(seed: Long, x: Int, y: Int): Double {
        var z = seed xor (x.toLong() * -7046029254386353131L) xor (y.toLong() * -4658895280553007687L)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return z.ushr(11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }
}
