package com.sendmefile77.chronosphere.worldgen

data class TileCoord(val x: Int, val y: Int)

class WorldHydrology {
    fun generateRivers(world: WorldMap): Set<TileCoord> {
        val rivers = linkedSetOf<TileCoord>()
        val sources = world.tiles
            .asSequence()
            .filter { it.elevation > 0.68 && it.moisture > 0.52 }
            .filter { sourceChance(world.seed.value, it.x, it.y) < 0.035 }
            .sortedByDescending { it.elevation + it.moisture * 0.25 }
            .take(28)
            .toList()

        for (source in sources) {
            var current = source
            val visited = hashSetOf<TileCoord>()
            for (step in 0 until 90) {
                val coord = TileCoord(current.x, current.y)
                if (!visited.add(coord)) break
                rivers += coord
                if (current.biome == Biome.OCEAN || current.biome == Biome.DEEP_OCEAN) break
                val next = neighbors(world, current.x, current.y)
                    .filter { it.elevation < current.elevation - 0.002 }
                    .minByOrNull { it.elevation - it.moisture * 0.03 }
                    ?: break
                current = next
            }
        }
        return rivers
    }

    private fun neighbors(world: WorldMap, x: Int, y: Int): List<WorldTile> {
        val result = ArrayList<WorldTile>(8)
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until world.width && ny in 0 until world.height) {
                result += world.tiles[ny * world.width + nx]
            }
        }
        return result
    }

    private fun sourceChance(seed: Long, x: Int, y: Int): Double {
        var z = seed xor (x.toLong() * -7046029254386353131L) xor (y.toLong() * -4658895280553007687L)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return z.ushr(11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }
}
