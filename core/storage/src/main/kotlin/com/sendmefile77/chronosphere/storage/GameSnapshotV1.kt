package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement

object GameSnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_SAVE_V1"

    fun encode(state: LivingPlanetState): String = buildString {
        appendLine(HEADER)
        appendLine("WORLD\t${state.worldSeed}\t${state.tick}")
        state.civilizations.forEach { civ ->
            appendLine(listOf("CIV", esc(civ.id), esc(civ.name), civ.population, civ.stability, civ.technology, civ.treasury, esc(civ.cultureTags.joinToString("|"))).joinToString("\t"))
        }
        state.settlements.forEach { settlement ->
            appendLine(listOf("SET", esc(settlement.id), esc(settlement.name), esc(settlement.civilizationId), settlement.x, settlement.y, settlement.population, settlement.foodStock, settlement.wealth, settlement.foundedTick).joinToString("\t"))
        }
    }

    fun decode(text: String): LivingPlanetState {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported save format" }
        val world = lines.getOrNull(1)?.split('\t') ?: error("Missing WORLD row")
        require(world.size >= 3 && world[0] == "WORLD")
        val civilizations = lines.drop(2).filter { it.startsWith("CIV\t") }.map { row ->
            val p = row.split('\t')
            Civilization(
                id = unesc(p[1]),
                name = unesc(p[2]),
                population = p[3].toLong(),
                stability = p[4].toDouble(),
                technology = p[5].toDouble(),
                treasury = p[6].toDouble(),
                cultureTags = unesc(p.getOrElse(7) { "" }).split('|').filter { it.isNotBlank() }.toSet(),
            )
        }
        val settlements = lines.drop(2).filter { it.startsWith("SET\t") }.map { row ->
            val p = row.split('\t')
            Settlement(
                id = unesc(p[1]),
                name = unesc(p[2]),
                civilizationId = unesc(p[3]),
                x = p[4].toInt(),
                y = p[5].toInt(),
                population = p[6].toLong(),
                foodStock = p[7].toDouble(),
                wealth = p[8].toDouble(),
                foundedTick = p[9].toLong(),
            )
        }
        return LivingPlanetState(
            worldSeed = world[1].toLong(),
            tick = world[2].toLong(),
            civilizations = civilizations,
            settlements = settlements,
        )
    }

    private fun esc(value: String): String = value.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")
    private fun unesc(value: String): String = value.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
}
