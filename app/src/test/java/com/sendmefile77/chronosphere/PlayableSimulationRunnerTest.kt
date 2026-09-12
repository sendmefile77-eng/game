package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.NoOpAdultModule
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayableSimulationRunnerTest {
    @Test
    fun repeatedRunFromSameStateIsDeterministic() {
        ChronicleDecisionMailbox.drain()
        val fixture = fixture()

        val first = fixture.runner.advance(fixture.worldState, fixture.people, fixture.economy, fixture.evolution, months = 120)
        val second = fixture.runner.advance(fixture.worldState, fixture.people, fixture.economy, fixture.evolution, months = 120)

        assertEquals(first, second)
        assertEquals(120L, first.world.tick)
        assertEquals(first.world.tick, first.people.tick)
        assertEquals(first.world.tick, first.economy.tick)
        assertEquals(first.world.tick, first.evolution.tick)
        assertTrue(first.world.totalPopulation >= 0L)
        assertTrue(first.world.civilizations.all { it.treasury.isFinite() && it.stability.isFinite() && it.technology.isFinite() })
    }

    @Test
    fun queuedChronicleDecisionIsAppliedAndPersistedOnNextAdvance() {
        ChronicleDecisionMailbox.drain()
        val fixture = fixture()
        val target = fixture.worldState.civilizations.first()
        val beforeTechnology = target.technology
        ChronicleDecisionMailbox.enqueue(
            ChronicleDecisionOption(
                id = "era-push",
                sourceEventId = "source-era-event",
                titleUk = "Продовжити ривок",
                effectUk = "Технологічний імпульс",
                riskUk = "Без прямої стабілізації",
                kind = InterventionKind.TECHNOLOGY_BOOST,
                targetCivilizationId = target.id,
                strength = 0.68,
            ),
        )

        val result = fixture.runner.advance(
            fixture.worldState,
            fixture.people,
            fixture.economy,
            fixture.evolution,
            months = 1,
        )

        assertTrue(result.world.civilizations.first { it.id == target.id }.technology > beforeTechnology)
        assertTrue(
            result.world.recentEvents.any {
                it.facts["sourceEventId"] == "source-era-event" && it.facts["choiceId"] == "era-push"
            },
        )
        assertTrue(ChronicleDecisionMailbox.drain().isEmpty())
    }

    private fun fixture(): Fixture {
        val world = WorldGenerator().generate(WorldSeed(424242L))
        val resources = WorldResourceGenerator().generate(world)
        val worldState = CivilizationEngine(world, resources).initialize()
        val peopleEngine = PeopleEngine()
        val people = peopleEngine.initialize(worldState)
        val economy = EconomyEngine(world, resources).initialize(worldState)
        val evolution = EvolutionEngine(world).initialize(worldState)
        val runner = PlayableSimulationRunner(
            worldMap = world,
            resources = resources,
            peopleEngine = peopleEngine,
            adultModule = NoOpAdultModule,
        )
        return Fixture(worldState, people, economy, evolution, runner)
    }

    private data class Fixture(
        val worldState: com.sendmefile77.chronosphere.civilization.LivingPlanetState,
        val people: com.sendmefile77.chronosphere.people.PeopleState,
        val economy: com.sendmefile77.chronosphere.economy.EconomyState,
        val evolution: com.sendmefile77.chronosphere.evolution.EvolutionState,
        val runner: PlayableSimulationRunner,
    )
}
