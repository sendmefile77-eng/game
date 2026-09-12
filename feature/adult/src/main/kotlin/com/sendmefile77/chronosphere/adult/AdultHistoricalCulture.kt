package com.sendmefile77.chronosphere.adult

/**
 * Maps historical facts onto adult culture tags and numeric biases.
 * Same process + different foundations/policies/civ seed => different erotic identity.
 * Never writes morphology and never mutates HistoricalMemory.
 */
internal object AdultHistoricalCulture {
    fun expand(rawTags: Set<String>, numeric: Map<String, Double>): Set<String> {
        val tags = AdultCulture.normalizedTags(rawTags)
        val ctx = AdultHistoricalContext.from(
            com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext(
                worldSeed = 0L,
                tick = 0L,
                cultureTags = tags,
                numericContext = numeric,
            ),
        )
        return buildSet {
            addAll(tags)
            addAll(fetishAliases(tags))
            if (!ctx.present) return@buildSet
            addAll(identityTags(ctx, tags))
        }
    }

    fun identityTags(ctx: AdultHistoricalContext, rawTags: Set<String>): Set<String> {
        if (!ctx.present) return emptySet()
        val tags = AdultCulture.normalizedTags(rawTags)
        val lane = identityLane(ctx)
        return buildSet {
            when {
                ctx.warActive && "authority" in ctx.foundations && (ctx.piety ?: 0.0) >= 0.55 ->
                    addAll(listOf("martial", "warrior", "austere"))
                ctx.warActive && "exchange" in ctx.foundations ->
                    addAll(listOf("martial", "open", "hedonist"))
                ctx.warActive && lane % 2 == 0L ->
                    addAll(listOf("martial", "honor"))
                ctx.warActive ->
                    addAll(listOf("martial", "libertine"))
            }
            when {
                ctx.migrationActive && "exchange" in ctx.foundations ->
                    addAll(listOf("open", "libertine"))
                ctx.migrationActive && (ctx.piety ?: 0.0) >= 0.6 ->
                    add("conservative")
                ctx.migrationActive ->
                    add("hedonist")
            }
            if (ctx.shortageActive) {
                add("austere")
                if ((ctx.scarcity ?: 0.0) >= 0.6) add("conservative")
            }
            if (ctx.dynasticActive || "authority" in ctx.foundations) {
                addAll(listOf("dynastic", "courtly"))
            }
            if ("exchange" in ctx.foundations && !ctx.shortageActive) {
                addAll(listOf("open", "libertine"))
            }
            if ("production" in ctx.foundations && ctx.era in EraTags.INDUSTRIAL_PLUS) {
                add("hedonist")
            }
            if (ctx.policies.any { it.contains("puritan") || it.contains("modest") || it.contains("cloister") }) {
                addAll(listOf("puritan", "austere", "conservative"))
            }
            if (ctx.policies.any { it.contains("festival") || it.contains("fertility") || it.contains("open") }) {
                addAll(listOf("open", "fertility_cult"))
            }
            when (ctx.personRole) {
                "ruler" -> addAll(listOf("dynastic", "royal", "courtly"))
                "commander" -> addAll(listOf("martial", "warrior"))
                "cleric" -> addAll(listOf("devout", "sacred", "temple"))
                "merchant" -> addAll(listOf("libertine", "open"))
                "courtesan" -> addAll(listOf("hedonist", "open"))
                "scholar" -> add("courtly")
                "artisan" -> add("open")
            }
            if ((ctx.urbanization ?: 0.0) >= 0.7) add("libertine")
            if ((ctx.bodyOpenness ?: 0.0) >= 0.7) addAll(listOf("open", "hedonist"))
            if ((ctx.privacy ?: 1.0) <= 0.25) add("libertine")
            if ((ctx.piety ?: 0.0) >= 0.8) addAll(listOf("devout", "austere"))
            addAll(tags.filter { it in fetishVocabulary() })
        }
    }

    fun preferredEventCodes(ctx: AdultHistoricalContext): Set<String> = buildSet {
        if (ctx.warActive) addAll(listOf("POWER_FUCK", "ROUGH_COUPLING", "HOMECOMING_LIAISON"))
        if (ctx.dynasticActive || ctx.personRole == "ruler") addAll(listOf("DYNASTIC_BOND", "UNION", "CONCUBINAGE", "PATRONAGE_LIAISON"))
        if (ctx.migrationActive) addAll(listOf("AFFAIR", "COURTSHIP", "UNION"))
        if (ctx.shortageActive) addAll(listOf("COURTSHIP", "CONTEXT_HOLD"))
        if (ctx.personRole == "cleric") addAll(listOf("SACRED_UNION", "FERTILITY_RITE", "CUM_RITE"))
        if (ctx.personRole == "merchant") add("PATRONAGE_LIAISON")
        if ((ctx.urbanization ?: 0.0) >= 0.65) addAll(listOf("PUBLIC_SEX", "ORGY", "AFFAIR"))
        if ((ctx.bodyOpenness ?: 0.0) >= 0.7) addAll(listOf("PUBLIC_SEX", "ORGY"))
    }

    fun eventWeightBump(eventCode: String, ctx: AdultHistoricalContext): Double {
        val preferred = preferredEventCodes(ctx)
        var bump = if (eventCode in preferred) 0.35 else 0.0
        if (ctx.warActive && eventCode in setOf("POWER_FUCK", "ROUGH_COUPLING")) bump += 0.25
        if (ctx.shortageActive && eventCode in setOf("ORGY", "PUBLIC_SEX", "HAREM_SERVICE")) bump -= 0.35
        if (ctx.personRole == "cleric" && eventCode in setOf("PUBLIC_SEX", "ORGY")) bump -= 0.25
        if (ctx.personRole == "ruler" && eventCode in setOf("DYNASTIC_BOND", "PATRONAGE_LIAISON")) bump += 0.2
        return bump
    }

    private fun identityLane(ctx: AdultHistoricalContext): Long {
        var hash = AdultFingerprint.mixString(0x9E3779B97F4A7C15UL.toLong(), ctx.civilizationId ?: "world")
        hash = AdultFingerprint.mixString(hash, ctx.era ?: "")
        hash = AdultFingerprint.mixString(hash, ctx.foundations.sorted().joinToString(","))
        hash = AdultFingerprint.mixString(hash, ctx.policies.sorted().joinToString(","))
        return hash
    }

    private fun fetishAliases(tags: Set<String>): Set<String> = buildSet {
        if ("nudity_culture" in tags) addAll(listOf("open", "libertine"))
        if ("public_sex" in tags) addAll(listOf("open", "libertine"))
        if ("ritual_sex" in tags) addAll(listOf("sacred", "temple"))
        if ("fertility_cult" in tags) addAll(listOf("open", "hedonist"))
        if ("dominance_culture" in tags) addAll(listOf("martial", "warrior"))
        if ("submission_culture" in tags) add("hedonist")
        if ("bondage_culture" in tags) addAll(listOf("hedonist", "martial"))
        if ("group_sex" in tags) addAll(listOf("hedonist", "open"))
        if ("voyeurism_culture" in tags) add("libertine")
        if ("status_bonds" in tags) addAll(listOf("dynastic", "royal"))
        if ("plural_bonding" in tags) addAll(listOf("libertine", "open"))
        if ("warlike" in tags) addAll(listOf("martial", "warrior"))
        if ("body_cult" in tags) add("hedonist")
    }

    private fun fetishVocabulary(): Set<String> = setOf(
        "open", "libertine", "hedonist", "puritan", "austere", "conservative",
        "dynastic", "noble", "royal", "courtly", "devout", "sacred", "temple",
        "theocratic", "martial", "warrior", "honor", "fertility_cult",
    )
}
