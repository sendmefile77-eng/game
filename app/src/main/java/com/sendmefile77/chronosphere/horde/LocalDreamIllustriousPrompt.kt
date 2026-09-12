package com.sendmefile77.chronosphere.horde

/**
 * Local Dream is running WAI Illustrious (or a DMD2 merge of it), not a Horde worker.
 * Long documentary prompts collapse to standing anime nudes. This rewrite keeps the
 * act and drops era/identity soup. Horde still receives the original request.
 */
internal object LocalDreamIllustriousPrompt {
    private const val QUALITY =
        "masterpiece, best quality, absurdres, rating_explicit, uncensored, nude"

    private const val NEGATIVE =
        "lowres, worst quality, bad anatomy, extra limbs, extra fingers, text, watermark, " +
            "clothed, dress, panties, bra, standing idle, standing side by side, " +
            "portrait, cowboy shot, kissing, kiss, closed mouth, 3d, realistic photo, child, loli"

    fun apply(request: HordeImageRequest): HordeImageRequest {
        val source = request.positivePrompt.lowercase()
        val action = request.cacheKey.startsWith("horde-adult-action-") || looksLikeAct(source)
        val girls = countWomen(source)
        val men = countMen(source)
        val people = peopleTag(girls, men, action, request.nsfw)
        val act = actTags(source)
        val positive = if (action && act.isNotBlank()) {
            "$QUALITY, $people, $act"
        } else if (request.nsfw) {
            "$QUALITY, $people, standing, nipples, pussy, navel"
        } else {
            "masterpiece, best quality, $people, fully clothed"
        }
        return request.copy(
            cacheKey = "${request.cacheKey}|ld-illust-v1",
            positivePrompt = positive,
            negativePrompt = NEGATIVE,
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
}
