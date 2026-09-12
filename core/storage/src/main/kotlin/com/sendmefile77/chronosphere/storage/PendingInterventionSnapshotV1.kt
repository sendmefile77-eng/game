package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.history.PendingInterventionState

object PendingInterventionSnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_PENDING_INTERVENTIONS_V1"

    fun encode(items: List<PendingInterventionState>): String = buildString {
        appendLine(HEADER)
        items.forEach { item ->
            appendLine(
                listOf(
                    "PENDING",
                    esc(item.commandId),
                    esc(item.sourceEventId),
                    esc(item.choiceId),
                    esc(item.choiceLabel),
                    esc(item.effectLabel),
                    esc(item.riskLabel),
                    item.kind.name,
                    esc(item.civilizationId),
                    item.strength,
                    esc(item.targetCivilizationId.orEmpty()),
                ).joinToString("\t"),
            )
        }
    }

    fun decode(text: String): List<PendingInterventionState> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported pending-intervention format" }
        val items = lines.drop(1).map { row ->
            val p = row.split('\t')
            require(p.size >= 11 && p[0] == "PENDING") { "Malformed pending-intervention row" }
            PendingInterventionState(
                commandId = unesc(p[1]),
                sourceEventId = unesc(p[2]),
                choiceId = unesc(p[3]),
                choiceLabel = unesc(p[4]),
                effectLabel = unesc(p[5]),
                riskLabel = unesc(p[6]),
                kind = InterventionKind.valueOf(p[7]),
                civilizationId = unesc(p[8]),
                strength = p[9].toDouble(),
                targetCivilizationId = unesc(p[10]).ifBlank { null },
            )
        }
        require(items.map { it.sourceEventId }.distinct().size == items.size) { "Duplicate pending source event" }
        return items
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
