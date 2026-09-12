package com.sendmefile77.chronosphere.horde

/**
 * Applies the active Local Dream model pack to a Horde request.
 * Horde workers still receive the original request; only the on-device payload is rewritten.
 */
internal object LocalDreamFastProfile {
    val STEPS: Int get() = LocalDreamModelPackRuntime.current().steps
    val CFG: Double get() = LocalDreamModelPackRuntime.current().cfgScale
    val SAMPLER: String get() = LocalDreamModelPackRuntime.current().samplerName
    val TIMEOUT_MS: Long get() = LocalDreamModelPackRuntime.current().timeoutMs

    fun apply(request: HordeImageRequest): HordeImageRequest {
        val pack = LocalDreamModelPackRuntime.current()
        val styled = when (pack.promptStyle) {
            LocalDreamPromptStyle.ILLUSTRIOUS_DANBOORU -> LocalDreamIllustriousPrompt.apply(request)
            LocalDreamPromptStyle.PHOTOREAL_KEEP -> applyPhotoreal(request, pack)
        }
        return styled.copy(
            cacheKey = "${styled.cacheKey}|ld-pack-${pack.id}",
            steps = pack.steps,
            cfgScale = pack.cfgScale,
            samplerName = pack.samplerName,
            preferredModels = (pack.hordePreferredModels + styled.preferredModels)
                .distinctBy { it.lowercase() },
        )
    }

    private fun applyPhotoreal(request: HordeImageRequest, pack: LocalDreamModelPack): HordeImageRequest {
        val prefix = pack.qualityPrefix.trim()
        val positive = if (prefix.isBlank() || request.positivePrompt.contains(prefix)) {
            request.positivePrompt
        } else {
            "$prefix, ${request.positivePrompt}"
        }
        val extraNegative = pack.extraNegative.trim()
        val negative = if (extraNegative.isBlank()) {
            request.negativePrompt
        } else {
            "${request.negativePrompt}, $extraNegative"
        }
        return request.copy(positivePrompt = positive, negativePrompt = negative)
    }
}
