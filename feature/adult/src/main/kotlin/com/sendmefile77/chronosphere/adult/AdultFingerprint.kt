package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest

internal object AdultFingerprint {
    fun of(request: AdultEventRequest): Long {
        var hash = mix(GOLDEN, request.context.worldSeed)
        hash = mix(hash, request.context.tick)
        hash = mixString(hash, request.requestId)
        hash = mix(hash, request.participants.size.toLong())
        for (participant in request.participants) {
            hash = mixString(hash, participant.entityId)
            hash = mix(hash, participant.ageYears.toLong())
        }
        for (tag in request.context.cultureTags.sorted()) {
            hash = mixString(hash, tag.lowercase())
        }
        for (key in request.context.numericContext.keys.sorted()) {
            if (key.isBlank()) continue
            val value = request.context.numericContext[key] ?: continue
            if (!value.isFinite()) continue
            hash = mixString(hash, key)
            hash = mix(hash, value.toRawBits())
        }
        return hash
    }

    fun unit(hash: Long, lane: Long): Double {
        val mixed = mix(hash, lane)
        val unit = (mixed ushr 11).toDouble() * POW2_NEG53
        return ((unit * 2.0) - 1.0).coerceIn(-1.0, 1.0)
    }

    fun bounded(hash: Long, lane: Long, scale: Double): Double {
        val raw = unit(hash, lane) * scale.coerceIn(0.0, 1.0)
        return sanitize(raw)
    }

    fun index(hash: Long, lane: Long, size: Int): Int {
        require(size > 0)
        val mixed = mix(hash, lane)
        val value = mixed ushr 1
        return (value % size.toLong()).toInt()
    }

    fun mix(seed: Long, value: Long): Long {
        var z = seed xor value
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293111396305L
        return z xor (z ushr 31)
    }

    fun mixString(seed: Long, value: String): Long {
        var hash = mix(seed, value.length.toLong())
        for (char in value) {
            hash = mix(hash, char.code.toLong())
        }
        return hash
    }

    fun sanitize(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return value.coerceIn(-1.0, 1.0)
    }

    fun unit01(hash: Long, lane: Long): Double {
        val mixed = mix(hash, lane)
        return (mixed ushr 11).toDouble() * POW2_NEG53
    }

    private const val GOLDEN = -7046029254386353131L
    private const val POW2_NEG53 = 1.0 / (1L shl 53)
}
