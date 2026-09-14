package com.sendmefile77.chronosphere.horde

/**
 * Local Dream is running WAI Illustrious (or a DMD2 merge of it), not a Horde worker.
 * Long documentary prompts collapse; this rewrite keeps the selected act AND a visible era set.
 * Adult/portrait frames lead with the person (human or chimera) so a tent or domestication tag
 * cannot become an empty-room or animal picture.
 */
internal object LocalDreamIllustriousPrompt {
    private const val QUALITY =
        "masterpiece, best quality, absurdres, rating_explicit, uncensored, nude"

    private const val NEGATIVE =
        "lowres, worst quality, bad anatomy, extra limbs, extra fingers, text, watermark, " +
            "clothed, dress, panties, bra, standing idle, standing side by side, " +
            "portrait, cowboy shot, kissing, kiss, closed mouth, 3d, realistic photo, child, loli, shota, " +
            "teen, underage, schoolgirl, kawaii, moe, chibi, baby face, childlike face, childlike proportions, " +
            "modern bedroom, drywall, tiled bathroom, porcelain toilet, smartphone, neon lights, " +
            "skyscraper, marble palace, greek columns, office, hospital, empty white background, " +
            "dog, puppy, wolf as subject, livestock, animal only, no humans, empty room, vacant tent, bestiality"

    private const val SAFE_NEGATIVE =
        "lowres, worst quality, bad anatomy, extra limbs, extra fingers, text, watermark, duplicate person, " +
            "floating head, disconnected body, 3d, plastic doll, child, loli, shota, nudity, explicit sex, " +
            "anachronistic props, unexplained modern objects, neon cyberpunk, empty white background, " +
            "dog, puppy, animal only, no humans, empty room, vacant tent"

    private const val ADULT_PRESENTATION =
        "unmistakably adult woman or man, mature adult facial features, adult body proportions, " +
            "confident sensual expression, direct erotic gaze, sexually charged body language"

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
        val matureLock = if (!safeRequest) ", $ADULT_PRESENTATION" else ""
        val eraAsBackground = if (!safeRequest || portrait) {
            era.removePrefix("wide shot, ").takeIf { it.isNotBlank() }?.let { "background $it" }.orEmpty()
        } else {
            era
        }
        val eraPrefixSafe = if (eraAsBackground.isBlank()) "" else "$eraAsBackground, "
        val positiveRaw = if (action && act.isNotBlank()) {
            "$QUALITY, $people$identityBit, $act, selected sex act clearly readable, active consensual adult pose, $eraPrefixSafe$adultCue$humanLock$chimeraLock$identityLock$eroticLock$matureLock"
        } else if (request.nsfw) {
            "$QUALITY, $people$identityBit, provocative full-body nude pose, nipples, pussy or penis according to subject, navel, full body looking at viewer, $eraPrefixSafe$adultCue$humanLock$chimeraLock$identityLock$eroticLock$matureLock"
        } else {
            "masterpiece, best quality, $people$identityBit, fully clothed, $eraPrefixSafe$safeMaterial$humanLock$chimeraLock"
        }
        val positive = HordeAdultSubjectGuard.sanitize(positiveRaw)
        val negative = if (safeRequest) {
            SAFE_NEGATIVE
        } else {
            buildString {
                append(NEGATIVE)
                val selectedActNegative = actionNegative(source)
                if (selectedActNegative.isNotBlank()) append(", ").append(selectedActNegative)
                if (!chimeric) append(", furry, anthro")
            }
        }
        val safeCacheSuffix = if (safeRequest) "|material-v2" else ""
        return request.copy(
            cacheKey = "${request.cacheKey}|ld-illust-v7$safeCacheSuffix",
            positivePrompt = positive,
            negativePrompt = negative,
            referenceCacheKey = if (action || request.nsfw) null else request.referenceCacheKey,
            saveResultAsReference = request.saveResultAsReference && !request.nsfw && !action,
            referenceDenoisingStrength = if (action) 0.92 else request.referenceDenoisingStrength,
        )
    }

    private fun looksLikeAct(source: String): Boolean = listOf(
        "footjob", "handjob", "blowjob", "cunnilingus", "69:", "sixty-nine",
        "vaginal sex", "anal sex", "paizuri", "scissoring", "tribadism",
        "mutual masturbation", "bukkake", "facial:", "creampie", "masturbation",
        "bdsm", "mmf threesome", "ffm threesome", "futanari",
    ).any(source::contains)

    private fun countWomen(source: String): Int = when {
        source.contains("ffm threesome") || source.contains("two adult women and one adult man") -> 2
        source.contains("mmf threesome") || source.contains("one adult woman and two adult men") -> 1
        source.contains("bukkake") -> 1
        source.contains("two nude adult women") || source.contains("two adult women") || source.contains("2girls") -> 2
        source.contains("adult woman") || source.contains("1girl") -> 1
        else -> 0
    }

    private fun countMen(source: String): Int = when {
        source.contains("bukkake") || source.contains("several confirmed adult men") || source.contains("several adult men") -> 3
        source.contains("mmf threesome") || source.contains("one adult woman and two adult men") -> 2
        source.contains("ffm threesome") || source.contains("two adult women and one adult man") -> 1
        source.contains("adult man") || source.contains("1boy") -> 1
        else -> 0
    }

    private fun peopleTag(girls: Int, men: Int, action: Boolean, nsfw: Boolean): String = when {
        girls >= 1 && men >= 3 -> "1girl, adult woman, multiple boys, adult men"
        girls >= 2 && men >= 1 -> "2girls, adult women, 1boy, adult man"
        girls >= 1 && men >= 2 -> "1girl, adult woman, 2boys, adult men"
        girls >= 2 -> "2girls, adult women"
        girls >= 1 && men >= 1 -> "1girl, adult woman, 1boy, adult man"
        girls >= 1 -> "1girl, adult woman"
        men >= 1 -> "1boy, adult man"
        action || nsfw -> "1girl, adult woman"
        else -> "1girl"
    }

    private fun actTags(source: String): String = when {
        source.contains("mmf threesome") ->
            "mmf threesome, 1girl, 2boys, exactly three adults, all three participating, explicit group sex"
        source.contains("ffm threesome") ->
            "ffm threesome, 2girls, 1boy, exactly three adults, all three participating, explicit group sex"
        source.contains("sixty-nine") || source.contains("69:") ->
            "69, sixty nine position, mutual oral sex, both adults giving oral simultaneously"
        source.contains("mutual masturbation") ->
            "mutual masturbation, two adults, hands on genitals, simultaneous manual stimulation"
        source.contains("cunnilingus") ->
            "cunnilingus, mouth on pussy, tongue on vulva, oral sex, hips and face visible"
        source.contains("paizuri") ->
            "paizuri, breast sex, penis between breasts, breasts around penis, explicit contact"
        source.contains("scissoring") || source.contains("tribadism") ->
            "scissoring, tribadism, 2girls, vulva to vulva contact, intertwined legs, hips touching"
        source.contains("handjob") ->
            "handjob, hand stroking penis or genitals, manual stimulation, explicit contact"
        source.contains("footjob") ->
            "footjob, bare feet on genitals, soles, toes, explicit foot stimulation"
        source.contains("bukkake") ->
            "bukkake, multiple adult men, group climax, semen on face and torso, kneeling adult"
        source.contains("facial:") || source.contains("facial finish") ->
            "facial, ejaculation on face, semen on face, receiving adult face centered"
        source.contains("creampie") ->
            "creampie, vaginal sex, internal ejaculation, post climax vaginal contact"
        source.contains("futanari") ->
            "futanari, adult woman, penis, ejaculation, breasts, orgasm"
        source.contains("bdsm") ->
            "bdsm, consensual bondage, collar, rope, adult dominance and submission, nude"
        source.contains("anal sex") || source.startsWith("anal") ->
            "anal sex, anal penetration, from behind, ass, explicit adult sex"
        source.contains("blowjob") || source.contains("oral sex") ->
            "blowjob, mouth on penis, oral sex, kneeling or lying adult"
        source.contains("vaginal sex") || source.contains("vaginal") ->
            "vaginal sex, vaginal penetration, joined hips, explicit adult sex"
        source.contains("masturbation") ->
            "solo masturbation, one adult, own hand on genitals, no partner"
        else -> ""
    }

    private fun actionNegative(source: String): String = when {
        source.contains("mmf threesome") -> "solo, couple only, ffm, only two people"
        source.contains("ffm threesome") -> "solo, couple only, mmf, only two people"
        source.contains("sixty-nine") || source.contains("69:") -> "one way oral, standing sex, kissing only"
        source.contains("mutual masturbation") -> "single person, penetration, oral sex, footjob"
        source.contains("cunnilingus") -> "blowjob, footjob, vaginal penetration, anal penetration"
        source.contains("paizuri") -> "handjob, blowjob, vaginal penetration, anal penetration"
        source.contains("scissoring") || source.contains("tribadism") -> "male only, penis penetration, standing pose, kissing only"
        source.contains("handjob") -> "footjob, blowjob, vaginal penetration, anal penetration"
        source.contains("footjob") -> "handjob, blowjob, vaginal penetration, anal penetration"
        source.contains("bukkake") -> "single man, only two people, dry face, no visible climax"
        source.contains("facial:") || source.contains("facial finish") -> "bukkake crowd, dry face, vaginal sex as main act"
        source.contains("creampie") -> "facial, external ejaculation, condom, no vaginal contact"
        source.contains("futanari") -> "no penis, ordinary female nude, clothed"
        source.contains("bdsm") -> "no restraints, vanilla standing nude, fashion pose"
        source.contains("anal sex") || source.startsWith("anal") -> "vaginal penetration, blowjob, footjob"
        source.contains("blowjob") || source.contains("oral sex") -> "cunnilingus, footjob, vaginal penetration, anal penetration"
        source.contains("vaginal sex") || source.contains("vaginal") -> "anal penetration, blowjob, footjob"
        source.contains("masturbation") -> "second person, group sex, penetration, oral sex"
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
