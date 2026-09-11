package com.sendmefile77.chronosphere.civilization

import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.WorldMap
import kotlin.math.abs
import kotlin.math.sqrt

class TerritoryResolver {
    fun resolve(world: WorldMap, state: LivingPlanetState): IntArray {
        val owners = IntArray(world.tiles.size) { -1 }
        val civIndex = state.civilizations.mapIndexed { index, civ -> civ.id to index }.toMap()
        val technology = state.civilizations.associate { it.id to it.technology }

        world.tiles.forEachIndexed { index, tile ->
            if (tile.biome == Biome.OCEAN || tile.biome == Biome.DEEP_OCEAN || tile.biome == Biome.ICE) return@forEachIndexed
            var bestScore = 0.0
            var bestOwner = -1
            for (settlement in state.settlements) {
                val distance = abs(tile.x - settlement.x) + abs(tile.y - settlement.y)
                val tech = technology[settlement.civilizationId] ?: 0.0
                val radius = 4.0 + sqrt(settlement.population.coerceAtLeast(1).toDouble()) / 18.0 + tech * 8.0
                val score = radius - distance + settlement.wealth.coerceAtMost(500.0) / 1000.0
                if (score > bestScore) {
                    bestScore = score
                    bestOwner = civIndex[settlement.civilizationId] ?: -1
                }
            }
            owners[index] = bestOwner
        }
        return owners
    }
}
