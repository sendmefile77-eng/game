package com.sendmefile77.chronosphere.horde

data class HordeImageRequest(
    val cacheKey: String,
    val positivePrompt: String,
    val negativePrompt: String = DEFAULT_NEGATIVE_PROMPT,
    val nsfw: Boolean,
    val ageYears: Int,
    val width: Int = 512,
    val height: Int = 768,
    val steps: Int = 24,
    val cfgScale: Double = 6.5,
    val samplerName: String = "k_dpmpp_2m",
    val seed: String,
    val preferredModels: List<String> = emptyList(),
    val qualityPriority: Boolean = false,
    val referenceCacheKey: String? = null,
    val saveResultAsReference: Boolean = false,
    val referenceDenoisingStrength: Double = 0.55,
) {
    init {
        require(cacheKey.isNotBlank())
        require(positivePrompt.isNotBlank())
        require(ageYears >= 0)
        require(width in 64..3072 && width % 64 == 0)
        require(height in 64..3072 && height % 64 == 0)
        require(steps in 1..500)
        require(cfgScale.isFinite() && cfgScale in 0.0..100.0)
        require(samplerName.isNotBlank())
        require(seed.isNotBlank())
        require(preferredModels.none { it.isBlank() })
        require(referenceCacheKey == null || referenceCacheKey.isNotBlank())
        require(referenceDenoisingStrength.isFinite() && referenceDenoisingStrength in 0.01..1.0)
        if (saveResultAsReference) require(!nsfw) { "NSFW generations must never become character references" }
        if (nsfw) require(ageYears >= 18) { "NSFW Horde requests require an adult participant" }
    }

    fun apiPrompt(): String = if (negativePrompt.isBlank()) {
        positivePrompt
    } else {
        "$positivePrompt###$negativePrompt"
    }

    companion object {
        const val DEFAULT_NEGATIVE_PROMPT =
            "low quality, worst quality, blurry, lowres, pixelated, jpeg artifacts, compression artifacts, " +
                "muddy details, oversmoothed skin, deformed, disfigured, bad anatomy, bad proportions, " +
                "extra limbs, extra fingers, fused fingers, duplicate body parts, poorly drawn hands, " +
                "poorly drawn face, text, watermark, logo, collage"
    }
}

data class HordeGenerationResult(
    val requestId: String,
    val generationId: String?,
    val imageBytes: ByteArray,
    val model: String?,
    val seed: String?,
    val censored: Boolean,
) {
    init {
        require(requestId.isNotBlank())
        require(imageBytes.isNotEmpty())
    }
}

class HordeGenerationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
