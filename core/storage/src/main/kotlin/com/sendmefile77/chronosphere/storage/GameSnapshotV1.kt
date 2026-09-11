package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.AllianceState
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.DiplomaticRelation
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.civilization.WarState

object GameSnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_SAVE_V1"

    fun encode(state: LivingPlanetState): String = buildString {
        appendLine(HEADER)
        appendLine("WORLD\t${state.worldSeed}\t${state.tick}")
        state.civilizations.forEach { civ ->
            appendLine(listOf("CIV", esc(civ.id), esc(civ.name), civ.population, civ.stability, civ.technology, civ.treasury, esc(civ.cultureTags.joinToString("|"))).joinToString("\t"))
        }
        state.settlements.forEach { s ->
            appendLine(listOf("SET", esc(s.id), esc(s.name), esc(s.civilizationId), s.x, s.y, s.population, s.foodStock, s.wealth, s.foundedTick).joinToString("\t"))
        }
        state.relations.forEach { r ->
            appendLine(listOf("REL", esc(r.civilizationA), esc(r.civilizationB), r.value, r.lastUpdatedTick).joinToString("\t"))
        }
        state.wars.forEach { w ->
            appendLine(listOf("WAR", esc(w.id), esc(w.civilizationA), esc(w.civilizationB), w.startedTick, w.casualtiesA, w.casualtiesB, w.scoreA, w.scoreB, w.capturesA, w.capturesB).joinToString("\t"))
        }
        state.alliances.forEach { a ->
            appendLine(listOf("ALLY", esc(a.id), esc(a.civilizationA), esc(a.civilizationB), a.startedTick).joinToString("\t"))
        }
    }

    fun decode(text: String): LivingPlanetState {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported save format" }
        val world = lines.getOrNull(1)?.split('\t') ?: error("Missing WORLD row")
        require(world.size >= 3 && world[0] == "WORLD")
        val rows = lines.drop(2)
        val civilizations = rows.filter { it.startsWith("CIV\t") }.map { row ->
            val p = row.split('\t')
            Civilization(unesc(p[1]), unesc(p[2]), p[3].toLong(), p[4].toDouble(), p[5].toDouble(), p[6].toDouble(), unesc(p.getOrElse(7) { "" }).split('|').filter { it.isNotBlank() }.toSet())
        }
        val settlements = rows.filter { it.startsWith("SET\t") }.map { row ->
            val p = row.split('\t')
            Settlement(unesc(p[1]), unesc(p[2]), unesc(p[3]), p[4].toInt(), p[5].toInt(), p[6].toLong(), p[7].toDouble(), p[8].toDouble(), p[9].toLong())
        }
        val relations = rows.filter { it.startsWith("REL\t") }.map { row ->
            val p = row.split('\t')
            DiplomaticRelation(unesc(p[1]), unesc(p[2]), p[3].toDouble(), p[4].toLong())
        }
        val wars = rows.filter { it.startsWith("WAR\t") }.map { row ->
            val p = row.split('\t')
            WarState(
                id = unesc(p[1]), civilizationA = unesc(p[2]), civilizationB = unesc(p[3]), startedTick = p[4].toLong(),
                casualtiesA = p.getOrElse(5) { "0" }.toLong(), casualtiesB = p.getOrElse(6) { "0" }.toLong(),
                scoreA = p.getOrElse(7) { "0.0" }.toDouble(), scoreB = p.getOrElse(8) { "0.0" }.toDouble(),
                capturesA = p.getOrElse(9) { "0" }.toInt(), capturesB = p.getOrElse(10) { "0" }.toInt(),
            )
        }
        val alliances = rows.filter { it.startsWith("ALLY\t") }.map { row ->
            val p = row.split('\t')
            AllianceState(unesc(p[1]), unesc(p[2]), unesc(p[3]), p[4].toLong())
        }
        return LivingPlanetState(world[1].toLong(), world[2].toLong(), civilizations, settlements, relations = relations, wars = wars, alliances = alliances)
    }

    private fun esc(value: String): String = value.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")
    private fun unesc(value: String): String = value.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
}
