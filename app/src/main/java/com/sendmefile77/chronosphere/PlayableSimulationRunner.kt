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

            val civilizationNext = civilizationEngine.advance(worldState, step)
            val economyResult = economyEngine.advance(economy, civilizationNext)
            val peopleResult = peopleEngine.advance(people, economyResult.world)
            val worldWithPeople = economyResult.world.copy(
                recentEvents = (economyResult.world.recentEvents + peopleResult.events).takeLast(96),
            )
            val peopleAtTick = peopleResult.state.copy(tick = worldWithPeople.tick)
            val economyAtTick = economyResult.state.copy(tick = worldWithPeople.tick)

            val evolutionResult = evolutionEngine.advance(evolution, worldWithPeople)
            val admixtureEvents = mutableListOf<SimulationEvent>()
            val evolutionAtTick = if (step == 12) {
                admixtureEngine.annualStep(
                    evolutionResult.state,
                    worldWithPeople,
                    worldWithPeople.tick,
                    admixtureEvents,
                )
            } else {
                evolutionResult.state
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
}
