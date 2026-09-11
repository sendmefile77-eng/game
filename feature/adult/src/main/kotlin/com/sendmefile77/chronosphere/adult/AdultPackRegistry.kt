package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest

internal class AdultPackRegistry(packs: List<AdultContentPack>) {
    val packs: List<AdultContentPack> = AdultPackValidator.validateAll(packs)

    fun pack(id: String): AdultContentPack = packs.first { it.id == id }

    fun selectPack(request: AdultEventRequest, fingerprint: Long): AdultContentPack {
        val tags = AdultCulture.normalizedTags(request.context.cultureTags)
        val numeric = request.context.numericContext
        val scored = packs.map { pack ->
            pack to packScore(pack, tags, numeric)
        }
        val bestScore = scored.maxOf { it.second }
        val tied = scored.filter { it.second == bestScore }.map { it.first }.sortedBy { it.id }
        return tied[AdultFingerprint.index(fingerprint, 29L, tied.size)]
    }

    fun selectEvent(pack: AdultContentPack, request: AdultEventRequest, fingerprint: Long): AdultEventRule {
        if (pack.classicSelection) return pickClassic(pack, request, fingerprint)
        return pickWeighted(pack, request, fingerprint)
    }

    fun eventWeight(event: AdultEventRule, request: AdultEventRequest): Double {
        val tags = AdultCulture.normalizedTags(request.context.cultureTags)
        var weight = event.baseWeight
        for (tag in tags) {
            val bump = event.cultureWeights[tag] ?: 0.0
            weight *= (1.0 + bump).coerceAtLeast(0.0)
        }
        for ((key, coeff) in event.numericWeights) {
            val raw = request.context.numericContext[key] ?: 0.0
            val value = if (raw.isFinite()) raw.coerceIn(-2.0, 2.0) else 0.0
            weight *= (1.0 + coeff * value).coerceAtLeast(0.0)
        }
        return if (weight.isFinite()) weight.coerceAtLeast(0.0) else 0.0
    }

    private fun packScore(
        pack: AdultContentPack,
        tags: Set<String>,
        numeric: Map<String, Double>,
    ): Int {
        var score = if (pack.matchTags.isEmpty()) 1 else 0
        if (pack.matchTags.any { it in tags }) score = pack.priority
        val fertility = numeric["fertility"] ?: numeric["lust"] ?: 0.0
        if (pack.id == PACK_HARDCORE && fertility >= 0.75) score += 1
        val piety = numeric["piety"] ?: 0.0
        if (pack.id == PACK_SACRED && piety >= 0.75) score += 1
        return score
    }

    private fun pickClassic(
        pack: AdultContentPack,
        request: AdultEventRequest,
        fingerprint: Long,
    ): AdultEventRule {
        val tone = AdultCulture.tone(request.context.cultureTags)
        val preferred = pack.events.filter { rule ->
            tone.preferredCodes.isEmpty() || rule.code in tone.preferredCodes
        }
        val pool = preferred.ifEmpty { pack.events }
        return pool[AdultFingerprint.index(fingerprint, 7L, pool.size)]
    }

    private fun pickWeighted(
        pack: AdultContentPack,
        request: AdultEventRequest,
        fingerprint: Long,
    ): AdultEventRule {
        val weighted = pack.events.map { it to eventWeight(it, request) }
        val total = weighted.sumOf { it.second }
        if (total <= 0.0) {
            return pack.events[AdultFingerprint.index(fingerprint, 7L, pack.events.size)]
        }
        val target = AdultFingerprint.unit01(fingerprint, 7L) * total
        var acc = 0.0
        for ((event, weight) in weighted) {
            acc += weight
            if (target < acc) return event
        }
        return weighted.last().first
    }

    companion object {
        const val PACK_CLASSIC = "classic"
        const val PACK_HARDCORE = "hardcore"
        const val PACK_DYNASTIC = "dynastic"
        const val PACK_SACRED = "sacred"
        const val PACK_AUSTERE = "austere"

        fun bundled(): AdultPackRegistry = AdultPackRegistry(BundledAdultPacks.all)
    }
}
