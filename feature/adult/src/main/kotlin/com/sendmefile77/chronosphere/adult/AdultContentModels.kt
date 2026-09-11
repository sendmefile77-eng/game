package com.sendmefile77.chronosphere.adult

internal data class AdultEventRule(
    val code: String,
    val intimacy: Double,
    val scandal: Double,
    val fertility: Double,
    val setting: String,
    val mediaKey: String,
    val mediaTags: Set<String>,
    val baseWeight: Double = 1.0,
    val cultureWeights: Map<String, Double> = emptyMap(),
    val numericWeights: Map<String, Double> = emptyMap(),
    val preferredFor: Set<String> = emptySet(),
)

internal data class AdultContentPack(
    val id: String,
    val matchTags: Set<String>,
    val priority: Int,
    val events: List<AdultEventRule>,
    val classicSelection: Boolean = false,
)

internal data class CultureTone(
    val bondScale: Double,
    val scandalScale: Double,
    val fertilityScale: Double,
    val cultureScale: Double,
    val explicitness: String,
    val preferredCodes: Set<String>,
)

internal object AdultCulture {
    val AUSTERE = setOf("puritan", "austere", "conservative", "ascetic")
    val OPEN = setOf("libertine", "open", "hedonist", "fertility_cult")
    val DYNASTIC = setOf("dynastic", "noble", "royal", "courtly")
    val SACRED = setOf("devout", "sacred", "temple", "theocratic")
    val MARTIAL = setOf("martial", "warrior", "honor")

    fun tone(tags: Set<String>): CultureTone {
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

    fun normalizedTags(tags: Set<String>): Set<String> = tags.map { it.lowercase() }.toSet()
}
