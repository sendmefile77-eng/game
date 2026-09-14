package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.CivilizationTaxPolicy
import com.sendmefile77.chronosphere.civilization.EliteFactionKind
import com.sendmefile77.chronosphere.civilization.EliteFactionState
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.ProvinceState
import com.sendmefile77.chronosphere.civilization.RebellionState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.civilization.TaxPolicyKind
import org.junit.Assert.assertEquals
import org.junit.Test

class InternalPoliticsSnapshotTest {
    @Test
    fun roundTripPreservesTaxesElitesProvincesAndRebellions() {
        val state = LivingPlanetState(
            worldSeed = 99L,
            tick = 240L,
            civilizations = listOf(Civilization("civ-1", "Ардан", 1_200L, 0.44, 0.20, 42.0)),
            settlements = listOf(Settlement("city-1", "Астра", "civ-1", 2, 3, 1_200L, 500.0, 140.0, 0L)),
            taxPolicies = listOf(CivilizationTaxPolicy("civ-1", TaxPolicyKind.HIGH, 216L, 456L)),
            eliteFactions = listOf(
                EliteFactionState("elite:civ-1:merchants", "civ-1", EliteFactionKind.MERCHANTS, 0.38, 0.31, 240L),
            ),
            provinces = listOf(
                ProvinceState("province:city-1", "civ-1", "city-1", 0.26, 0.79, 0.35, 0.75, 240L),
            ),
            rebellions = listOf(
                RebellionState("rebellion-city-1-228", "civ-1", "province:city-1", 228L, 240L, 0.82),
            ),
        )

        assertEquals(state, GameSnapshotV1.decode(GameSnapshotV1.encode(state)))
    }

    @Test
    fun legacyTaxRowWithoutPriorityStillLoads() {
        val state = LivingPlanetState(
            worldSeed = 99L,
            tick = 240L,
            civilizations = listOf(Civilization("civ-1", "Ардан", 1_200L, 0.44, 0.20, 42.0)),
            settlements = listOf(Settlement("city-1", "Астра", "civ-1", 2, 3, 1_200L, 500.0, 140.0, 0L)),
            taxPolicies = listOf(CivilizationTaxPolicy("civ-1", TaxPolicyKind.HIGH, 216L)),
        )
        val modern = GameSnapshotV1.encode(state)
        val legacy = modern.replace("TAX\tciv-1\tHIGH\t216\t0", "TAX\tciv-1\tHIGH\t216")

        val decoded = GameSnapshotV1.decode(legacy)

        assertEquals(0L, decoded.taxPolicies.single().playerPriorityUntilTick)
        assertEquals(state, decoded)
    }
}
