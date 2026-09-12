package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.NoOpAdultModule
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldHydrology
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WorldSetupIntegrationTest {
    private val generator = WorldGenerator()
    private val hydrology = WorldHydrology()
    private val resources = WorldResourceGenerator()
    private val peopleEngine = PeopleEngine()

    @Test
    fun configuredStartUsesRequestedTribesBiologyAndSexualCulture() {
        val setup = sampleSetup(StartSpacing.NORMAL)
        val start = createConfiguredWorldStart(setup, generator, hydrology, resources, peopleEngine)

        val orderedCivilizations = start.session.state.civilizations.sortedBy { it.id.substringAfterLast('-').toInt() }
        assertEquals(3, orderedCivilizations.size)
        assertEquals(listOf("Астарі", "Варки", "Нері"), orderedCivilizations.map { it.name })

        val firstCiv = orderedCivilizations[0]
        val firstSettlement = start.session.state.settlements.first { it.civilizationId == firstCiv.id }
        val firstLineage = start.evolution.lineageForSettlement(firstSettlement.id)!!
        val firstPopulation = start.evolution.population(firstSettlement.id)!!
        val firstProfile = start.people.profile(firstCiv.id)!!

        assertEquals(2, firstLineage.bodyPlan.armPairs)
        assertNull(firstLineage.parentLineageId)
        assertTrue("independent_origin" in firstLineage.tags)
        assertTrue("human_derived" !in firstLineage.tags)
        assertTrue("race_four_armed" in firstLineage.tags)
        assertTrue("group_sex" in firstProfile.tags)
        assertTrue("public_sex" in firstProfile.tags)
        assertTrue(firstProfile.bodyOpenness >= 0.65)
        assertTrue(firstProfile.privacy <= 0.45)
        assertTrue(firstPopulation.mutationPressure >= 0.72)
        assertTrue(firstPopulation.geneFlow > 0.0)
    }

    @Test
    fun oneTribeWorldStartsWithExactlyOneCivilization() {
        val setup = WorldSetup.default(seed = 424242L, tribeCount = 1)
        assertEquals(1, setup.tribes.size)

        val start = createConfiguredWorldStart(setup, generator, hydrology, resources, peopleEngine)

        assertEquals(1, start.session.state.civilizations.size)
        assertEquals(1, start.session.state.settlements.map { it.civilizationId }.distinct().size)
        assertEquals(1, start.people.socialProfiles.size)
        assertEquals(setup.tribes.single().name, start.session.state.civilizations.single().name)
    }

    @Test
    fun farStartSpacingReallyPlacesTribesFartherApart() {
        val close = createConfiguredWorldStart(sampleSetup(StartSpacing.CLOSE), generator, hydrology, resources, peopleEngine)
        val far = createConfiguredWorldStart(sampleSetup(StartSpacing.FAR), generator, hydrology, resources, peopleEngine)

        val closeDistance = minimumSettlementDistance(close)
        val farDistance = minimumSettlementDistance(far)
        assertTrue("far=$farDistance close=$closeDistance", farDistance >= closeDistance)
        assertTrue("far starts should honor requested spacing on the default world", farDistance >= StartSpacing.FAR.minimumDistance)
    }

    @Test
    fun configuredEvolutionPressureSurvivesSimulationAdvance() {
        val start = createConfiguredWorldStart(sampleSetup(StartSpacing.NORMAL), generator, hydrology, resources, peopleEngine)
        val firstCiv = start.session.state.civilizations.sortedBy { it.id }.first()
        val firstSettlement = start.session.state.settlements.first { it.civilizationId == firstCiv.id }
        val runner = runner(start)

        val advanced = runner.advance(
            currentWorld = start.session.state,
            currentPeople = start.people,
            currentEconomy = start.economy,
            currentEvolution = start.evolution,
            months = 12,
        )

        val population = advanced.evolution.population(firstSettlement.id)!!
        assertTrue(population.mutationPressure >= 0.72)
        val lineage = advanced.evolution.lineage(population.lineageId)!!
        assertTrue("rapid_mutation" in lineage.tags)
        assertTrue("hybrid_friendly" in lineage.tags)
    }

    @Test
    fun longRunKeepsOnlyDevelopmentAppropriateMajorCenters() {
        val start = createConfiguredWorldStart(sampleSetup(StartSpacing.NORMAL), generator, hydrology, resources, peopleEngine)
        val advanced = runner(start).advance(
            currentWorld = start.session.state,
            currentPeople = start.people,
            currentEconomy = start.economy,
            currentEvolution = start.evolution,
            months = 2_400,
        )

        advanced.world.civilizations.forEach { civilization ->
            val expectedCap = when {
                civilization.technology < 0.10 -> 3
                civilization.technology < 0.30 -> 4
                civilization.technology < 0.55 -> 5
                else -> 7
            }
            val actual = advanced.world.settlements.count { it.civilizationId == civilization.id }
            assertTrue("${civilization.id}: $actual > $expectedCap", actual <= expectedCap)
        }
    }

    private fun runner(start: ConfiguredWorldStart): PlayableSimulationRunner = PlayableSimulationRunner(
        worldMap = start.session.world,
        resources = start.session.resources,
        peopleEngine = peopleEngine,
        adultModule = NoOpAdultModule,
    )

    private fun sampleSetup(spacing: StartSpacing): WorldSetup = WorldSetup(
        seed = 424242L,
        startSpacing = spacing,
        tribes = listOf(
            TribeSetup(
                name = "Астарі",
                race = TribeRace.FOUR_ARMED,
                sexualFeatures = setOf(SexualFeature.GROUP, SexualFeature.PUBLIC),
                traits = setOf(TribeTrait.RAPID_MUTATION, TribeTrait.HYBRID_FRIENDLY),
                weakness = TribeWeakness.LOW_FERTILITY,
            ),
            TribeSetup(
                name = "Варки",
                race = TribeRace.FURRED,
                sexualFeatures = setOf(SexualFeature.DOMINANCE, SexualFeature.RITUAL),
                traits = setOf(TribeTrait.WARLIKE, TribeTrait.BODY_CULT),
                weakness = TribeWeakness.INTERNAL_SPLITS,
            ),
            TribeSetup(
                name = "Нері",
                race = TribeRace.TAILED,
                sexualFeatures = setOf(SexualFeature.MONOGAMY, SexualFeature.VOYEURISM),
                traits = setOf(TribeTrait.ISOLATIONIST, TribeTrait.TECHNOLOGICAL),
                weakness = TribeWeakness.GENETIC_BOTTLENECK,
            ),
        ),
    )

    private fun minimumSettlementDistance(start: ConfiguredWorldStart): Int {
        val settlements = start.session.state.settlements
        return settlements.indices.minOf { left ->
            settlements.indices
                .filter { it != left }
                .minOf { right ->
                    abs(settlements[left].x - settlements[right].x) +
                        abs(settlements[left].y - settlements[right].y)
                }
        }
    }
}