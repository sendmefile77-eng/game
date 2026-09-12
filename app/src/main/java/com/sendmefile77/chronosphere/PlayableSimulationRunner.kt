package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.AdmixtureEngine
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.society.MorphologyContextAdultModule
import com.sendmefile77.chronosphere.society.SocietyEngine
import com.sendmefile77.chronosphere.worldgen.ResourceDeposit
import com.sendmefile77.chronosphere.worldgen.WorldMap
import kotlin.math.abs

internal data class PlayableSimulationState(
    val world: LivingPlanetState,
    val people: PeopleState,
    val economy: EconomyState,
    val evolution: EvolutionState,
)

/** Runs the causal playable simulation slice without any Compose/UI dependency. */
internal class PlayableSimulationRunner(
    private val worldMap: WorldMap,
    private val resources: List<ResourceDeposit>,
    private val peopleEngine: PeopleEngine,
    private val adultModule: AdultModule,
) {
    fun advance(
        currentWorld: LivingPlanetState,
        currentPeople: PeopleState,
        currentEconomy: EconomyState,
        currentEvolution: EvolutionState,
        months: Int,
    ): PlayableSimulationState {
        require(months > 0)

        val civilizationEngine = CivilizationEngine(worldMap, resources)
        val economyEngine = EconomyEngine(worldMap, resources)
        val evolutionEngine = EvolutionEngine(worldMap)
        val admixtureEngine = AdmixtureEngine(worldMap)

        var worldState = currentWorld
        var people = currentPeople
        var economy = currentEconomy
        var evolution = currentEvolution
        var remaining = months

        while (remaining > 0) {
            val step = minOf(12, remaining)
            val fromTick = worldState.tick

            val civilizationNext = applyConfiguredCultureDynamics(
                consolidateMinorSettlements(civilizationEngine.advance(worldState, step)),
                months = step,
            )
            val economyResult = economyEngine.advance(economy, civilizationNext)
            val peopleResult = peopleEngine.advance(people, economyResult.world)
            val worldWithPeople = economyResult.world.copy(
                recentEvents = (economyResult.world.recentEvents + peopleResult.events).takeLast(96),
            )
            val peopleAtTick = peopleResult.state.copy(tick = worldWithPeople.tick)
            val economyAtTick = economyResult.state.copy(tick = worldWithPeople.tick)

            val evolutionResult = evolutionEngine.advance(evolution, worldWithPeople)
            val cultureBiasedEvolution = applyConfiguredEvolutionBias(evolutionResult.state)
            val admixtureEvents = mutableListOf<SimulationEvent>()
            val evolutionAtTick = if (step == 12) {
                admixtureEngine.annualStep(
                    cultureBiasedEvolution,
                    worldWithPeople,
                    worldWithPeople.tick,
                    admixtureEvents,
                )
            } else {
                cultureBiasedEvolution
            }
            val worldWithEvolution = worldWithPeople.copy(
                recentEvents = (
                    worldWithPeople.recentEvents + evolutionResult.events + admixtureEvents
                    ).takeLast(96),
            )

            val morphologyAwareModule = MorphologyContextAdultModule(
                delegate = adultModule,
                people = peopleAtTick,
                evolution = evolutionAtTick,
            )
            val societyResult = SocietyEngine(morphologyAwareModule).advance(
                fromTick = fromTick,
                world = worldWithEvolution,
                people = peopleAtTick,
                economy = economyAtTick,
            )

            worldState = societyResult.world
            people = societyResult.people.copy(tick = worldState.tick)
            economy = economyAtTick
            evolution = evolutionAtTick.copy(tick = worldState.tick)
            remaining -= step
        }

        return PlayableSimulationState(
            world = worldState,
            people = people,
            economy = economy,
            evolution = evolution,
        )
    }

    /**
     * Keeps the map readable. The civilization core may found many small colonies over millennia;
     * once a civilization exceeds its development-appropriate visible-center budget, the smallest
     * colonies are folded into nearby major centers. Population, food and wealth are conserved.
     */
    private fun consolidateMinorSettlements(world: LivingPlanetState): LivingPlanetState {
        val removedIds = linkedSetOf<String>()
        val consolidated = mutableListOf<Settlement>()

        world.civilizations.forEach { civilization ->
            val all = world.settlements.filter { it.civilizationId == civilization.id }
            val cap = visibleSettlementCap(civilization.technology)
            if (all.size <= cap) {
                consolidated += all
                return@forEach
            }

            val keep = all.sortedWith(
                compareByDescending<Settlement> { it.population }
                    .thenBy { it.foundedTick }
                    .thenBy { it.id },
            ).take(cap)
            val keepIds = keep.mapTo(hashSetOf()) { it.id }
            val extras = all.filter { it.id !in keepIds }
            removedIds += extras.map { it.id }

            val populationBonus = keep.associate { it.id to 0L }.toMutableMap()
            val foodBonus = keep.associate { it.id to 0.0 }.toMutableMap()
            val wealthBonus = keep.associate { it.id to 0.0 }.toMutableMap()
            extras.forEach { extra ->
                val target = keep.minWithOrNull(
                    compareBy<Settlement> { abs(it.x - extra.x) + abs(it.y - extra.y) }
                        .thenByDescending { it.population },
                ) ?: return@forEach
                populationBonus[target.id] = populationBonus.getValue(target.id) + extra.population
                foodBonus[target.id] = foodBonus.getValue(target.id) + extra.foodStock
                wealthBonus[target.id] = wealthBonus.getValue(target.id) + extra.wealth
            }
            consolidated += keep.map { center ->
                center.copy(
                    population = center.population + populationBonus.getValue(center.id),
                    foodStock = center.foodStock + foodBonus.getValue(center.id),
                    wealth = center.wealth + wealthBonus.getValue(center.id),
                )
            }
        }

        if (removedIds.isEmpty()) return world
        val retained = consolidated.sortedWith(
            compareBy<Settlement> { it.civilizationId }
                .thenByDescending { it.population }
                .thenBy { it.id },
        )
        return world.copy(
            settlements = retained,
            recentEvents = world.recentEvents.filterNot { event ->
                event.locationId in removedIds && event.code in NOISY_MINOR_SETTLEMENT_EVENTS
            }.takeLast(96),
        )
    }

    private fun visibleSettlementCap(technology: Double): Int = when {
        technology < 0.10 -> 3
        technology < 0.30 -> 4
        technology < 0.55 -> 5
        else -> 7
    }

    private fun applyConfiguredCultureDynamics(world: LivingPlanetState, months: Int): LivingPlanetState {
        val years = months / 12.0
        val byId = world.civilizations.associateBy { it.id }
        val civilizations = world.civilizations.map { civ ->
            val tags = civ.cultureTags
            val technologyDelta = if ("technological" in tags) 0.00020 * months else 0.0
            val treasuryDelta = when {
                "mercantile" in tags -> civ.population * 0.00010 * months
                "weak_economy" in tags -> -civ.population * 0.00007 * months
                else -> 0.0
            }
            val stabilityDelta = when {
                "weak_internal_splits" in tags -> -0.0035 * years
                "dynastic" in tags -> 0.0015 * years
                else -> 0.0
            }
            civ.copy(
                technology = (civ.technology + technologyDelta).coerceIn(0.0, 1.0),
                treasury = (civ.treasury + treasuryDelta).coerceAtLeast(0.0),
                stability = (civ.stability + stabilityDelta).coerceIn(0.15, 0.95),
            )
        }
        val relations = world.relations.map { relation ->
            val a = byId[relation.civilizationA]?.cultureTags.orEmpty()
            val b = byId[relation.civilizationB]?.cultureTags.orEmpty()
            var drift = 0.0
            if ("warlike" in a || "warlike" in b) drift -= 0.010 * years
            if ("weak_xenophobia" in a || "weak_xenophobia" in b) drift -= 0.012 * years
            if ("mercantile" in a && "mercantile" in b) drift += 0.006 * years
            if ("hybrid_friendly" in a || "hybrid_friendly" in b) drift += 0.003 * years
            relation.copy(value = (relation.value + drift).coerceIn(-1.0, 1.0))
        }
        return world.copy(civilizations = civilizations, relations = relations)
    }

    private fun applyConfiguredEvolutionBias(state: EvolutionState): EvolutionState = state.copy(
        populations = state.populations.map { population ->
            val tags = state.lineage(population.lineageId)?.tags.orEmpty()
            var isolation = population.isolation
            var mutationPressure = population.mutationPressure
            if ("isolationist" in tags) isolation += 0.20
            if ("hybrid_friendly" in tags) isolation -= 0.18
            if ("weak_genetic_bottleneck" in tags) isolation += 0.10
            if ("rapid_mutation" in tags) mutationPressure = maxOf(mutationPressure, 0.72)
            isolation = isolation.coerceIn(0.02, 0.98)
            population.copy(
                isolation = isolation,
                geneFlow = (1.0 - isolation).coerceIn(0.0, 1.0),
                mutationPressure = mutationPressure.coerceIn(0.0, 1.0),
            )
        },
    )

    companion object {
        private val NOISY_MINOR_SETTLEMENT_EVENTS = setOf(
            "COLONY_FOUNDED",
            "SETTLEMENT_GROWTH",
            "FOOD_SHORTAGE",
            "MIGRATION",
        )
    }
}
