package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult
import com.sendmefile77.chronosphere.adultcontracts.CoreEffectKind
import com.sendmefile77.chronosphere.adultcontracts.CoreEffectProposal
import com.sendmefile77.chronosphere.adultcontracts.MediaCue
import kotlin.math.abs

/**
 * Isolated adult-module engine for contract v1.
 * Pure and deterministic: only [AdultEventRequest] data is used.
 */
class DeterministicAdultModule : AdultModule {
    override val contractVersion: Int = ADULT_CONTRACT_VERSION

    override fun evaluate(request: AdultEventRequest): AdultModuleResult {
        val fingerprint = AdultFingerprint.of(request)
        val event = AdultEventCatalog.select(request, fingerprint)
        return AdultModuleResult(
            requestId = request.requestId,
            eventCode = event.code,
            effects = event.effects(request, fingerprint),
            mediaCue = event.mediaCue(request),
        )
    }
}

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
            hash = mixString(hash, key)
            hash = mix(hash, request.context.numericContext.getValue(key).toRawBits())
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

    private fun sanitize(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return value.coerceIn(-1.0, 1.0)
    }

    private fun mix(seed: Long, value: Long): Long {
        var z = seed xor value
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293111396305L
        return z xor (z ushr 31)
    }

    private fun mixString(seed: Long, value: String): Long {
        var hash = mix(seed, value.length.toLong())
        for (char in value) {
            hash = mix(hash, char.code.toLong())
        }
        return hash
    }

    private const val GOLDEN = -7046029254386353131L
    private const val POW2_NEG53 = 1.0 / (1L shl 53)
}

internal data class AdultEventSpec(
    val code: String,
    val intimacy: Double,
    val scandal: Double,
    val fertility: Double,
    val setting: String,
) {
    fun effects(request: AdultEventRequest, fingerprint: Long): List<CoreEffectProposal> {
        val primary = request.participants.first().entityId
        val partner = request.participants.getOrNull(1)?.entityId ?: primary
        val tone = cultureTone(request.context.cultureTags)
        val ageBias = request.participants.map { it.ageYears }.average().let { age ->
            ((age - 30.0) / 80.0).coerceIn(-0.2, 0.2)
        }
        val proposals = mutableListOf(
            proposal(
                kind = CoreEffectKind.RELATIONSHIP,
                targetId = primary,
                magnitude = AdultFingerprint.bounded(fingerprint, 11L, intimacy * tone.bondScale) + ageBias * 0.1,
                reasonCode = "REL_$code",
            ),
            proposal(
                kind = CoreEffectKind.RELATIONSHIP,
                targetId = partner,
                magnitude = AdultFingerprint.bounded(fingerprint, 13L, intimacy * tone.bondScale),
                reasonCode = "REL_$code",
            ),
            proposal(
                kind = CoreEffectKind.REPUTATION,
                targetId = primary,
                magnitude = AdultFingerprint.bounded(fingerprint, 17L, scandal * tone.scandalScale),
                reasonCode = "REP_$code",
            ),
        )
        if (abs(fertility) > 0.0) {
            proposals += proposal(
                kind = CoreEffectKind.DEMOGRAPHY,
                targetId = primary,
                magnitude = AdultFingerprint.bounded(fingerprint, 19L, fertility * tone.fertilityScale),
                reasonCode = "DEM_$code",
            )
        }
        val cultureTarget = request.context.cultureTags.sorted().firstOrNull()?.lowercase() ?: "world"
        proposals += proposal(
            kind = CoreEffectKind.CULTURE,
            targetId = cultureTarget,
            magnitude = AdultFingerprint.bounded(fingerprint, 23L, 0.35 * tone.cultureScale),
            reasonCode = "CUL_$code",
        )
        return proposals
    }

    fun mediaCue(request: AdultEventRequest): MediaCue {
        val tone = cultureTone(request.context.cultureTags)
        val cultures = request.context.cultureTags.map { it.lowercase() }.sorted()
        val tags = buildSet {
            add(code.lowercase())
            add(setting)
            add(tone.explicitness)
            addAll(cultures.take(4))
            add("participants_${request.participants.size.coerceAtMost(8)}")
        }
        return MediaCue(
            assetKey = "adult://scene/${code.lowercase()}/$setting",
            tags = tags,
        )
    }

    private fun proposal(
        kind: CoreEffectKind,
        targetId: String,
        magnitude: Double,
        reasonCode: String,
    ): CoreEffectProposal {
        val finite = if (magnitude.isNaN() || magnitude.isInfinite()) 0.0 else magnitude
        return CoreEffectProposal(
            kind = kind,
            targetId = targetId.ifBlank { "unknown" },
            magnitude = finite.coerceIn(-1.0, 1.0),
            reasonCode = reasonCode,
        )
    }
}

internal object AdultEventCatalog {
    private val events = listOf(
        AdultEventSpec("COURTSHIP", intimacy = 0.45, scandal = 0.15, fertility = 0.10, setting = "garden"),
        AdultEventSpec("UNION", intimacy = 0.70, scandal = 0.10, fertility = 0.55, setting = "chamber"),
        AdultEventSpec("AFFAIR", intimacy = 0.65, scandal = 0.70, fertility = 0.25, setting = "hidden-room"),
        AdultEventSpec("SCANDAL", intimacy = 0.25, scandal = 0.85, fertility = 0.05, setting = "court"),
        AdultEventSpec("DYNASTIC_BOND", intimacy = 0.50, scandal = 0.20, fertility = 0.60, setting = "palace"),
        AdultEventSpec("FERTILITY_RITE", intimacy = 0.60, scandal = 0.30, fertility = 0.80, setting = "shrine"),
        AdultEventSpec("PATRONAGE_LIAISON", intimacy = 0.40, scandal = 0.45, fertility = 0.15, setting = "salon"),
        AdultEventSpec("CONCUBINAGE", intimacy = 0.55, scandal = 0.50, fertility = 0.45, setting = "annex"),
        AdultEventSpec("SACRED_UNION", intimacy = 0.58, scandal = 0.12, fertility = 0.40, setting = "temple"),
        AdultEventSpec("TABOO_BREAK", intimacy = 0.48, scandal = 0.90, fertility = 0.20, setting = "threshold"),
    )

    fun select(request: AdultEventRequest, fingerprint: Long): AdultEventSpec {
        val tone = cultureTone(request.context.cultureTags)
        val preferred = events.filter { spec -> tone.preferredCodes.isEmpty() || spec.code in tone.preferredCodes }
        val pool = preferred.ifEmpty { events }
        return pool[AdultFingerprint.index(fingerprint, 7L, pool.size)]
    }
}

internal data class CultureTone(
    val bondScale: Double,
    val scandalScale: Double,
    val fertilityScale: Double,
    val cultureScale: Double,
    val explicitness: String,
    val preferredCodes: Set<String>,
)

internal fun cultureTone(tags: Set<String>): CultureTone {
    val normalized = tags.map { it.lowercase() }.toSet()
    val austere = normalized.any { it in AUSTERE }
    val open = normalized.any { it in OPEN }
    val dynastic = normalized.any { it in DYNASTIC }
    val sacred = normalized.any { it in SACRED }
    val martial = normalized.any { it in MARTIAL }
    return CultureTone(
        bondScale = when {
            open -> 0.90
            austere -> 0.55
            else -> 0.75
        },
        scandalScale = when {
            austere -> 1.00
            martial -> 0.85
            open -> 0.45
            else -> 0.70
        },
        fertilityScale = when {
            dynastic || open -> 0.95
            austere -> 0.40
            else -> 0.70
        },
        cultureScale = when {
            sacred || austere -> 0.80
            else -> 0.55
        },
        explicitness = when {
            austere -> "implied"
            open -> "explicit"
            else -> "intimate"
        },
        preferredCodes = buildSet {
            if (dynastic) addAll(listOf("DYNASTIC_BOND", "UNION", "CONCUBINAGE"))
            if (sacred) addAll(listOf("SACRED_UNION", "FERTILITY_RITE"))
            if (open) addAll(listOf("AFFAIR", "FERTILITY_RITE", "UNION"))
            if (austere) addAll(listOf("COURTSHIP", "SCANDAL", "TABOO_BREAK"))
        },
    )
}

private val AUSTERE = setOf("puritan", "austere", "conservative", "ascetic")
private val OPEN = setOf("libertine", "open", "hedonist", "fertility_cult")
private val DYNASTIC = setOf("dynastic", "noble", "royal", "courtly")
private val SACRED = setOf("devout", "sacred", "temple", "theocratic")
private val MARTIAL = setOf("martial", "warrior", "honor")
