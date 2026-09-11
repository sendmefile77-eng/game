package com.sendmefile77.chronosphere.simulation

/** SplitMix64-based RNG with explicit algorithm stability for save/replay determinism. */
class DeterministicRng(seed: WorldSeed) {
    private var state: Long = seed.value

    fun nextLong(): Long {
        state += -7046029254386353131L
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    fun nextDouble(): Double {
        val bits = nextLong().ushr(11)
        return bits.toDouble() * (1.0 / (1L shl 53).toDouble())
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return (nextDouble() * bound).toInt().coerceAtMost(bound - 1)
    }
}
