package com.sendmefile77.chronosphere.horde

/**
 * Local Dream is running WAI Illustrious (or a DMD2 merge of it), not a Horde worker.
 * Long documentary prompts collapse; this rewrite keeps the act AND a visible era set.
 * Adult/portrait frames lead with the person (human or chimera) so a tent or
 * domestication tag cannot become an empty-room or animal picture.
 */
internal object LocalDreamIllustriousPrompt {
    private const val QUALITY =
        "masterpiece, best quality, absurdres, rating_explicit, uncensored, nude"

    private const val NEGATIVE =
        "lowres, worst quality, bad anatomy, extra limbs, extra fingers, text, watermark, " +
            "clothed, dress, panties, bra, standing idle, standing side by side, " +
            "portrait, cowboy shot, kissing, kiss, closed mouth, 3d, realistic photo, child, loli, " +
            "modern bedroom, drywall, tiled bathroom, porcelain toilet, smartphone, neon lights, " +
            "skyscraper, marble palace, greek columns, office, hospital, empty white background, " +
            "dog, puppy, wolf as subject, livestock, animal only, no humans, empty room, vacant tent, bestiality"

    private const val SAFE_NEGATIVE =
        "lowres, worst quality, bad anatomy, extra limbs, extra fingers, text, watermark, duplicate person, " +
            "floating head, disconnected body, 3d, plastic doll, child, loli, shota, nudity, explicit sex, " +
            "anachronistic props, unexplained modern objects, neon cyberpunk, empty white background, " +
            "dog, puppy, animal only, no humans, empty room, vacant tent"

    fun apply(request: HordeImageRequest): HordeImageRequest {
        val source = request.positivePrompt.lowercase()
        val action = request.cacheKey.startsWith("horde-adult-action-") || looksLikeAct(source)
        val girls = countWomen(source)
        val men = countMen(source)
        val people = peopleTag(girls, men, action, request.nsfw)
        val act = actTags(source)
        val era = eraScene(source)
        val safeRequest = !action && !request.nsfw
        val portrait = request.cacheKey.startsWith("horde-resolved-scene") ||
            request.cacheKey.startsWith("horde-adult-character")
        val rawMaterial = if (safeRequest) LocalDreamMaterialCueBridge.fragment(source) else ""
        val safeMaterial = if (portrait) HordeAdultSubjectGuard.stripAnimalSubject(rawMaterial) else rawMaterial
        val adultCue = if (!safeRequest) {
            HordeAdultSubjectGuard.sanitize(LocalDreamAdultCueBridge.fragment(source))
        } else {
            ""
        }
        val identity = HordeAdultSubjectGuard.identityFragment(source)
        val identityBit = if (identity.isNotBlank()) ", $identity" else ""
        val chimeric = HordeAdultSubjectGuard.looksChimeric(source)
        val humanLock = if (!safeRequest || portrait) ", ${HordeAdultSubjectGuard.PERSON_LOCK}" else ""
        val chimeraLock = if ((!safeRequest || portrait) && chimeric) ", ${HordeAdultSubjectGuard.CHIMERA_LOCK}" else ""
        val identityLock = if (!safeRequest || portrait) ", ${HordeAdultSubjectGuard.IDENTITY_LOCK}" else ""
        val eroticLock = if (!safeRequest) ", ${HordeAdultSubjectGuard.EROTIC_LOCK}" else ""
        val eraAsBackground = if (!safeRequest || portrait) {
            era.removePrefix("wide shot, ").takeIf { it.isNotBlank() }?.let { "background $it" }.orEmpty()
        } else {
            era
        }
        val eraPrefixSafe = if (eraAsBackground.isBlank()) "" else "$eraAsBackground, "
        val positiveRaw = if (action && act.isNotBlank()) {
            "$QUALITY, $people$identityBit, $act, $eraPrefixSafe$adultCue$humanLock$chimeraLock$identityLock$eroticLock"
        } else if (request.nsfw) {
            "$QUALITY, $people$identityBit, standing, nipples, pussy, navel, full body looking at viewer, $eraPrefixSafe$adultCue$humanLock$chimeraLock$identityLock$eroticLock"
        } else {
            "masterpiece, best quality, $people$identityBit, fully clothed, $eraPrefixSafe$safeMaterial$humanLock$chimeraLock"
        }
        val positive = HordeAdultSubjectGuard.sanitize(positiveRaw)
        val negative = if (safeRequest) {
            SAFE_NEGATIVE
        } else {
            buildString {
                append(NEGATIVE)
                if (!chimeric) append(", furry, anthro")
            }
        }
        val safeCacheSuffix = if (safeRequest) "|material-v2" else ""
        return request.copy(
            cacheKey = "${request.cacheKey}|ld-illust-v5$safeCacheSuffix",
            positivePrompt = positive,
            negativePrompt = negative,
            referenceCacheKey = if (action || request.nsfw) null else request.referenceCacheKey,
            saveResultAsReference = request.saveResultAsReference && !request.nsfw && !action,
            referenceDenoisingStrength = if (action) 0.92 else request.referenceDenoisingStrength,
        )
    }

    private fun looksLikeAct(source: String): Boolean = listOf(
        "footjob", "oral sex", "blowjob", "cunnilingus", "vaginal sex", "anal sex",
        "bukkake", "masturbation", "bdsm", "futanari",
    ).any(source::contains)

    private fun countWomen(source: String): Int = when {
        source.contains("two nude adult women") || source.contains("2girls") -> 2
        source.contains("adult woman") || source.contains("1girl") -> 1
        else -> 0
    }

    private fun countMen(source: String): Int = when {
        source.contains("bukkake") || source.contains("several erect adult penises") -> 3
        source.contains("adult man") || source.contains("1boy") -> 1
        else -> 0
    }

    private fun peopleTag(girls: Int, men: Int, action: Boolean, nsfw: Boolean): String = when {
        girls >= 2 && men <= 0 -> "2girls"
        girls >= 1 && men >= 3 -> "1girl, multiple boys"
        girls >= 1 && men >= 1 -> "1girl, 1boy"
        girls >= 1 -> "1girl"
        men >= 1 -> "1boy"
        action || nsfw -> "1girl"
        else -> "1girl"
    }

    private fun actTags(source: String): String = when {
        source.contains("footjob") ->
            "footjob, soles, toes, pussy, clitoris, legs up, looking at viewer"
        source.contains("bukkake") ->
            "bukkake, facial, cum on face, cum on breasts, penis, kneeling, open mouth"
        source.contains("futanari") ->
            "futanari, penis, ejaculation, cum, breasts, orgasm"
        source.contains("masturbation") ->
            if (source.contains("clitoris") || source.contains("pussy") || source.contains("vulva")) {
                "masturbation, female masturbation, pussy, fingering, orgasm"
            } else {
                "masturbation, penis, ejaculation"
            }
        source.contains("bdsm") ->
            "bdsm, bondage, collar, rope, nude, pussy"
        source.contains("anal") ->
            "anal, anal sex, from behind, ass, penis, sex"
        source.contains("oral") || source.contains("blowjob") || source.contains("cunnilingus") ->
            if (source.contains("cunnilingus") || source.contains("vulva")) {
                "cunnilingus, pussy, oral"
            } else {
                "blowjob, oral, penis, kneeling"
            }
        source.contains("vaginal") ->
            "sex, vaginal, penis, pussy, missionary"
        else -> ""
    }

    private fun eraScene(source: String): String = when {
        matches(source, "prehistoric", "tribal", "stone-age", "stone age", "hide tent", "hide tents", "reed hut", "ochre", "hearth") ->
            "wide shot, prehistoric tribal camp, hide tent interior, packed earth floor, stacked furs, open hearth fire, woodsmoke, ochre body paint, bone charms, animal hides, stone tools"
        matches(source, "agrarian", "thatch", "cottage", "grain") ->
            "wide shot, agrarian village, thatch cottage, clay walls, straw pallet, oil lamp, grain baskets"
        matches(source, "early urban", "mudbrick", "market") ->
            "wide shot, early city street, mudbrick houses, timber beams, clay lamps, market cloth"
        matches(source, "bronze", "iron tools", "furnace", "metalworking") ->
            "wide shot, metal-age workshop dwelling, furnace glow, hammered bronze, soot on timber"
        matches(source, "medieval", "timber-framed", "rope bed", "candle") ->
            "wide shot, medieval timber chamber, rope bed, wool blankets, heavy shutters, candlelight"
        matches(source, "early industrial", "industrial", "tenement", "gaslight", "soot") ->
            "wide shot, industrial tenement, brick walls, iron bed, gaslight, factory smoke outside"
        matches(source, "electric lighting", "bakelite") ->
            "wide shot, early electric apartment, wired bulb, bakelite radio, wallpaper"
        matches(source, "information-age", "contemporary apartment") ->
            "wide shot, contemporary apartment, current furniture, city window"
        matches(source, "spacefaring", "spacecraft", "viewport") ->
            "wide shot, spacecraft cabin, padded bulkheads, oval viewport, instrument lighting"
        else -> eraTags(source)
    }

    private fun matches(source: String, vararg needles: String): Boolean =
        needles.any { source.contains(it) }

    private fun eraTags(source: String): String {
        val found = ERA_PHRASES.filter { source.contains(it) }.distinct().toMutableList()
        if (found.contains("hide tents")) found.remove("hide tent")
        if (found.contains("hearth fire")) found.remove("hearth")
        if (found.contains("animal hides")) found.remove("animal hide")
        if (found.contains("stone-age")) found.remove("stone age")
        return found.joinToString(", ")
    }

    private val ERA_PHRASES = listOf(
        "prehistoric", "tribal", "stone-age", "stone age",
        "hide tents", "hide tent", "reed hut", "hearth fire", "hearth",
        "packed earth", "animal hides", "animal hide", "furs", "ochre",
        "bone charms", "woven fiber", "mammoth",
        "agrarian", "thatch", "clay", "cottage", "grain",
        "early urban", "mudbrick", "masonry", "market",
        "bronze", "iron tools", "furnace", "metalworking",
        "medieval", "timber-framed", "candle", "rope bed",
        "early industrial", "industrial", "soot", "brick", "gaslight", "tenement",
        "electric lighting", "bakelite", "information-age", "contemporary apartment",
        "spacefaring", "spacecraft", "viewport",
        "galenhaven",
    )
}
