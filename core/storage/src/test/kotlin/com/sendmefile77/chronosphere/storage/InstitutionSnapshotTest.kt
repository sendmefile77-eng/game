package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.InstitutionKind
import com.sendmefile77.chronosphere.civilization.InstitutionState
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import org.junit.Assert.assertEquals
import org.junit.Test

class InstitutionSnapshotTest {
    @Test
    fun roundTripPreservesInstitutionsAndOldSavesMayOmitThem() {
        val state = LivingPlanetState(
            worldSeed = 505L,
            tick = 240L,
            civilizations = listOf(Civilization("civ-1", "Ардан", 1_200L, 0.55, 0.30, 75.0)),
            settlements = listOf(Settlement("city-1", "Астра", "civ-1", 2, 3, 1_200L, 500.0, 140.0, 0L)),
            institutions = listOf(
                InstitutionState("institution:civ-1:council", "civ-1", InstitutionKind.COUNCIL, 0.42, 0.58, 240L),
                InstitutionState("institution:civ-1:administration", "civ-1", InstitutionKind.ADMINISTRATION, 0.51, 0.49, 240L),
            ),
        )

        assertEquals(state, GameSnapshotV1.decode(GameSnapshotV1.encode(state)))

        val legacy = GameSnapshotV1.encode(state.copy(institutions = emptyList()))
        assertEquals(emptyList<InstitutionState>(), GameSnapshotV1.decode(legacy).institutions)
    }
}
