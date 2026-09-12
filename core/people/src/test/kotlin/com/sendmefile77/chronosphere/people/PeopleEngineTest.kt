package com.sendmefile77.chronosphere.people

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleEngineTest {
    private val engine = PeopleEngine()

    @Test
    fun initializeIsDeterministicForSameWorld() {
        val world = sampleWorld(0L)
        assertEquals(engine.initialize(world), engine.initialize(world))
    }

    @Test
    fun initializeAtLateTickKeepsNewCharactersAtOrBelowForty() {
        val world = sampleWorld(2_400L)
        val state = engine.initialize(world)
        state.persons.forEach { person ->
            assertTrue(person.ageYearsAt(world.tick) in 0..40)
        }
        assertTrue(state.dynasties.all { it.foundedTick == world.tick })
        assertTrue(state.relationships.all { it.startedTick == world.tick })
    }

    @Test
    fun stableSexAssignmentIsApproximatelyNinetyPercentFemale() {
        val sample = (0 until 1_000).map { BiologicalSex.fromStableKey("person-$it") }
        val femaleShare = sample.count { it == BiologicalSex.FEMALE } / sample.size.toDouble()
        assertTrue(femaleShare in 0.88..0.92)
        assertEquals(
            BiologicalSex.fromStableKey("person-77"),
            BiologicalSex.fromStableKey("person-77"),
        )
    }

    @Test
    fun featuredPeopleAreAdultsNoOlderThanForty() {
        val initialWorld = sampleWorld(0L)
        val initial = engine.initialize(initialWorld)
        val result = engine.advance(initial, sampleWorld(3_600L)).state

        result.civilizationsOrIdsFromTest().forEach { civilizationId ->
            result.featuredPeople(civilizationId, result.tick).forEach { person ->
                assertTrue(person.ageYearsAt(result.tick) in 18..40)
            }
        }
    }

    @Test
    fun longAdvanceKeepsAValidLivingRulerAndBoundedRoster() {
        val initialWorld = sampleWorld(0L)
        val initial = engine.initialize(initialWorld)
        val futureWorld = sampleWorld(3_600L)
        val result = engine.advance(initial, futureWorld)

        futureWorld.civilizations.forEach { civilization ->
            val ruler = result.state.ruler(civilization.id)
            assertNotNull(ruler)
            assertTrue(ruler!!.isAlive)
        }
        assertTrue(result.state.persons.size <= futureWorld.civilizations.size * 80)
        assertTrue(result.state.relationships.all { relation ->
            result.state.persons.any { it.id == relation.personA } && result.state.persons.any { it.id == relation.personB }
        })
    }

    @Test
    fun longAdvanceIsDeterministic() {
        val initialWorld = sampleWorld(0L)
        val initial = engine.initialize(initialWorld)
        val futureWorld = sampleWorld(2_400L)
        assertEquals(engine.advance(initial, futureWorld), engine.advance(initial, futureWorld))
    }

    @Test
    fun cultureProfileRemainsFiniteAndBounded() {
        val initial = engine.initialize(sampleWorld(0L))
        val result = engine.advance(initial, sampleWorld(1_200L)).state
        result.socialProfiles.forEach { profile ->
            listOf(
                profile.privacy,
                profile.bodyOpenness,
                profile.pairBonding,
                profile.jealousy,
                profile.fertilityNorm,
                profile.piety,
                profile.statusHierarchy,
                profile.socialTension,
            ).forEach { value ->
                assertTrue(value.isFinite())
                assertTrue(value in 0.0..1.0)
            }
        }
    }

    private fun PeopleState.civilizationsOrIdsFromTest(): List<String> =
        persons.map { it.civilizationId }.distinct()

    private fun sampleWorld(tick: Long): LivingPlanetState {
        val a = Civilization("civ-a", "Ардан", 2_000L, 0.68, 0.15, 80.0, setOf("coastal", "warm-climate"))
        val b = Civilization("civ-b", "Велор", 1_800L, 0.72, 0.13, 75.0, setOf("highland", "river-and-rain"))
        return LivingPlanetState(
            worldSeed = 424242L,
            tick = tick,
            civilizations = listOf(a, b),
            settlements = listOf(
                Settlement("city-a", "Астра", "civ-a", 3, 4, 2_000L, 1_200.0, 70.0, 0L),
                Settlement("city-b", "Брен", "civ-b", 12, 7, 1_800L, 1_100.0, 65.0, 0L),
            ),
        )
    }
}
