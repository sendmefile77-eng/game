package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.history.CivilizationFoundation
import com.sendmefile77.chronosphere.history.FoundationKind
import com.sendmefile77.chronosphere.history.HistoricalCausalLink
import com.sendmefile77.chronosphere.history.HistoricalCausalRelation
import com.sendmefile77.chronosphere.history.HistoricalCommitment
import com.sendmefile77.chronosphere.history.HistoricalLegacy
import com.sendmefile77.chronosphere.history.HistoricalLegacyKind
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
            causalLinks = listOf(
                HistoricalCausalLink(
                    id = "causal:e1->e2",
                    civilizationIds = setOf("civ-a", "civ-b"),
                    causeEventId = "e1",
                    effectEventId = "e2",
                    causeTick = 240L,
                    effectTick = 360L,
                    relation = HistoricalCausalRelation.ESCALATION,
                    titleUk = "Війна призвела до зміни контролю",
                ),
            ),
            legacies = listOf(
                HistoricalLegacy(
                    id = "legacy:territorial_memory:civ-a+civ-b",
                    civilizationIds = setOf("civ-a", "civ-b"),
                    kind = HistoricalLegacyKind.TERRITORIAL_MEMORY,
                    titleUk = "Пам'ять про втрату землі",
                    originTick = 360L,
                    lastReinforcedTick = 360L,
                    strength = 0.68,
                    sourceEventIds = listOf("e2"),
                ),
            ),
        )

        assertEquals(state, HistoricalMemorySnapshotV1.decode(HistoricalMemorySnapshotV1.encode(state)))
    }

    @Test
    fun oldSnapshotWithoutCausalRowsStillLoads() {
        val legacyV1 = listOf(
            "CHRONOSPHERE_HISTORICAL_MEMORY_V1",
            "STATE\t77\t120\t-1\t",
        ).joinToString("\n")

        val decoded = HistoricalMemorySnapshotV1.decode(legacyV1)

        assertEquals(emptyList<HistoricalCausalLink>(), decoded.causalLinks)
        assertEquals(emptyList<HistoricalLegacy>(), decoded.legacies)
    }
}
