package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRelationship
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.people.RelationshipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultActionPlannerTest {
    @Test
    fun minorsNeverReceiveAnActionPlan() {
        val child = person("child-a", birthTick = 300L)
        val adult = person("adult-b", birthTick = 0L)
        assertNull(
            AdultActionPlanner.plan(
                person = child,
                tick = 360L,
                people = people(child, adult),
                sequence = 1,
            ),
        )
    }

    @Test
    fun sequenceZeroDoesNotCreateAnAction() {
        val adult = person("adult-a", birthTick = 0L)
        assertNull(
            AdultActionPlanner.plan(
                person = adult,
                tick = 360L,
                people = people(adult),
                sequence = 0,
            ),
        )
    }

    @Test
    fun successiveSequencesChangeActionType() {
        val types = (1..12).map { sequence ->
            AdultActionPlanner.pickType("adult-a", 360L, sequence)
        }.toSet()
        assertTrue(types.size >= 2)
        assertTrue(types.containsAll(setOf(AdultActionType.FOOTJOB, AdultActionType.ORAL)) || types.size >= 3)
    }

    @Test
    fun preferredTypeIsHonoredWhenAnatomyAllows() {
        val adult = person("adult-a", birthTick = 0L)
        val other = person("adult-b", birthTick = 0L)
        val plan = AdultActionPlanner.plan(
            person = adult,
            tick = 360L,
            people = people(adult, other),
            sequence = 1,
            preferredType = AdultActionType.ORAL,
        )
        requireNotNull(plan)
        assertEquals(AdultActionType.ORAL, plan.type)
    }

    @Test
    fun explicitPlayerChoiceIsNotSilentlyReplacedByEraNorms() {
        val adult = person("adult-a", birthTick = 0L)
        val other = person("adult-b", birthTick = 0L)
        val plan = AdultActionPlanner.plan(
            person = adult,
            tick = 360L,
            people = people(adult, other),
            sequence = 1,
            preferredType = AdultActionType.BUKKAKE,
            technologyEra = TechnologyEra.TRIBAL,
        )
        requireNotNull(plan)
        assertEquals(AdultActionType.BUKKAKE, plan.type)
        assertTrue(plan.mood.contains("taboo"))
    }

    @Test
    fun partnerIsARealAdultFromTheSameCivilization() {
        val primary = person("adult-a", birthTick = 0L)
        val lover = person("adult-lover", birthTick = 0L)
        val sibling = person("adult-sib", birthTick = 0L)
        val foreign = person("adult-x", birthTick = 0L, civilizationId = "other")
        val people = people(primary, lover, sibling, foreign).copy(
            relationships = listOf(
                PersonRelationship("rel-lover", primary.id, lover.id, RelationshipKind.LOVER, 0.8, 10L),
                PersonRelationship("rel-sib", primary.id, sibling.id, RelationshipKind.SIBLING, 0.9, 10L),
            ),
        )
        val plan = AdultActionPlanner.plan(primary, 360L, people, sequence = 3)
        requireNotNull(plan)
        assertEquals(primary.id, plan.primary.personId)
        assertEquals(lover.id, plan.partner?.personId)
        assertTrue(plan.partner!!.ageYears >= 18)
    }

    @Test
    fun twoMalesCannotKeepAVaginalAct() {
        assertEquals(
            AdultActionType.ANAL,
            AdultActionPlanner.normalizeType(
                AdultActionType.VAGINAL,
                BiologicalSex.MALE,
                BiologicalSex.MALE,
            ),
        )
        assertEquals(
            AdultActionType.VAGINAL,
            AdultActionPlanner.normalizeType(
                AdultActionType.VAGINAL,
                BiologicalSex.FEMALE,
                BiologicalSex.MALE,
            ),
        )
    }

    @Test
    fun missingPartnerFallsBackToSoloRatherThanInventingAnyone() {
        val only = person("adult-solo", birthTick = 0L)
        val plan = AdultActionPlanner.plan(only, 360L, people(only), sequence = 2)
        requireNotNull(plan)
        assertNull(plan.partner)
        assertTrue(plan.solo)
    }

    @Test
    fun sameInputsStayDeterministic() {
        val primary = person("adult-a", birthTick = 0L)
        val other = person("adult-b", birthTick = 0L)
        val state = people(primary, other)
        val first = AdultActionPlanner.plan(primary, 240L, state, 4)
        val second = AdultActionPlanner.plan(primary, 240L, state, 4)
        assertEquals(first, second)
        val third = AdultActionPlanner.plan(primary, 240L, state, 5)
        assertNotEquals(first?.cacheToken, third?.cacheToken)
    }

    private fun people(vararg persons: NotablePerson): PeopleState = PeopleState(
        worldSeed = 1L,
        tick = 360L,
        persons = persons.toList(),
        dynasties = emptyList(),
        relationships = emptyList(),
        rulerByCivilization = emptyMap(),
        socialProfiles = emptyList(),
    )

    private fun person(
        id: String,
        birthTick: Long,
        civilizationId: String = "civ",
    ): NotablePerson = NotablePerson(
        id = id,
        name = id,
        civilizationId = civilizationId,
        settlementId = null,
        dynastyId = null,
        birthTick = birthTick,
        role = PersonRole.NOTABLE,
        prestige = 0.5,
        aptitude = 0.5,
    )
}
