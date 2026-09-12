package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
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

internal data class PlayableSimulationState(
    val world: LivingPlanetState,
    val people: PeopleState,
    val economy: EconomyState,
    val evolution: EvolutionState,
)

/**
 * Runs the causal playable simulation slice without any Compose/UI dependency.
 *
 * Keeping this work outside the composable lets the Android shell move long advances to a
 * background dispatcher while preserving the exact deterministic order used by the game.
 */
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

        // A year is the causal integration slice: politics/economy/people/evolution/society
        // are resolved in sequence before the next year begins.
        while (remaining > 0) {
            val step = minOf(12, remaining)
            val fromTick = worldState.tick

            val civilizationNext = applyConfiguredCultureDynamics(
                civilizationEngine.advance(worldState, step),
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

    /** Persistent gameplay effects for player-selected non-biological tribe traits. */
    private fun applyConfiguredCultureDynamics(world: LivingPlanetState, months: Int): LivingPlanetState {
        val years = months / 12.0
        val byId = world.civilizations.associateBy { it.id }
        val civilizations = world.civilizations.map { civ ->
            val tags = civ.cultureTags
            val technologyDelta = when {
                "technological" in tags -> 0.00020 * months
                else -> 0.0
            }
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

    /** Re-applies cultural evolutionary pressure after geographic isolation is recalculated each year. */
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
}
