package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomicGood
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.economy.TradeRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class EconomySnapshotV1Test {
    @Test
    fun roundTripPreservesEconomyAndTradeRoutes() {
        val goods = EconomicGood.entries.associateWith { 1.5 + it.ordinal }
        val state = EconomyState(
            worldSeed = 77L,
            tick = 360L,
            civilizations = listOf(
                CivilizationEconomy(
                    civilizationId = "civ-a",
                    era = TechnologyEra.METALLURGIC,
                    stockpiles = goods,
                    production = goods.mapValues { it.value + 2.0 },
                    demand = goods.mapValues { it.value + 1.0 },
                    shortageIndex = 0.17,
                    tradeBalance = 12.5,
                    grossOutput = 88.0,
                ),
                CivilizationEconomy(
                    civilizationId = "civ-b",
                    era = TechnologyEra.URBAN,
                    stockpiles = goods.mapValues { it.value * 0.5 },
                    production = goods.mapValues { it.value * 0.8 },
                    demand = goods.mapValues { it.value * 1.1 },
                    shortageIndex = 0.31,
                    tradeBalance = -12.5,
                    grossOutput = 61.0,
                ),
            ),
            routes = listOf(
                TradeRoute("trade-a-b-metal", "civ-a", "civ-b", EconomicGood.METAL, 4.5, 13.95, 360L),
            ),
        )

        assertEquals(state, EconomySnapshotV1.decode(EconomySnapshotV1.encode(state)))
    }
}
