package com.sendmefile77.chronosphere.worldgen

import com.sendmefile77.chronosphere.simulation.WorldSeed
import kotlin.math.abs
import kotlin.math.floor

class WorldGenerator {
    fun generate(seed: WorldSeed, width: Int = 96, height: Int = 54): WorldMap {
        require(width in 16..512 && height in 16..512)
        val tiles = ArrayList<WorldTile>(width * height)
        for (y in 0 until height) {
            val latitude = abs((y.toDouble() / (height - 1)) * 2.0 - 1.0)
            for (x in 0 until width) {
                val elevation = fractal(seed.value, x.toDouble() / width, y.toDouble() / height, 11)
                val moisture = fractal(seed.value, x.toDouble() / width, y.toDouble() / height, 29)
                val climateNoise = fractal(seed.value, x.toDouble() / width, y.toDouble() / height, 47)
                val temperature = ((1.0 - latitude) * 0.92 + climateNoise * 0.18 - elevation * 0.32).coerceIn(0.0, 1.0)
                tiles += WorldTile(x, y, elevation, moisture, temperature, classify(elevation, moisture, temperature))
            }
        }
        return WorldMap(seed, width, height, tiles)
    }

    private fun classify(e: Double, m: Double, t: Double): Biome = when {
        e < 0.30 -> Biome.DEEP_OCEAN
        e < 0.46 -> Biome.OCEAN
        e < 0.50 -> Biome.COAST
        e > 0.88 -> if (t < 0.25) Biome.ICE else Biome.MOUNTAIN
        t < 0.14 -> Biome.ICE
        t < 0.28 -> if (m > 0.50) Biome.TAIGA else Biome.TUNDRA
        m < 0.22 -> Biome.DESERT
        m < 0.38 -> Biome.STEPPE
        m < 0.58 -> Biome.GRASSLAND
        m > 0.76 && t > 0.62 -> Biome.RAINFOREST
        else -> Biome.FOREST
    }

    private fun fractal(seed: Long, x: Double, y: Double, salt: Int): Double {
        var total = 0.0
        var amplitude = 1.0
        var frequency = 2.0
        var norm = 0.0
        repeat(5) {
            total += valueNoise(seed + salt, x * frequency, y * frequency) * amplitude
            norm += amplitude
            amplitude *= 0.5
            frequency *= 2.0
        }
        return (total / norm).coerceIn(0.0, 1.0)
    }

    private fun valueNoise(seed: Long, x: Double, y: Double): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val tx = smooth(x - x0)
        val ty = smooth(y - y0)
        val a = hash01(seed, x0, y0)
        val b = hash01(seed, x0 + 1, y0)
        val c = hash01(seed, x0, y0 + 1)
        val d = hash01(seed, x0 + 1, y0 + 1)
        val ab = lerp(a, b, tx)
        val cd = lerp(c, d, tx)
        return lerp(ab, cd, ty)
    }

    private fun hash01(seed: Long, x: Int, y: Int): Double {
        var z = seed xor (x.toLong() * -7046029254386353131L) xor (y.toLong() * -4658895280553007687L)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return z.ushr(11).toDouble() * (1.0 / (1L shl 53).toDouble())
    }

    private fun smooth(t: Double) = t * t * (3.0 - 2.0 * t)
    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
