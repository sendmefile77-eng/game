package com.sendmefile77.chronosphere.society

import com.sendmefile77.chronosphere.history.CivilizationFoundation
import com.sendmefile77.chronosphere.history.FoundationKind
import com.sendmefile77.chronosphere.history.HistoricalCommitment
import com.sendmefile77.chronosphere.history.HistoricalConsequence
import com.sendmefile77.chronosphere.history.HistoricalMemoryState
import com.sendmefile77.chronosphere.history.HistoricalProcess
import com.sendmefile77.chronosphere.history.HistoricalProcessKind
import com.sendmefile77.chronosphere.history.HistoricalProcessStage
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PersonRole
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalAdultContextBridgeTest {
    @Test
    fun emitsStableHistoryTagsForAdultContext() {
        val civilizationId = "civ-a"
        val memory = HistoricalMemoryState(
            worldSeed = 7L,
            tick = 120L,
            foundations = listOf(
                CivilizationFoundation(
                    id = "civ-a:exchange",
                    civilizationId = civilizationId,
                    kind = FoundationKind.EXCHANGE,
                    titleUk = "Торгова мережа",
                    benefitUk = "Обмін",
                    costUk = "Залежність",
                    strength = 0.7,
                    originTick = 0L,
                    lastChangedTick = 120L,
                ),
            ),
            processes = listOf(
                HistoricalProcess(
                    id = "war-a",
                    kind = HistoricalProcessKind.WAR,
                    civilizationIds = setOf(civilizationId),
                    titleUk = "Тривала війна",
                    startedTick = 72L,
                    lastUpdatedTick = 120L,
                    stage = HistoricalProcessStage.ACTIVE,
                    intensity = 0.8,
                    sourceEventIds = listOf("war-start"),
                    latestEventCode = "WAR_STARTED",
                ),
            ),
            commitments = listOf(
                HistoricalCommitment(
                    id = "commitment:civ-a:craft",
                    civilizationId = civilizationId,
                    family = "development",
                    choiceId = "century-craft",
                    titleUk = "Ставка на ремесла",
                    benefitUk = "Розвиток",
                    recurringCostUk = "Напруга",
                    originTick = 96L,
                ),
            ),
            consequences = listOf(
                HistoricalConsequence(
                    id = "tail-a",
                    civilizationIds = setOf(civilizationId),
                    originEventId = "war-start",
                    originTick = 72L,
                    titleUk = "Незавершена війна",
                    triggerUk = "Мир або виснаження",
                ),
            ),
        )
        val person = NotablePerson(
            id = "person-a",
            name = "Ari",
            civilizationId = civilizationId,
            settlementId = null,
            dynastyId = null,
            birthTick = 0L,
            role = PersonRole.MERCHANT,
            prestige = 0.82,
            aptitude = 0.7,
        )

        val tags = HistoricalAdultContextBridge.tags(
            baseTags = setOf("era_urban"),
            civilizationId = civilizationId,
            historicalMemory = memory,
            branchId = "branch-2",
            primaryPerson = person,
        )

        assertTrue("foundation:exchange" in tags)
        assertTrue("history_process:war" in tags)
        assertTrue("history_process_stage:active" in tags)
        assertTrue("history_commitment:century-craft" in tags)
        assertTrue("history_policy:century-craft" in tags)
        assertTrue("history_consequence:open" in tags)
        assertTrue("history_branch:branch-2" in tags)
        assertTrue("person_role:merchant" in tags)
        assertTrue("person_status:elite" in tags)
    }
}
