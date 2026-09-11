package com.sendmefile77.chronosphere.worldgen

import com.sendmefile77.chronosphere.simulation.WorldSeed

enum class Biome { DEEP_OCEAN, OCEAN, COAST, DESERT, STEPPE, GRASSLAND, FOREST, RAINFOREST, TAIGA, TUNDRA, MOUNTAIN, ICE }

data class WorldTile(
    val x: Int,
    val y: Int,
    val elevation: Double,
    val moisture: Double,
    val temperature: Double,
    val biome: Biome,
)

data class WorldMap(
    val seed: WorldSeed,
    val width: Int,
    val height: Int,
    val tiles: List<WorldTile>,
) {
    init { require(width > 0 && height > 0 && tiles.size == width * height) }

    val fingerprint: String by lazy {
        var hash = -3750763034362895579L
        tiles.forEach {
            hash = (hash xor it.biome.ordinal.toLong()) * 1099511628211L
            hash = (hash xor (it.elevation * 10_000).toLong()) * 1099511628211L
        }
        java.lang.Long.toUnsignedString(hash, 16)
    }

    val landPercent: Int by lazy {
        val land = tiles.count { it.biome != Biome.DEEP_OCEAN && it.biome != Biome.OCEAN }
        ((land.toDouble() / tiles.size) * 100.0).toInt()
    }
}
