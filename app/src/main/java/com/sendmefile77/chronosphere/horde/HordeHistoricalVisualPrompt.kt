package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.TechnologyEra

/** Maps historical visual and century-choice tags into concise renderer language. */
internal object HordeHistoricalVisualPrompt {
    fun fragment(tags: Set<String>, technologyEra: TechnologyEra? = null): String {
        fun value(prefix: String): String? = tags.asSequence()
            .filter { it.startsWith(prefix) && it.length > prefix.length }
            .map { it.removePrefix(prefix) }
            .sorted()
            .firstOrNull()

        fun eraRelevant(raw: String): Boolean {
            val slug = HordeHistoricalTagEra.canonical(raw)
            val origin = HordeVisualEraChoiceIndex.eraForSlug(slug) ?: return true
            val era = technologyEra ?: return true
            return origin.ordinal >= (era.ordinal - 1).coerceAtLeast(0)
        }

        data class EraChoice(val family: String, val slug: String)

        val eraChoices = tags.asSequence()
            .filter { it.startsWith("era-choice:") }
            .mapNotNull { tag ->
                val parts = tag.removePrefix("era-choice:").split(':', limit = 2)
                if (parts.size == 2 && parts.all(String::isNotBlank)) EraChoice(parts[0], parts[1]) else null
            }
            .toList()

        val parts = linkedSetOf<String>()

        // Current ways of life replace their previous family choice, so they describe what the
        // civilization actually looks like now even if that tradition began centuries ago.
        eraChoices.asSequence()
            .filterNot { it.family == "breakthrough" }
            .sortedBy { it.family }
            .take(3)
            .forEach { parts += HordeDecisionVisualCue.forSlug(it.slug) }

        // Breakthroughs accumulate forever. Only current/recent-era breakthroughs are foregrounded;
        // otherwise an industrial civilization keeps receiving stone-age props in every portrait.
        eraChoices.asSequence()
            .filter { it.family == "breakthrough" }
            .filter { HordeVisualEraChoiceIndex.shouldEmphasizeBreakthrough(it.slug, technologyEra) }
            .sortedBy { it.slug }
            .take(2)
            .forEach { parts += HordeDecisionVisualCue.forSlug(it.slug) }

        tags.asSequence()
            .filter { it.startsWith("policy:") }
            .map { it.removePrefix("policy:") }
            .sorted()
            .take(2)
            .forEach { parts += HordeDecisionVisualCue.forHistoricalTag("policy:", it) }

        tags.asSequence()
            .filter { it.startsWith("foundation:") }
            .map { it.removePrefix("foundation:") }
            .filter(::eraRelevant)
            .sorted()
            .take(2)
            .forEach { parts += HordeDecisionVisualCue.forHistoricalTag("foundation:", it) }

        tags.asSequence()
            .filter { it.startsWith("hist:") }
            .map { it.removePrefix("hist:") }
            .filter(::eraRelevant)
            .sorted()
            .take(2)
            .forEach { parts += HordeDecisionVisualCue.forHistoricalTag("hist:", it) }

        // Existing presentation/customisation tags stay compatible. This layer does not define or
        // reinterpret adult content; it only preserves descriptors already supplied by that layer.
        value("cloth:")?.let { parts += "clothing/material tradition ${humanize(it)}" }
        value("jewel:")?.let { parts += "jewellery tradition ${humanize(it)}" }
        value("hair:")?.let { parts += "historical hairstyle ${humanize(it)}" }
        value("body-norm:")?.let { parts += "body presentation norm ${humanize(it)}" }
        value("arch:")?.let { parts += "architecture ${humanize(it)}" }
        value("set-bias:")?.let { parts += "preferred setting ${humanize(it)}" }
        value("cosmetic:")?.let { parts += "cosmetic tradition ${humanize(it)}" }
        value("publicness:")?.let { parts += "social visibility ${humanize(it)}" }

        return parts.filter(String::isNotBlank).take(MAX_PARTS).joinToString(", ")
    }

    /**
     * Compatibility name retained for callers. It now understands both era choices and century
     * dilemmas, so a dilemma response can drive its Chronicle image instead of being text-only.
     */
    fun eraChoiceFragment(choiceId: String?): String = HordeDecisionVisualCue.forChoiceId(choiceId)

    /** Version prefix intentionally invalidates old cached images after material cue changes. */
    fun signature(tags: Set<String>): String = buildString {
        append(SIGNATURE_SCHEMA).append('|')
        append(
            tags.asSequence()
                .filter { tag -> PREFIXES.any(tag::startsWith) }
                .sorted()
                .joinToString(";"),
        )
    }

    private fun humanize(value: String): String = value.replace('_', ' ').replace('-', ' ').replace('+', ' ')

    private const val SIGNATURE_SCHEMA = "material-visual-v3"
    private const val MAX_PARTS = 11

    private val PREFIXES = listOf(
        "cloth:", "jewel:", "hair:", "body-norm:", "arch:", "set-bias:",
        "cosmetic:", "publicness:", "hist:", "foundation:", "policy:", "era-choice:", "era:",
    )
}
