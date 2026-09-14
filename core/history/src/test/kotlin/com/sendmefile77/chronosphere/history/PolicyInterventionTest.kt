package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.InstitutionEngine
import com.sendmefile77.chronosphere.civilization.InternalPoliticsEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.civilization.TaxPolicyKind
import com.sendmefile77.chronosphere.civilization.institutionStrength
import com.sendmefile77.chronosphere.civilization.taxPolicyFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyInterventionTest {
    @Test
    fun playerRaisesTaxExactlyOneStepWithoutForeignActor() {
        val initial = InternalPoliticsEngine.reconcile(baseState())
        val result = InterventionEngine().apply(
            initial,
            InterventionCommand("player-tax", InterventionKind.TAX_RAISE, "civ-1", 0.65),
        )

        assertEquals(TaxPolicyKind.HIGH, result.taxPolicyFor("civ-1")?.kind)
        val event = result.recentEvents.last()
        assertEquals("INTERVENTION_TAX_RAISE", event.code)
        assertEquals(listOf("civ-1"), event.actorIds)
    }

    @Test
    fun playerReformStrengthensInstitutionalState() {
        val initial = InstitutionEngine.reconcile(InternalPoliticsEngine.reconcile(baseState()))
        val before = initial.institutionStrength("civ-1")
        val result = InterventionEngine().apply(
            initial,
            InterventionCommand("player-reform", InterventionKind.INSTITUTION_REFORM, "civ-1", 0.65),
        )

        assertTrue(result.institutionStrength("civ-1") > before)
        assertEquals(listOf("civ-1"), result.recentEvents.last().actorIds)
    }

    private fun baseState(): LivingPlanetState = LivingPlanetState(
        worldSeed = 808L,
        tick = 120L,
        civilizations = listOf(
            Civilization("civ-1", "Ардан", 1_200L, 0.61, 0.24, 90.0),
            Civilization("civ-2", "Велор", 1_100L, 0.58, 0.23, 84.0),
        ),
        settlements = listOf(
            Settlement("city-1", "Астра", "civ-1", 2, 2, 1_200L, 600.0, 150.0, 0L),
            Settlement("city-2", "Брен", "civ-2", 20, 20, 1_100L, 550.0, 145.0, 0L),
        ),
    )
}
