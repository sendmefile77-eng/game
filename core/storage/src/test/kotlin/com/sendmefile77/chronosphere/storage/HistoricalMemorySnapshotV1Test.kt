package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.history.CivilizationFoundation
import com.sendmefile77.chronosphere.history.FoundationKind
import com.sendmefile77.chronosphere.history.HistoricalCommitment
import com.sendmefile77.chronosphere.history.HistoricalMemoryState
import com.sendmefile77.chronosphere.history.HistoricalProcess
import com.sendmefile77.chronosphere.history.HistoricalProcessKind
import com.sendmefile77.chronosphere.history.HistoricalProcessStage
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoricalMemorySnapshotV1Test {
    @Test
    fun roundTripPreservesCausalMemory() {
        val state = HistoricalMemoryState(
            worldSeed = 77L,
            tick = 360L,
            foundations = listOf(
                CivilizationFoundation(
                    id = "civ-a:production",
                    civilizationId = "civ-a",
                    kind = FoundationKind.PRODUCTION,
                    titleUk = "Металургійна база",
                    benefitUk = "Перевага",
                    costUk = "Ціна",
                    strength = 0.72,
                    originTick = 120L,
                    lastChangedTick = 360L,
                ),
            ),
            processes = listOf(
                HistoricalProcess(
                    id = "process:war:e1",
                    kind = HistoricalProcessKind.WAR,
                    civilizationIds = setOf("civ-a", "civ-b"),
                    titleUk = "Воєнний цикл",
                    startedTick = 240L,
                    lastUpdatedTick = 360L,
                    stage = HistoricalProcessStage.STRAINED,
                    intensity = 0.81,
                    sourceEventIds = listOf("e1", "e2"),
                    latestEventCode = "CITY_CAPTURED",
                ),
            ),
            commitments = listOf(
                HistoricalCommitment(
                    id = "commitment:civ-a:era:era-push:120",
                    civilizationId = "civ-a",
                    family = "era",
                    choiceId = "era-push",
                    titleUk = "Безперервний технологічний ривок",
                    benefitUk = "Розвиток",
                    recurringCostUk = "Напруга",
                    originTick = 120L,
                ),
            ),
            lastProcessedTick = 360L,
            processedEventIdsAtLastTick = setOf("e2"),
        )

        assertEquals(state, HistoricalMemorySnapshotV1.decode(HistoricalMemorySnapshotV1.encode(state)))
    }
}
