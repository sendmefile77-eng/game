package com.sendmefile77.chronosphere.adult

internal object BundledAdultPacks {
    val classicEvents: List<AdultEventRule> = listOf(
        rule("COURTSHIP", 0.45, 0.15, 0.10, "garden", tags = setOf("courtship", "clothed"), minP = 1, maxP = 2),
        rule("UNION", 0.70, 0.10, 0.55, "chamber", tags = setOf("sex", "penetration", "marriage-bed"), minP = 2, maxP = 2),
        rule("AFFAIR", 0.65, 0.70, 0.25, "hidden-room", tags = setOf("sex", "adultery", "clandestine"), minP = 2, maxP = 2),
        rule("SCANDAL", 0.25, 0.85, 0.05, "court", tags = setOf("exposure", "rumor"), minP = 1, maxP = 8),
        rule("DYNASTIC_BOND", 0.50, 0.20, 0.60, "palace", tags = setOf("sex", "heir", "marriage-bed"), minP = 2, maxP = 2),
        rule("FERTILITY_RITE", 0.60, 0.30, 0.80, "shrine", tags = setOf("sex", "ritual", "creampie"), minP = 2, maxP = 8),
        rule("PATRONAGE_LIAISON", 0.40, 0.45, 0.15, "salon", tags = setOf("sex", "transaction"), minP = 2, maxP = 2),
        rule("CONCUBINAGE", 0.55, 0.50, 0.45, "annex", tags = setOf("sex", "harem", "kept-lover"), minP = 2, maxP = 4),
        rule("SACRED_UNION", 0.58, 0.12, 0.40, "temple", tags = setOf("sex", "ritual"), minP = 2, maxP = 2),
        rule("TABOO_BREAK", 0.48, 0.90, 0.20, "threshold", tags = setOf("sex", "forbidden"), minP = 2, maxP = 2),
    )

    val all: List<AdultContentPack> = listOf(
        AdultContentPack(id = AdultPackRegistry.PACK_CLASSIC, matchTags = emptySet(), priority = 1, events = classicEvents, classicSelection = true),
        AdultContentPack(
            id = AdultPackRegistry.PACK_HARDCORE,
            matchTags = AdultCulture.OPEN,
            priority = 4,
            events = listOf(
                rule("ROUGH_COUPLING", 0.95, 0.55, 0.50, "bedchamber", tags = setOf("hardcore", "rough-sex", "penetration", "sweat"), base = 1.3, culture = mapOf("libertine" to 0.4, "open" to 0.3, "hedonist" to 0.5), numeric = mapOf("lust" to 0.4, "fertility" to 0.2), minP = 2, maxP = 2),
                rule("ANAL_UNION", 0.90, 0.60, 0.10, "hidden-room", tags = setOf("hardcore", "anal", "penetration"), base = 1.1, culture = mapOf("libertine" to 0.5, "hedonist" to 0.4), numeric = mapOf("lust" to 0.5), minP = 2, maxP = 2, forbidden = AdultCulture.AUSTERE),
                rule("ORGY", 0.85, 0.75, 0.45, "feast-hall", tags = setOf("hardcore", "group", "penetration", "fluids"), base = 1.2, culture = mapOf("hedonist" to 0.6, "open" to 0.4, "fertility_cult" to 0.5), numeric = mapOf("lust" to 0.35), minP = 3, maxP = 12, gates = listOf(NumericGate(SocialContextKeys.BODY_OPENNESS, min = 0.35))),
                rule("PUBLIC_SEX", 0.80, 0.95, 0.30, "plaza", tags = setOf("hardcore", "exhibition", "penetration"), base = 0.9, culture = mapOf("open" to 0.5, "libertine" to 0.4), numeric = mapOf("tension" to 0.2), minP = 2, maxP = 8, forbidden = AdultCulture.AUSTERE, gates = listOf(NumericGate(SocialContextKeys.PRIVACY, max = 0.45), NumericGate(SocialContextKeys.BODY_OPENNESS, min = 0.4))),
                rule("BONDAGE_RITE", 0.88, 0.70, 0.20, "cellar", tags = setOf("hardcore", "bdsm", "restraint", "power"), base = 1.0, culture = mapOf("hedonist" to 0.3, "martial" to 0.4), numeric = mapOf("lust" to 0.3), minP = 2, maxP = 4),
                rule("HAREM_SERVICE", 0.82, 0.50, 0.55, "annex", tags = setOf("hardcore", "harem", "oral", "penetration"), base = 1.1, culture = mapOf("dynastic" to 0.3, "royal" to 0.3, "libertine" to 0.3), numeric = mapOf("fertility" to 0.3), minP = 2, maxP = 8),
                rule("CUM_RITE", 0.86, 0.40, 0.95, "shrine", tags = setOf("hardcore", "creampie", "ritual", "fluids"), base = 1.25, culture = mapOf("fertility_cult" to 0.8, "open" to 0.3), numeric = mapOf("fertility" to 0.6), minP = 2, maxP = 8, gates = listOf(NumericGate(SocialContextKeys.FERTILITY, min = 0.2))),
                rule("POWER_FUCK", 0.92, 0.65, 0.35, "war-tent", tags = setOf("hardcore", "power", "rough-sex", "penetration"), base = 1.0, culture = mapOf("martial" to 0.6, "warrior" to 0.5, "honor" to 0.2), numeric = mapOf("tension" to 0.25), minP = 2, maxP = 2),
            ),
        ),
        AdultContentPack(
            id = AdultPackRegistry.PACK_DYNASTIC,
            matchTags = setOf("dynastic", "noble", "royal"),
            priority = 3,
            events = listOf(
                rule("DYNASTIC_BOND", 0.50, 0.20, 0.60, "palace", tags = setOf("sex", "heir", "marriage-bed"), base = 1.4, culture = mapOf("dynastic" to 0.5, "royal" to 0.4), minP = 2, maxP = 2),
                rule("CONCUBINAGE", 0.55, 0.50, 0.45, "annex", tags = setOf("sex", "harem", "kept-lover"), base = 1.2, culture = mapOf("royal" to 0.4), minP = 2, maxP = 4),
                rule("UNION", 0.70, 0.10, 0.55, "chamber", tags = setOf("sex", "penetration", "marriage-bed"), base = 1.1, minP = 2, maxP = 2),
                rule("HAREM_NIGHT", 0.84, 0.55, 0.70, "seraglio", tags = setOf("hardcore", "harem", "group", "penetration"), base = 1.0, culture = mapOf("royal" to 0.5, "libertine" to 0.3), numeric = mapOf("fertility" to 0.4), minP = 3, maxP = 10),
                rule("SUCCESSION_BED", 0.78, 0.35, 0.90, "palace", tags = setOf("sex", "heir", "creampie"), base = 1.3, numeric = mapOf("fertility" to 0.5), minP = 2, maxP = 2, gates = listOf(NumericGate(SocialContextKeys.STATUS, min = 0.3))),
            ),
        ),
        AdultContentPack(
            id = AdultPackRegistry.PACK_SACRED,
            matchTags = AdultCulture.SACRED,
            priority = 3,
            events = listOf(
                rule("SACRED_UNION", 0.58, 0.12, 0.40, "temple", tags = setOf("sex", "ritual"), base = 1.2, culture = mapOf("devout" to 0.4, "temple" to 0.4), minP = 2, maxP = 2),
                rule("FERTILITY_RITE", 0.60, 0.30, 0.80, "shrine", tags = setOf("sex", "ritual", "creampie"), base = 1.3, culture = mapOf("fertility_cult" to 0.5), minP = 2, maxP = 8),
                rule("ALTAR_COUPLING", 0.83, 0.45, 0.75, "altar", tags = setOf("hardcore", "ritual", "penetration", "public-rite"), base = 1.1, culture = mapOf("theocratic" to 0.4, "sacred" to 0.4), minP = 2, maxP = 6),
                rule("CUM_RITE", 0.86, 0.40, 0.95, "shrine", tags = setOf("hardcore", "creampie", "ritual", "fluids"), base = 1.0, numeric = mapOf("piety" to 0.2, "fertility" to 0.5), minP = 2, maxP = 8, gates = listOf(NumericGate(SocialContextKeys.FERTILITY, min = 0.2))),
            ),
        ),
        AdultContentPack(
            id = AdultPackRegistry.PACK_AUSTERE,
            matchTags = AdultCulture.AUSTERE,
            priority = 3,
            events = listOf(
                rule("COURTSHIP", 0.45, 0.15, 0.10, "garden", tags = setOf("courtship", "clothed"), base = 1.4, minP = 1, maxP = 2),
                rule("SCANDAL", 0.25, 0.85, 0.05, "court", tags = setOf("exposure", "rumor"), base = 1.3, culture = mapOf("puritan" to 0.5, "austere" to 0.4), minP = 1, maxP = 8),
                rule("TABOO_BREAK", 0.48, 0.90, 0.20, "threshold", tags = setOf("sex", "forbidden"), base = 1.1, culture = mapOf("conservative" to 0.3), minP = 2, maxP = 2),
                rule("SECRET_COUPLING", 0.72, 0.88, 0.35, "locked-room", tags = setOf("sex", "clandestine", "penetration"), base = 0.8, numeric = mapOf("tension" to 0.3), minP = 2, maxP = 2, gates = listOf(NumericGate(SocialContextKeys.PRIVACY, min = 0.4))),
            ),
        ),
    )

    private fun rule(
        code: String,
        intimacy: Double,
        scandal: Double,
        fertility: Double,
        setting: String,
        tags: Set<String>,
        base: Double = 1.0,
        culture: Map<String, Double> = emptyMap(),
        numeric: Map<String, Double> = emptyMap(),
        minP: Int = 1,
        maxP: Int = 8,
        required: Set<String> = emptySet(),
        forbidden: Set<String> = emptySet(),
        gates: List<NumericGate> = emptyList(),
    ): AdultEventRule = AdultEventRule(
        code = code,
        intimacy = intimacy,
        scandal = scandal,
        fertility = fertility,
        setting = setting,
        mediaKey = "adult://scene/${code.lowercase()}/$setting",
        mediaTags = tags,
        baseWeight = base,
        cultureWeights = culture,
        numericWeights = numeric,
        minParticipants = minP,
        maxParticipants = maxP,
        requiredTags = required,
        forbiddenTags = forbidden,
        numericGates = gates,
    )
}
