package com.sendmefile77.chronosphere.worldgen

enum class ResourceKind { TIMBER, STONE, METALS, FERTILE_LAND, FUEL }

data class ResourceDeposit(
    val x: Int,
    val y: Int,
    val kind: ResourceKind,
    val richness: Double,
)

class WorldResourceGenerator {
    fun generate(world: WorldMap): List<ResourceDeposit> {
        val deposits = ArrayList<ResourceDeposit>()
        for (tile in world.tiles) {
            if (tile.biome == Biome.OCEAN || tile.biome == Biome.DEEP_OCEAN || tile.biome == Biome.ICE) continue
            val roll = hash01(world.seed.value + tile.biome.ordinal * 101L, tile.x, tile.y)
            val kind = when {
                tile.biome == Biome.FOREST || tile.biome == Biome.RAINFOREST || tile.biome == Biome.TAIGA -> ResourceKind.TIMBER
                tile.biome == Biome.GRASSLAND || tile.biome == Biome.STEPPE -> ResourceKind.FERTILE_LAND
                tile.elevation > 0.76 -> if (roll > 0.55) ResourceKind.METALS else ResourceKind.STONE
                roll > 0.965 -> ResourceKind.FUEL
                roll > 0.91 -> ResourceKind.METALS
                else -> null
            }
            if (kind != null && (roll > 0.74 || kind == ResourceKind.FERTILE_LAND || kind == ResourceKind.TIMBER)) {
                val richness = (0.35 + hash01(world.seed.value xor 0x5A5A5A5AL, tile.x, tile.y) * 0.65).coerceIn(0.0, 1.0)
                deposits += ResourceDeposit(tile.x, tile.y, kind, richness)
            }
        }
        return deposits
    }

    private fun hash01(seed: Long, x: Int, y: Int): Double {
        var z = seed xor (x.toLong() * -7046029254386353131L) xor (y.toLong() * -4658895280553007687L)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return z.ushr(11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }
}
