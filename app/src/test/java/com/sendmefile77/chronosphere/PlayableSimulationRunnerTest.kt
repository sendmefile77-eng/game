package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.NoOpAdultModule
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlayableSimulationRunnerTest {
    @Test
    fun repeatedRunFromSameStateIsDeterministic() {
        ChronicleDecisionMailbox.drain()
        val fixture = fixture()

        val first = fixture.runner.advance(fixture.worldState, fixture.people, fixture.economy, fixture.evolution, months = 120)
        ChronicleDecisionMailbox.drain()
        val second = fixture.runner.advance(fixture.worldState, fixture.people, fixture.economy, fixture.evolution, months = 120)

        assertEquals(first, second)
        assertEquals(fixture.worldState.tick + 120L, first.world.tick)
        assertEquals(first.world.tick, first.people.tick)
        assertEquals(first.world.tick, first.economy.tick)
        assertEquals(first.world.tick, first.evolution.tick)
        assertTrue(first.world.totalPopulation >= 0L)
        assertTrue(first.world.civilizations.all { it.treasury.isFinite() && it.stability.isFinite() && it.technology.isFinite() })
    }

    @Test
    fun confirmedCenturyAlwaysAdvancesFull1200Months() {
        ChronicleDecisionMailbox.drain()
        val fixture = fixture()

        val result = fixture.runner.advance(
            fixture.worldState,
            fixture.people,
            fixture.economy,
            fixture.evolution,
            months = TURN_MONTHS,
        )

        assertEquals(fixture.worldState.tick + TURN_MONTHS, result.world.tick)
        assertEquals(result.world.tick, result.people.tick)
        assertEquals(result.world.tick, result.economy.tick)
        assertEquals(result.world.tick, result.evolution.tick)
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

    @Test
    fun multipleEraChoicesAreAppliedBeforeTheSameSimulationAdvance() {
        ChronicleDecisionMailbox.drain()
        val fixture = fixture()
        val target = fixture.worldState.civilizations.first()
        val decision = EraTurnChoiceCatalog.decision(
            worldSeed = fixture.worldState.worldSeed,
            tick = fixture.worldState.tick,
            civilizationId = target.id,
            civilizationName = target.name,
            era = TechnologyEra.TRIBAL,
            cultureTags = target.cultureTags,
        )
        val fire = decision.options.first { it.id == "era-tribal-breakthrough-fire" }
        val hunters = decision.options.first { it.id == "era-tribal-subsistence-predator_hunters" }
        var chosenWorld = EraTurnChoiceCatalog.applyLegacy(fixture.worldState, target.id, fire.id)
        chosenWorld = EraTurnChoiceCatalog.applyLegacy(chosenWorld, target.id, hunters.id)
        ChronicleDecisionMailbox.enqueue(fire)
        ChronicleDecisionMailbox.enqueue(hunters)

        val result = fixture.runner.advance(
            chosenWorld,
            fixture.people,
            fixture.economy,
            fixture.evolution,
            months = 1,
        )
        val resolvedSources = result.world.recentEvents.mapNotNull { it.facts["sourceEventId"] }.toSet()
        val cultureTags = result.world.civilizations.first { it.id == target.id }.cultureTags

        assertTrue(fire.sourceEventId in resolvedSources)
        assertTrue(hunters.sourceEventId in resolvedSources)
        assertTrue("foundation:fire_mastery" in cultureTags)
        assertTrue("policy:predator_hunters" in cultureTags)
        assertTrue(ChronicleDecisionMailbox.drain().isEmpty())
    }

    @Test
    fun queuedResolutionOfExistingForkDoesNotBlockTheCenturyStart() {
        ChronicleDecisionMailbox.drain()
        val fixture = fixture()
        val target = fixture.worldState.civilizations.first()
        val source = SimulationEvent(
            id = "shortage-now",
            tick = fixture.worldState.tick,
            code = "FOOD_SHORTAGE",
            actorIds = listOf(target.id),
            facts = mapOf("civilization" to target.name),
        )
        val blockedWorld = fixture.worldState.copy(recentEvents = fixture.worldState.recentEvents + source)
        val decision = ChronicleDecisionCatalog.latestUnresolved(blockedWorld.recentEvents, fixture.people, fixture.economy)!!
        ChronicleDecisionMailbox.enqueue(decision.options.first())

        val result = fixture.runner.advance(
            blockedWorld,
            fixture.people,
            fixture.economy,
            fixture.evolution,
            months = 1,
        )

        assertEquals(blockedWorld.tick + 1L, result.world.tick)
        assertTrue(result.world.recentEvents.any { it.facts["sourceEventId"] == source.id })
        assertTrue(ChronicleDecisionMailbox.drain().isEmpty())
    }

    @Test
    fun queuedDiplomacyIsAppliedToTheExplicitSelectedCivilization() {
        ChronicleDecisionMailbox.drain()
        val fixture = fixture()
        val actor = fixture.worldState.civilizations.first()
        val target = fixture.worldState.civilizations[1]
        GameplayLoop.queueAction(
            state = fixture.worldState,
            civilizationId = actor.id,
            kind = InterventionKind.EMBASSY,
            titleUk = "Посольство до ${target.name}",
            effectUk = "Покращити відносини",
            riskUk = "Витрати казни",
            counterpartCivilizationId = target.id,
        )

        val result = fixture.runner.advance(
            fixture.worldState,
            fixture.people,
            fixture.economy,
            fixture.evolution,
            months = 1,
        )

        val action = result.world.recentEvents.firstOrNull {
            it.facts["sourceEventId"] == GameplayLoop.turnSourceId(fixture.worldState.tick)
        }
        assertTrue(action != null)
        assertEquals(target.id, action!!.facts["targetCivilizationId"])
        assertTrue(action.actorIds.contains(actor.id))
        assertTrue(action.actorIds.contains(target.id))
        assertTrue(ChronicleDecisionMailbox.drain().isEmpty())
    }

    @Test
    fun unresolvedImportantEventBlocksFurtherTimeUntilPlayerChooses() {
        ChronicleDecisionMailbox.drain()
        val fixture = fixture()
        val target = fixture.worldState.civilizations.first()
        val blockedWorld = fixture.worldState.copy(
            recentEvents = fixture.worldState.recentEvents + SimulationEvent(
                id = "shortage-now",
                tick = 1L,
                code = "FOOD_SHORTAGE",
                actorIds = listOf(target.id),
                facts = mapOf("civilization" to target.name),
            ),
        )

        try {
            fixture.runner.advance(blockedWorld, fixture.people, fixture.economy, fixture.evolution, months = 12)
            fail("Expected PendingChronicleDecisionException")
        } catch (error: PendingChronicleDecisionException) {
            assertTrue(error.titleUk.contains("Дефіцит"))
        }
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
