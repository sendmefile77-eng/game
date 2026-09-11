package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomicGood
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.economy.TradeRoute

object EconomySnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_ECONOMY_V1"

    fun encode(state: EconomyState): String = buildString {
        appendLine(HEADER)
        appendLine("WORLD\t${state.worldSeed}\t${state.tick}")
        state.civilizations.forEach { economy ->
            appendLine(
                listOf(
                    "ECON",
                    esc(economy.civilizationId),
                    economy.era.name,
                    encodeGoods(economy.stockpiles),
                    encodeGoods(economy.production),
                    encodeGoods(economy.demand),
                    economy.shortageIndex,
                    economy.tradeBalance,
                    economy.grossOutput,
                ).joinToString("\t"),
            )
        }
        state.routes.forEach { route ->
            appendLine(
                listOf(
                    "TRADE",
                    esc(route.id),
                    esc(route.exporterId),
                    esc(route.importerId),
                    route.good.name,
                    route.volume,
                    route.value,
                    route.tick,
                ).joinToString("\t"),
            )
        }
    }

    fun decode(text: String): EconomyState {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported economy save format" }
        val world = lines.getOrNull(1)?.split('\t') ?: error("Missing economy WORLD row")
        require(world.size >= 3 && world[0] == "WORLD") { "Malformed economy WORLD row" }
        val rows = lines.drop(2)
        val civilizations = rows.filter { it.startsWith("ECON\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 9) { "Malformed ECON row" }
            CivilizationEconomy(
                civilizationId = unesc(p[1]),
                era = TechnologyEra.valueOf(p[2]),
                stockpiles = decodeGoods(p[3]),
                production = decodeGoods(p[4]),
                demand = decodeGoods(p[5]),
                shortageIndex = p[6].toDouble(),
                tradeBalance = p[7].toDouble(),
                grossOutput = p[8].toDouble(),
            )
        }
        val routes = rows.filter { it.startsWith("TRADE\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 8) { "Malformed TRADE row" }
            TradeRoute(
                id = unesc(p[1]),
                exporterId = unesc(p[2]),
                importerId = unesc(p[3]),
                good = EconomicGood.valueOf(p[4]),
                volume = p[5].toDouble(),
                value = p[6].toDouble(),
                tick = p[7].toLong(),
            )
        }
        return EconomyState(
            worldSeed = world[1].toLong(),
            tick = world[2].toLong(),
            civilizations = civilizations,
            routes = routes,
        )
    }

    private fun encodeGoods(values: Map<EconomicGood, Double>): String = EconomicGood.entries.joinToString(",") { good ->
        "${good.name}:${values[good] ?: 0.0}"
    }

    private fun decodeGoods(value: String): Map<EconomicGood, Double> {
        val parsed = if (value.isBlank()) emptyMap() else value.split(',').associate { item ->
            val separator = item.indexOf(':')
            require(separator > 0) { "Malformed goods map" }
            EconomicGood.valueOf(item.substring(0, separator)) to item.substring(separator + 1).toDouble()
        }
        return EconomicGood.entries.associateWith { parsed[it] ?: 0.0 }
    }

    private fun esc(value: String): String = value.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")
    private fun unesc(value: String): String = value.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
}
