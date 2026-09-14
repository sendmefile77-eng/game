package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.history.CivilizationFoundation
import com.sendmefile77.chronosphere.history.FoundationKind
import com.sendmefile77.chronosphere.history.HistoricalCausalLink
import com.sendmefile77.chronosphere.history.HistoricalCausalRelation
import com.sendmefile77.chronosphere.history.HistoricalCommitment
import com.sendmefile77.chronosphere.history.HistoricalCommitmentStatus
import com.sendmefile77.chronosphere.history.HistoricalConsequence
import com.sendmefile77.chronosphere.history.HistoricalConsequenceStatus
import com.sendmefile77.chronosphere.history.HistoricalLegacy
import com.sendmefile77.chronosphere.history.HistoricalLegacyKind
import com.sendmefile77.chronosphere.history.HistoricalMemoryState
import com.sendmefile77.chronosphere.history.HistoricalProcess
import com.sendmefile77.chronosphere.history.HistoricalProcessKind
import com.sendmefile77.chronosphere.history.HistoricalProcessStage
import java.nio.charset.StandardCharsets
import java.util.Base64

object HistoricalMemorySnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_HISTORICAL_MEMORY_V1"

    fun encode(state: HistoricalMemoryState): String = buildString {
        appendLine(HEADER)
        appendLine(
            listOf(
                "STATE",
                state.worldSeed,
                state.tick,
                state.lastProcessedTick,
                packList(state.processedEventIdsAtLastTick.sorted()),
            ).joinToString("\t"),
        )
        state.foundations.forEach { foundation ->
            appendLine(
                listOf(
                    "FOUNDATION",
                    esc(foundation.id),
                    esc(foundation.civilizationId),
                    foundation.kind.name,
                    esc(foundation.titleUk),
                    esc(foundation.benefitUk),
                    esc(foundation.costUk),
                    foundation.strength,
                    foundation.originTick,
                    foundation.lastChangedTick,
                ).joinToString("\t"),
            )
        }
        state.processes.forEach { process ->
            appendLine(
                listOf(
                    "PROCESS",
                    esc(process.id),
                    process.kind.name,
                    packList(process.civilizationIds.sorted()),
                    esc(process.titleUk),
                    process.startedTick,
                    process.lastUpdatedTick,
                    process.stage.name,
                    process.intensity,
                    packList(process.sourceEventIds),
                    esc(process.latestEventCode),
                    process.resolvedTick?.toString().orEmpty(),
                ).joinToString("\t"),
            )
        }
        state.consequences.forEach { consequence ->
            appendLine(
                listOf(
                    "CONSEQUENCE",
                    esc(consequence.id),
                    packList(consequence.civilizationIds.sorted()),
                    esc(consequence.originEventId),
                    consequence.originTick,
                    esc(consequence.titleUk),
                    esc(consequence.triggerUk),
                    consequence.status.name,
                    consequence.resolvedTick?.toString().orEmpty(),
                ).joinToString("\t"),
            )
        }
        state.commitments.forEach { commitment ->
            appendLine(
                listOf(
                    "COMMITMENT",
                    esc(commitment.id),
                    esc(commitment.civilizationId),
                    esc(commitment.family),
                    esc(commitment.choiceId),
                    esc(commitment.titleUk),
                    esc(commitment.benefitUk),
                    esc(commitment.recurringCostUk),
                    commitment.originTick,
                    commitment.status.name,
                    commitment.endedTick?.toString().orEmpty(),
                ).joinToString("\t"),
            )
        }
        state.causalLinks.forEach { link ->
            appendLine(
                listOf(
                    "CAUSAL",
                    esc(link.id),
                    packList(link.civilizationIds.sorted()),
                    esc(link.causeEventId),
                    esc(link.effectEventId),
                    link.causeTick,
                    link.effectTick,
                    link.relation.name,
                    esc(link.titleUk),
                ).joinToString("\t"),
            )
        }
        state.legacies.forEach { legacy ->
            appendLine(
                listOf(
                    "LEGACY",
                    esc(legacy.id),
                    packList(legacy.civilizationIds.sorted()),
                    legacy.kind.name,
                    esc(legacy.titleUk),
                    legacy.originTick,
                    legacy.lastReinforcedTick,
                    legacy.strength,
                    packList(legacy.sourceEventIds),
                ).joinToString("\t"),
            )
        }
    }

    fun decode(text: String): HistoricalMemoryState {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported historical memory format" }
        val stateRow = lines.firstOrNull { it.startsWith("STATE\t") }?.split('\t')
            ?: error("Missing historical memory STATE row")
        require(stateRow.size >= 5) { "Malformed historical memory STATE row" }
        val worldSeed = stateRow[1].toLong()
        val tick = stateRow[2].toLong()
        val lastProcessedTick = stateRow[3].toLong()
        val processedIds = unpackList(stateRow[4]).toSet()

        val foundations = lines.filter { it.startsWith("FOUNDATION\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 10) { "Malformed FOUNDATION row" }
            CivilizationFoundation(
                id = unesc(p[1]),
                civilizationId = unesc(p[2]),
                kind = FoundationKind.valueOf(p[3]),
                titleUk = unesc(p[4]),
                benefitUk = unesc(p[5]),
                costUk = unesc(p[6]),
                strength = p[7].toDouble(),
                originTick = p[8].toLong(),
                lastChangedTick = p[9].toLong(),
            )
        }
        val processes = lines.filter { it.startsWith("PROCESS\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 12) { "Malformed PROCESS row" }
            HistoricalProcess(
                id = unesc(p[1]),
                kind = HistoricalProcessKind.valueOf(p[2]),
                civilizationIds = unpackList(p[3]).toSet(),
                titleUk = unesc(p[4]),
                startedTick = p[5].toLong(),
                lastUpdatedTick = p[6].toLong(),
                stage = HistoricalProcessStage.valueOf(p[7]),
                intensity = p[8].toDouble(),
                sourceEventIds = unpackList(p[9]),
                latestEventCode = unesc(p[10]),
                resolvedTick = p[11].takeIf { it.isNotBlank() }?.toLong(),
            )
        }
        val consequences = lines.filter { it.startsWith("CONSEQUENCE\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 9) { "Malformed CONSEQUENCE row" }
            HistoricalConsequence(
                id = unesc(p[1]),
                civilizationIds = unpackList(p[2]).toSet(),
                originEventId = unesc(p[3]),
                originTick = p[4].toLong(),
                titleUk = unesc(p[5]),
                triggerUk = unesc(p[6]),
                status = HistoricalConsequenceStatus.valueOf(p[7]),
                resolvedTick = p[8].takeIf { it.isNotBlank() }?.toLong(),
            )
        }
        val commitments = lines.filter { it.startsWith("COMMITMENT\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 11) { "Malformed COMMITMENT row" }
            HistoricalCommitment(
                id = unesc(p[1]),
                civilizationId = unesc(p[2]),
                family = unesc(p[3]),
                choiceId = unesc(p[4]),
                titleUk = unesc(p[5]),
                benefitUk = unesc(p[6]),
                recurringCostUk = unesc(p[7]),
                originTick = p[8].toLong(),
                status = HistoricalCommitmentStatus.valueOf(p[9]),
                endedTick = p[10].takeIf { it.isNotBlank() }?.toLong(),
            )
        }
        val causalLinks = lines.filter { it.startsWith("CAUSAL\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 9) { "Malformed CAUSAL row" }
            HistoricalCausalLink(
                id = unesc(p[1]),
                civilizationIds = unpackList(p[2]).toSet(),
                causeEventId = unesc(p[3]),
                effectEventId = unesc(p[4]),
                causeTick = p[5].toLong(),
                effectTick = p[6].toLong(),
                relation = HistoricalCausalRelation.valueOf(p[7]),
                titleUk = unesc(p[8]),
            )
        }
        val legacies = lines.filter { it.startsWith("LEGACY\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 9) { "Malformed LEGACY row" }
            HistoricalLegacy(
                id = unesc(p[1]),
                civilizationIds = unpackList(p[2]).toSet(),
                kind = HistoricalLegacyKind.valueOf(p[3]),
                titleUk = unesc(p[4]),
                originTick = p[5].toLong(),
                lastReinforcedTick = p[6].toLong(),
                strength = p[7].toDouble(),
                sourceEventIds = unpackList(p[8]),
            )
        }

        return HistoricalMemoryState(
            worldSeed = worldSeed,
            tick = tick,
            foundations = foundations,
            processes = processes,
            consequences = consequences,
            commitments = commitments,
            lastProcessedTick = lastProcessedTick,
            processedEventIdsAtLastTick = processedIds,
            causalLinks = causalLinks,
            legacies = legacies,
        )
    }

    private fun packList(values: Collection<String>): String = values.joinToString(",") { value ->
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun unpackList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split(',').map { item ->
            String(Base64.getUrlDecoder().decode(item), StandardCharsets.UTF_8)
        }
    }

    private fun esc(value: String): String = value
        .replace("%", "%25")
        .replace("\t", "%09")
        .replace("\n", "%0A")

    private fun unesc(value: String): String = value
        .replace("%0A", "\n")
        .replace("%09", "\t")
        .replace("%25", "%")
}
