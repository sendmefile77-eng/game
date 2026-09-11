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
        val candidates = world.tiles
            .asSequence()
            .filter { isHabitable(it) }
            .sortedByDescending { settlementScore(it) }
            .toList()

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
            val settlementName = settlementName(index)
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
            settlements += Settlement(
                id = settlementId,
                name = settlementName,
                civilizationId = civId,
                x = tile.x,
                y = tile.y,
                population = population,
                foodStock = population * 0.65,
                wealth = 60.0,
                foundedTick = 0,
            )
            events += SimulationEvent(
                id = "founding-$index",
                tick = 0,
                code = "SETTLEMENT_FOUNDED",
                actorIds = listOf(civId),
                locationId = settlementId,
                numbers = mapOf("population" to population.toDouble()),
                facts = mapOf("civilization" to civName, "settlement" to settlementName),
            )
        }
        return LivingPlanetState(world.seed.value, 0L, civilizations, settlements, events.takeLast(32))
    }

    fun advance(state: LivingPlanetState, months: Int): LivingPlanetState {
        require(months in 1..12_000)
        var current = state
        repeat(months) { current = step(current) }
        return current
    }

    private fun step(state: LivingPlanetState): LivingPlanetState {
        val nextTick = state.tick + 1
        val generatedEvents = ArrayList<SimulationEvent>()
        val updatedSettlements = state.settlements.map { settlement ->
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

            val oldBand = populationBand(settlement.population)
            val newBand = populationBand(nextPopulation)
            if (newBand > oldBand) {
                generatedEvents += SimulationEvent(
                    id = "growth-${settlement.id}-$nextTick-$newBand",
                    tick = nextTick,
                    code = "SETTLEMENT_GROWTH",
                    actorIds = listOf(settlement.civilizationId),
                    locationId = settlement.id,
                    numbers = mapOf("population" to nextPopulation.toDouble()),
                    facts = mapOf("settlement" to settlement.name),
                )
            }
            if (hungerPenalty < 0.0 && nextTick % 12L == 0L) {
                generatedEvents += SimulationEvent(
                    id = "shortage-${settlement.id}-$nextTick",
                    tick = nextTick,
                    code = "FOOD_SHORTAGE",
                    actorIds = listOf(settlement.civilizationId),
                    locationId = settlement.id,
                    numbers = mapOf("population" to nextPopulation.toDouble()),
                    facts = mapOf("settlement" to settlement.name),
                )
            }
            settlement.copy(population = nextPopulation, foodStock = nextFood, wealth = nextWealth)
        }.toMutableList()

        if (nextTick % 240L == 0L) {
            foundColonies(updatedSettlements, nextTick, generatedEvents)
        }

        val populations = updatedSettlements.groupBy { it.civilizationId }.mapValues { (_, list) -> list.sumOf { it.population } }
        val wealth = updatedSettlements.groupBy { it.civilizationId }.mapValues { (_, list) -> list.sumOf { it.wealth } }
        val updatedCivilizations = state.civilizations.map { civ ->
            val pop = populations[civ.id] ?: 0L
            civ.copy(
                population = pop,
                treasury = (civ.treasury * 0.998 + (wealth[civ.id] ?: 0.0) * 0.002).coerceAtLeast(0.0),
                technology = (civ.technology + 0.0000025 * (1.0 + pop / 10_000.0)).coerceAtMost(1.0),
                stability = (civ.stability + stabilityDrift(civ.id, nextTick)).coerceIn(0.15, 0.95),
            )
        }

        return state.copy(
            tick = nextTick,
            civilizations = updatedCivilizations,
            settlements = updatedSettlements,
            recentEvents = (state.recentEvents + generatedEvents).takeLast(48),
        )
    }

    private fun foundColonies(
        settlements: MutableList<Settlement>,
        tick: Long,
        events: MutableList<SimulationEvent>,
    ) {
        val founders = settlements.toList()
        founders.forEach { founder ->
            if (founder.population < 2_500L) return@forEach
            val chance = hash01(world.seed.value xor tick, founder.id.hashCode(), tick.toInt())
            if (chance >= 0.22) return@forEach
            val target = findExpansionTile(founder, settlements) ?: return@forEach
            val founderIndex = settlements.indexOfFirst { it.id == founder.id }
            if (founderIndex < 0) return@forEach
            val liveFounder = settlements[founderIndex]
            val transfer = (liveFounder.population * 0.12).roundToLong().coerceIn(250L, liveFounder.population / 3)
            val colonyId = "${founder.civilizationId}-colony-$tick-${target.x}-${target.y}"
            val colonyName = settlementNameFor(world.seed.value xor tick xor (target.x.toLong() shl 32) xor target.y.toLong())
            settlements[founderIndex] = liveFounder.copy(
                population = liveFounder.population - transfer,
                foodStock = (liveFounder.foodStock * 0.88).coerceAtLeast(0.0),
            )
            settlements += Settlement(
                id = colonyId,
                name = colonyName,
                civilizationId = founder.civilizationId,
                x = target.x,
                y = target.y,
                population = transfer,
                foodStock = transfer * 0.52,
                wealth = liveFounder.wealth * 0.08,
                foundedTick = tick,
            )
            events += SimulationEvent(
                id = "colony-$colonyId",
                tick = tick,
                code = "COLONY_FOUNDED",
                actorIds = listOf(founder.civilizationId),
                locationId = colonyId,
                numbers = mapOf("population" to transfer.toDouble()),
                facts = mapOf("settlement" to colonyName, "parent" to founder.name),
            )
        }
    }

    private fun findExpansionTile(founder: Settlement, settlements: List<Settlement>): WorldTile? = world.tiles
        .asSequence()
        .filter { isHabitable(it) }
        .filter {
            val d = abs(it.x - founder.x) + abs(it.y - founder.y)
            d in 5..14
        }
        .filter { tile -> settlements.none { abs(it.x - tile.x) + abs(it.y - tile.y) < 4 } }
        .maxByOrNull { settlementScore(it) }

    private fun isHabitable(tile: WorldTile): Boolean = tile.biome !in setOf(Biome.DEEP_OCEAN, Biome.OCEAN, Biome.ICE, Biome.MOUNTAIN)

    private fun settlementScore(tile: WorldTile): Double =
        habitability(tile) + resourceBonus(tile.x, tile.y) * 0.16 + hash01(world.seed.value, tile.x, tile.y) * 0.08

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
