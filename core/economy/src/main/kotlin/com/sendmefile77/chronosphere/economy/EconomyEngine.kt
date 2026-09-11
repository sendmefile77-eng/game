package com.sendmefile77.chronosphere.economy

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.worldgen.ResourceDeposit
import com.sendmefile77.chronosphere.worldgen.ResourceKind
import com.sendmefile77.chronosphere.worldgen.WorldMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class EconomyAdvanceResult(
    val state: EconomyState,
    val world: LivingPlanetState,
    val events: List<SimulationEvent>,
)

class EconomyEngine(
    private val map: WorldMap,
    private val deposits: List<ResourceDeposit>,
) {
    fun initialize(world: LivingPlanetState): EconomyState {
        val economies = world.civilizations.map { civilization ->
            createInitialEconomy(civilization, world)
        }
        return EconomyState(
            worldSeed = world.worldSeed,
            tick = world.tick,
            civilizations = economies,
        )
    }

    fun prepareState(state: EconomyState?, world: LivingPlanetState): EconomyState {
        if (state == null || state.worldSeed != world.worldSeed) return initialize(world)
        val known = state.civilizations.associateBy { it.civilizationId }
        val economies = world.civilizations.map { civilization ->
            known[civilization.id] ?: createInitialEconomy(civilization, world)
        }
        return state.copy(civilizations = economies)
    }

    fun advance(input: EconomyState?, world: LivingPlanetState): EconomyAdvanceResult {
        var state = prepareState(input, world)
        require(world.tick >= state.tick) { "Cannot move economy simulation backward" }
        if (world.tick == state.tick) return EconomyAdvanceResult(state, world, emptyList())

        val events = mutableListOf<SimulationEvent>()
        var nextAnnualTick = ((state.tick / 12L) + 1L) * 12L
        var updatedWorld = world
        while (nextAnnualTick <= world.tick) {
            val annual = annualStep(state, updatedWorld, nextAnnualTick, events)
            state = annual.first
            updatedWorld = annual.second
            nextAnnualTick += 12L
        }
        state = state.copy(tick = world.tick)
        updatedWorld = updatedWorld.copy(
            tick = world.tick,
            recentEvents = (updatedWorld.recentEvents + events).takeLast(96),
        )
        return EconomyAdvanceResult(state, updatedWorld, events)
    }

    private fun annualStep(
        previous: EconomyState,
        world: LivingPlanetState,
        tick: Long,
        events: MutableList<SimulationEvent>,
    ): Pair<EconomyState, LivingPlanetState> {
        val previousById = previous.civilizations.associateBy { it.civilizationId }
        val productionById = linkedMapOf<String, Map<EconomicGood, Double>>()
        val demandById = linkedMapOf<String, Map<EconomicGood, Double>>()
        val eraById = linkedMapOf<String, TechnologyEra>()
        val stock = linkedMapOf<String, MutableMap<EconomicGood, Double>>()

        world.civilizations.forEach { civilization ->
            val production = productionFor(civilization, world)
            val era = eraFor(civilization, production)
            val demand = demandFor(civilization, era)
            val previousEconomy = previousById[civilization.id]
            productionById[civilization.id] = production
            demandById[civilization.id] = demand
            eraById[civilization.id] = era
            stock[civilization.id] = EconomicGood.entries.associateWith { good ->
                ((previousEconomy?.stockpiles?.get(good) ?: 0.0) * STOCK_RETENTION + (production[good] ?: 0.0))
                    .coerceAtLeast(0.0)
            }.toMutableMap()
        }

        val surplus = linkedMapOf<String, MutableMap<EconomicGood, Double>>()
        val deficit = linkedMapOf<String, MutableMap<EconomicGood, Double>>()
        world.civilizations.forEach { civilization ->
            val id = civilization.id
            surplus[id] = EconomicGood.entries.associateWith { good ->
                val available = stock.getValue(id).getValue(good)
                val demand = demandById.getValue(id).getValue(good)
                (available - demand * RESERVE_TARGET).coerceAtLeast(0.0)
            }.toMutableMap()
            deficit[id] = EconomicGood.entries.associateWith { good ->
                val available = stock.getValue(id).getValue(good)
                val demand = demandById.getValue(id).getValue(good)
                (demand - available).coerceAtLeast(0.0)
            }.toMutableMap()
        }

        val routes = mutableListOf<TradeRoute>()
        val tradeCash = world.civilizations.associate { it.id to 0.0 }.toMutableMap()
        val budgets = world.civilizations.associate { civilization ->
            civilization.id to (25.0 + civilization.treasury * 0.22).coerceAtLeast(0.0)
        }.toMutableMap()

        EconomicGood.entries.forEach { good ->
            val exporters = world.civilizations.map { it.id }.filter { surplus.getValue(it).getValue(good) > MIN_TRADE_VOLUME }.sorted()
            val importers = world.civilizations.map { it.id }.filter { deficit.getValue(it).getValue(good) > MIN_TRADE_VOLUME }.sorted()
            for (importer in importers) {
                var need = deficit.getValue(importer).getValue(good)
                if (need <= MIN_TRADE_VOLUME) continue
                for (exporter in exporters) {
                    if (exporter == importer || !canTrade(exporter, importer, world)) continue
                    val available = surplus.getValue(exporter).getValue(good)
                    if (available <= MIN_TRADE_VOLUME) continue
                    val importerDemand = demandById.getValue(importer).getValue(good).coerceAtLeast(0.001)
                    val scarcity = (need / importerDemand).coerceIn(0.0, 1.5)
                    val price = BASE_PRICE.getValue(good) * (1.0 + scarcity * 0.55)
                    val budget = budgets.getValue(importer)
                    if (budget <= 0.01) break
                    val capacity = tradeCapacity(exporter, importer, world)
                    val volume = min(min(available, need), min(capacity, budget / price))
                    if (volume <= MIN_TRADE_VOLUME) continue
                    val value = volume * price

                    stock.getValue(exporter)[good] = (stock.getValue(exporter).getValue(good) - volume).coerceAtLeast(0.0)
                    stock.getValue(importer)[good] = stock.getValue(importer).getValue(good) + volume
                    surplus.getValue(exporter)[good] = (available - volume).coerceAtLeast(0.0)
                    need = (need - volume).coerceAtLeast(0.0)
                    deficit.getValue(importer)[good] = need
                    budgets[importer] = (budget - value).coerceAtLeast(0.0)
                    tradeCash[importer] = tradeCash.getValue(importer) - value
                    tradeCash[exporter] = tradeCash.getValue(exporter) + value
                    routes += TradeRoute(
                        id = "trade-$exporter-$importer-${good.name.lowercase()}",
                        exporterId = exporter,
                        importerId = importer,
                        good = good,
                        volume = volume,
                        value = value,
                        tick = tick,
                    )
                    if (need <= MIN_TRADE_VOLUME) break
                }
            }
        }

        val economies = mutableListOf<CivilizationEconomy>()
        val updatedCivilizations = world.civilizations.map { civilization ->
            val id = civilization.id
            val demand = demandById.getValue(id)
            val production = productionById.getValue(id)
            var totalDemand = 0.0
            var unmet = 0.0
            val finalStock = linkedMapOf<EconomicGood, Double>()
            EconomicGood.entries.forEach { good ->
                val required = demand.getValue(good)
                val available = stock.getValue(id).getValue(good)
                totalDemand += required
                unmet += (required - available).coerceAtLeast(0.0)
                finalStock[good] = (available - required).coerceAtLeast(0.0)
            }
            val shortage = if (totalDemand <= 0.0) 0.0 else (unmet / totalDemand).coerceIn(0.0, 1.0)
            val grossOutput = EconomicGood.entries.sumOf { good ->
                production.getValue(good) * BASE_PRICE.getValue(good)
            }
            val tradeBalance = tradeCash.getValue(id)
            val previousEconomy = previousById[id]
            val era = eraById.getValue(id)
            val taxIncome = grossOutput * 0.045 * (1.0 - shortage * 0.60)
            val maintenance = civilization.population / 1000.0 * 0.055
            val nextTreasury = (civilization.treasury + taxIncome - maintenance + tradeBalance).coerceAtLeast(0.0)
            val prosperity = (grossOutput / ((civilization.population / 1000.0).coerceAtLeast(0.2) * 16.0)).coerceIn(0.0, 1.5)
            val tradeIntensity = routes.count { it.exporterId == id || it.importerId == id }.coerceAtMost(12)
            val techGain = 0.00018 + prosperity * 0.00034 + tradeIntensity * 0.000018
            val stabilityDelta = when {
                shortage >= 0.45 -> -0.028
                shortage >= 0.25 -> -0.012
                shortage <= 0.05 -> 0.003
                else -> 0.0
            }

            if ((previousEconomy?.shortageIndex ?: 0.0) < 0.25 && shortage >= 0.25) {
                events += SimulationEvent(
                    id = "economic-shortage-$id-$tick",
                    tick = tick,
                    code = "ECONOMIC_SHORTAGE",
                    actorIds = listOf(id),
                    numbers = mapOf("shortage" to shortage),
                    facts = mapOf("civilization" to civilization.name),
                )
            }
            if (previousEconomy != null && era.ordinal > previousEconomy.era.ordinal) {
                events += SimulationEvent(
                    id = "era-advanced-$id-${era.name.lowercase()}-$tick",
                    tick = tick,
                    code = "ERA_ADVANCED",
                    actorIds = listOf(id),
                    facts = mapOf(
                        "civilization" to civilization.name,
                        "era" to era.displayNameUk,
                    ),
                )
            }

            economies += CivilizationEconomy(
                civilizationId = id,
                era = era,
                stockpiles = finalStock,
                production = production,
                demand = demand,
                shortageIndex = shortage,
                tradeBalance = tradeBalance,
                grossOutput = grossOutput,
            )
            civilization.copy(
                treasury = nextTreasury,
                technology = (civilization.technology + techGain).coerceIn(0.0, 1.0),
                stability = (civilization.stability + stabilityDelta).coerceIn(0.10, 0.98),
            )
        }

        if (tick % 60L == 0L) {
            val names = world.civilizations.associate { it.id to it.name }
            routes.sortedByDescending { it.value }.take(3).forEach { route ->
                events += SimulationEvent(
                    id = "trade-flow-${route.exporterId}-${route.importerId}-${route.good.name.lowercase()}-$tick",
                    tick = tick,
                    code = "TRADE_FLOW",
                    actorIds = listOf(route.exporterId, route.importerId),
                    numbers = mapOf("volume" to route.volume, "value" to route.value),
                    facts = mapOf(
                        "exporter" to (names[route.exporterId] ?: route.exporterId),
                        "importer" to (names[route.importerId] ?: route.importerId),
                        "good" to route.good.name,
                    ),
                )
            }
        }

        return EconomyState(
            worldSeed = previous.worldSeed,
            tick = tick,
            civilizations = economies,
            routes = routes,
        ) to world.copy(civilizations = updatedCivilizations)
    }

    private fun createInitialEconomy(civilization: Civilization, world: LivingPlanetState): CivilizationEconomy {
        val production = productionFor(civilization, world)
        val era = eraFor(civilization, production)
        val demand = demandFor(civilization, era)
        return CivilizationEconomy(
            civilizationId = civilization.id,
            era = era,
            stockpiles = EconomicGood.entries.associateWith { good -> (production[good] ?: 0.0) * 0.70 },
            production = production,
            demand = demand,
            shortageIndex = 0.0,
            tradeBalance = 0.0,
            grossOutput = EconomicGood.entries.sumOf { good -> production.getValue(good) * BASE_PRICE.getValue(good) },
        )
    }

    private fun productionFor(civilization: Civilization, world: LivingPlanetState): Map<EconomicGood, Double> {
        val settlements = world.settlements.filter { it.civilizationId == civilization.id }
        val populationUnits = (settlements.sumOf { it.population } / 1000.0).coerceAtLeast(0.10)
        val richness = richnessNear(settlements.map { it.x to it.y })
        val technology = civilization.technology.coerceIn(0.0, 1.0)
        val food = populationUnits * (4.7 + technology * 1.5) + richness.getValue(ResourceKind.FERTILE_LAND) * 6.0
        val timber = populationUnits * 0.55 + richness.getValue(ResourceKind.TIMBER) * 4.2
        val stone = populationUnits * 0.38 + richness.getValue(ResourceKind.STONE) * 3.6
        val metal = populationUnits * (0.10 + technology * 0.48) + richness.getValue(ResourceKind.METALS) * (2.0 + technology * 2.0)
        val fuel = populationUnits * technology * 0.22 + richness.getValue(ResourceKind.FUEL) * (1.8 + technology * 2.4)
        val crafts = populationUnits * (0.24 + technology * 1.85) + min(timber + stone + metal, populationUnits * 3.0) * 0.10
        return linkedMapOf(
            EconomicGood.FOOD to food,
            EconomicGood.TIMBER to timber,
            EconomicGood.STONE to stone,
            EconomicGood.METAL to metal,
            EconomicGood.FUEL to fuel,
            EconomicGood.CRAFTS to crafts,
        )
    }

    private fun demandFor(civilization: Civilization, era: TechnologyEra): Map<EconomicGood, Double> {
        val p = (civilization.population / 1000.0).coerceAtLeast(0.10)
        val advanced = era.ordinal / (TechnologyEra.entries.size - 1.0)
        return linkedMapOf(
            EconomicGood.FOOD to p * (5.0 + advanced * 0.8),
            EconomicGood.TIMBER to p * (0.65 + advanced * 0.55),
            EconomicGood.STONE to p * (0.42 + advanced * 0.62),
            EconomicGood.METAL to p * (0.12 + advanced * 1.35),
            EconomicGood.FUEL to p * (0.03 + advanced * advanced * 1.75),
            EconomicGood.CRAFTS to p * (0.30 + advanced * 1.25),
        )
    }

    private fun eraFor(civilization: Civilization, production: Map<EconomicGood, Double>): TechnologyEra {
        val technology = civilization.technology
        val population = civilization.population
        val metal = production.getValue(EconomicGood.METAL)
        val fuel = production.getValue(EconomicGood.FUEL)
        return when {
            technology >= 0.97 && population >= 2_000_000L -> TechnologyEra.SPACEFARING
            technology >= 0.86 && population >= 500_000L -> TechnologyEra.INFORMATION
            technology >= 0.72 && population >= 220_000L -> TechnologyEra.ELECTRIC
            technology >= 0.55 && population >= 100_000L && fuel >= 5.0 -> TechnologyEra.INDUSTRIAL
            technology >= 0.38 && population >= 45_000L && metal >= 4.0 -> TechnologyEra.EARLY_INDUSTRIAL
            technology >= 0.24 && population >= 18_000L -> TechnologyEra.MEDIEVAL
            technology >= 0.14 && metal >= 1.5 -> TechnologyEra.METALLURGIC
            technology >= 0.08 && population >= 5_000L -> TechnologyEra.URBAN
            technology >= 0.04 || population >= 2_000L -> TechnologyEra.AGRARIAN
            else -> TechnologyEra.TRIBAL
        }
    }

    private fun richnessNear(settlements: List<Pair<Int, Int>>): Map<ResourceKind, Double> {
        if (settlements.isEmpty()) return ResourceKind.entries.associateWith { 0.0 }
        val totals = ResourceKind.entries.associateWith { 0.0 }.toMutableMap()
        deposits.forEach { deposit ->
            val close = settlements.any { (x, y) -> abs(x - deposit.x) + abs(y - deposit.y) <= RESOURCE_RADIUS }
            if (close) totals[deposit.kind] = totals.getValue(deposit.kind) + deposit.richness
        }
        return totals
    }

    private fun canTrade(a: String, b: String, world: LivingPlanetState): Boolean {
        if (world.wars.any { it.matches(a, b) }) return false
        val relation = world.relations.firstOrNull {
            (it.civilizationA == a && it.civilizationB == b) || (it.civilizationA == b && it.civilizationB == a)
        }
        return (relation?.value ?: 0.0) >= -0.35
    }

    private fun tradeCapacity(a: String, b: String, world: LivingPlanetState): Double {
        val settlementsA = world.settlements.filter { it.civilizationId == a }
        val settlementsB = world.settlements.filter { it.civilizationId == b }
        if (settlementsA.isEmpty() || settlementsB.isEmpty()) return 0.0
        val minDistance = settlementsA.minOf { left ->
            settlementsB.minOf { right -> abs(left.x - right.x) + abs(left.y - right.y) }
        }
        val population = min(
            world.civilizations.firstOrNull { it.id == a }?.population ?: 0L,
            world.civilizations.firstOrNull { it.id == b }?.population ?: 0L,
        )
        val allianceBoost = if (world.alliances.any { it.matches(a, b) }) 1.35 else 1.0
        val distanceFactor = (1.0 - minDistance / (map.width + map.height).toDouble()).coerceIn(0.20, 1.0)
        return (1.5 + sqrt(population.coerceAtLeast(1L).toDouble() / 1000.0)) * distanceFactor * allianceBoost
    }

    companion object {
        private const val STOCK_RETENTION = 0.93
        private const val RESERVE_TARGET = 1.12
        private const val RESOURCE_RADIUS = 5
        private const val MIN_TRADE_VOLUME = 0.05

        private val BASE_PRICE = mapOf(
            EconomicGood.FOOD to 1.0,
            EconomicGood.TIMBER to 1.35,
            EconomicGood.STONE to 1.20,
            EconomicGood.METAL to 3.10,
            EconomicGood.FUEL to 2.60,
            EconomicGood.CRAFTS to 4.20,
        )
    }
}
