package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest

internal object SocialContextKeys {
    const val PRIVACY = "privacy"
    const val BODY_OPENNESS = "body_openness"
    const val MONOGAMY = "monogamy"
    const val JEALOUSY = "jealousy"
    const val FERTILITY = "fertility"
    const val PIETY = "piety"
    const val STATUS = "status"
    const val TENSION = "tension"
    const val LUST = "lust"
    const val TECHNOLOGY = "technology"
    const val WEALTH = "wealth"
    const val SCARCITY = "scarcity"
    const val URBANIZATION = "urbanization"
    const val TRADE_OPENNESS = "trade_openness"
    const val WAR_PRESSURE = "war_pressure"
    const val SOCIAL_TENSION = "social_tension"
}

internal object EraTags {
    const val TRIBAL = "era_tribal"
    const val AGRARIAN = "era_agrarian"
    const val URBAN = "era_urban"
    const val METALLURGIC = "era_metallurgic"
    const val MEDIEVAL = "era_medieval"
    const val EARLY_INDUSTRIAL = "era_early_industrial"
    const val INDUSTRIAL = "era_industrial"
    const val ELECTRIC = "era_electric"
    const val INFORMATION = "era_information"
    const val SPACEFARING = "era_spacefaring"

    val ALL = setOf(
        TRIBAL, AGRARIAN, URBAN, METALLURGIC, MEDIEVAL,
        EARLY_INDUSTRIAL, INDUSTRIAL, ELECTRIC, INFORMATION, SPACEFARING,
    )
    val PRE_INDUSTRIAL = setOf(TRIBAL, AGRARIAN, URBAN, METALLURGIC, MEDIEVAL)
    val INDUSTRIAL_PLUS = setOf(EARLY_INDUSTRIAL, INDUSTRIAL, ELECTRIC, INFORMATION, SPACEFARING)
}

internal object AdultContextSignals {
    fun finite(numeric: Map<String, Double>, key: String): Double? {
        val raw = numeric[key] ?: return null
        return if (raw.isFinite()) raw.coerceIn(-2.0, 2.0) else null
    }

    fun eraTags(tags: Set<String>): Set<String> =
        AdultCulture.normalizedTags(tags).filter { it in EraTags.ALL }.toSet()

    fun matchesEraRequirement(requiredEras: Set<String>, forbiddenEras: Set<String>, tags: Set<String>): Boolean {
        val eras = eraTags(tags)
        val required = requiredEras.map { it.lowercase() }.toSet()
        val forbidden = forbiddenEras.map { it.lowercase() }.toSet()
        if (forbidden.any { it in eras }) return false
        if (required.isEmpty()) return true
        return eras.any { it in required }
    }

    fun weightMultiplier(
        request: AdultEventRequest,
        eraWeights: Map<String, Double>,
        numericWeights: Map<String, Double>,
    ): Double {
        var weight = 1.0
        val eras = eraTags(request.context.cultureTags)
        for (era in eras) {
            val bump = eraWeights[era] ?: 0.0
            if (bump.isFinite()) weight *= (1.0 + bump).coerceAtLeast(0.0)
        }
        for ((key, coeff) in numericWeights) {
            if (!coeff.isFinite()) continue
            val value = finite(request.context.numericContext, key) ?: continue
            weight *= (1.0 + coeff * value).coerceAtLeast(0.0)
        }
        return if (weight.isFinite()) weight.coerceAtLeast(0.0) else 1.0
    }

    val DEFAULT_EVENT_ERA_WEIGHTS: Map<String, Map<String, Double>> = mapOf(
        "COURTSHIP" to mapOf(EraTags.TRIBAL to 0.25, EraTags.AGRARIAN to 0.15),
        "PATRONAGE_LIAISON" to mapOf(EraTags.URBAN to 0.25, EraTags.EARLY_INDUSTRIAL to 0.2, EraTags.INFORMATION to 0.15),
        "PUBLIC_SEX" to mapOf(EraTags.URBAN to 0.35, EraTags.INFORMATION to 0.2),
        "POWER_FUCK" to mapOf(EraTags.TRIBAL to 0.25, EraTags.MEDIEVAL to 0.2, EraTags.METALLURGIC to 0.15),
        "HAREM_SERVICE" to mapOf(EraTags.AGRARIAN to 0.15, EraTags.MEDIEVAL to 0.25),
        "CUM_RITE" to mapOf(EraTags.TRIBAL to 0.2, EraTags.AGRARIAN to 0.2),
    )

    val DEFAULT_EVENT_NUMERIC_WEIGHTS: Map<String, Map<String, Double>> = mapOf(
        "POWER_FUCK" to mapOf(SocialContextKeys.WAR_PRESSURE to 0.55, SocialContextKeys.SOCIAL_TENSION to 0.2),
        "PATRONAGE_LIAISON" to mapOf(SocialContextKeys.WEALTH to 0.45, SocialContextKeys.SCARCITY to -0.35, SocialContextKeys.TRADE_OPENNESS to 0.2),
        "PUBLIC_SEX" to mapOf(SocialContextKeys.URBANIZATION to 0.4, SocialContextKeys.PRIVACY to -0.25),
        "ORGY" to mapOf(SocialContextKeys.WEALTH to 0.2, SocialContextKeys.SCARCITY to -0.25),
        "COURTSHIP" to mapOf(SocialContextKeys.SCARCITY to -0.15),
        "CUM_RITE" to mapOf(SocialContextKeys.FERTILITY to 0.25),
        "ROUGH_COUPLING" to mapOf(SocialContextKeys.WAR_PRESSURE to 0.2, SocialContextKeys.LUST to 0.2),
    )

    fun eventEraWeights(event: AdultEventRule): Map<String, Double> =
        merge(DEFAULT_EVENT_ERA_WEIGHTS[event.code].orEmpty(), event.eraWeights)

    fun eventNumericWeights(event: AdultEventRule): Map<String, Double> =
        merge(DEFAULT_EVENT_NUMERIC_WEIGHTS[event.code].orEmpty(), event.numericWeights)

    private fun merge(base: Map<String, Double>, extra: Map<String, Double>): Map<String, Double> {
        val out = base.toMutableMap()
        extra.forEach { (key, value) -> out[key] = value }
        return out
    }
}
