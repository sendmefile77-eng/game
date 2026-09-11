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

internal data class SimulationAdvanceResult(
    val world: LivingPlanetState,
    val people: PeopleState,
    val economy: EconomyState,
    val evolution: EvolutionState,
)

internal fun advanceSimulation(
    world: WorldMap,
    resources: List<ResourceDeposit>,
    initialWorld: LivingPlanetState,
    initialPeople: PeopleState,
    initialEconomy: EconomyState,
    initialEvolution: EvolutionState,
    peopleEngine: PeopleEngine,
    adultModule: AdultModule,
    months: Int,
): SimulationAdvanceResult {
    require(months > 0)

    val civilizationEngine = CivilizationEngine(world, resources)
    val economyEngine = EconomyEngine(world, resources)
    val evolutionEngine = EvolutionEngine(world)
    val admixtureEngine = AdmixtureEngine(world)
    var worldState = initialWorld
    var people = initialPeople
    var economy = initialEconomy
    var evolution = initialEvolution
    var remaining = months

    // A year is the causal integration slice. Keeping this order stable is part of determinism.
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
            recentEvents = (worldWithPeople.recentEvents + evolutionResult.events + admixtureEvents).takeLast(96),
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

    return SimulationAdvanceResult(worldState, people, economy, evolution)
}
