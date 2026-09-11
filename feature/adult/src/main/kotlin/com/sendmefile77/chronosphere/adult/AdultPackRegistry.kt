package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest

internal class AdultPackRegistry(packs: List<AdultContentPack>) {
    val packs: List<AdultContentPack> = AdultPackValidator.validateAll(packs)

    fun pack(id: String): AdultContentPack = packs.first { it.id == id }

    fun selectPack(request: AdultEventRequest, fingerprint: Long): AdultContentPack {
        val tags = AdultCulture.normalizedTags(request.context.cultureTags)
        val numeric = request.context.numericContext
        val scored = packs.map { pack -> pack to packScore(pack, tags, numeric) }
        val bestScore = scored.maxOf { it.second }
        val tied = scored.filter { it.second == bestScore }.map { it.first }.sortedBy { it.id }
        return tied[AdultFingerprint.index(fingerprint, 29L, tied.size)]
    }

    fun selectEvent(pack: AdultContentPack, request: AdultEventRequest, fingerprint: Long): AdultEventRule {
        val eligible = AdultEligibility.eligibleEvents(pack.events, request)
        if (eligible.isEmpty()) return SAFE_CONTEXT_FALLBACK
        if (pack.classicSelection) return pickClassic(eligible, request, fingerprint)
        return pickWeighted(eligible, request, fingerprint)
    }

    fun eventWeight(event: AdultEventRule, request: AdultEventRequest): Double {
        if (!AdultEligibility.isEligible(event, request)) return 0.0
        val tags = AdultCulture.normalizedTags(request.context.cultureTags)
        var weight = event.baseWeight
        for (tag in tags) {
            val bump = event.cultureWeights[tag] ?: 0.0
            if (bump.isFinite()) weight *= (1.0 + bump).coerceAtLeast(0.0)
        }
        weight *= AdultContextSignals.weightMultiplier(
            request = request,
            eraWeights = AdultContextSignals.eventEraWeights(event),
            numericWeights = AdultContextSignals.eventNumericWeights(event),
        )
        return if (weight.isFinite()) weight.coerceAtLeast(0.0) else 0.0
    }

    private fun packScore(pack: AdultContentPack, tags: Set<String>, numeric: Map<String, Double>): Int {
        var score = if (pack.matchTags.isEmpty()) 1 else 0
        if (pack.matchTags.any { it in tags }) score = pack.priority
        val fertility = AdultContextSignals.finite(numeric, SocialContextKeys.FERTILITY)
            ?: AdultContextSignals.finite(numeric, SocialContextKeys.LUST) ?: 0.0
        if (pack.id == PACK_HARDCORE && fertility >= 0.75) score += 1
        val piety = AdultContextSignals.finite(numeric, SocialContextKeys.PIETY) ?: 0.0
        if (pack.id == PACK_SACRED && piety >= 0.75) score += 1
        val war = AdultContextSignals.finite(numeric, SocialContextKeys.WAR_PRESSURE) ?: 0.0
        if (pack.id == PACK_HARDCORE && war >= 0.75) score += 1
        val wealth = AdultContextSignals.finite(numeric, SocialContextKeys.WEALTH) ?: 0.0
        if (pack.id == PACK_DYNASTIC && wealth >= 0.75) score += 1
        return score
    }

    private fun pickClassic(eligible: List<AdultEventRule>, request: AdultEventRequest, fingerprint: Long): AdultEventRule {
        val tone = AdultCulture.tone(request.context.cultureTags)
        val preferred = eligible.filter { rule -> tone.preferredCodes.isEmpty() || rule.code in tone.preferredCodes }
        val pool = preferred.ifEmpty { eligible }
        return pool[AdultFingerprint.index(fingerprint, 7L, pool.size)]
    }

    private fun pickWeighted(eligible: List<AdultEventRule>, request: AdultEventRequest, fingerprint: Long): AdultEventRule {
        val weighted = eligible.map { it to eventWeight(it, request) }
        val total = weighted.sumOf { it.second }
        if (total <= 0.0) return SAFE_CONTEXT_FALLBACK
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
