package com.sendmefile77.chronosphere.horde

/**
 * Distilled on-device models (DMD2 / LCM in Local Dream) overcook at Horde step
 * counts. Horde workers still use the request's own steps/cfg; this profile is
 * only applied to the Local Dream payload.
 */
internal object LocalDreamFastProfile {
    const val STEPS = 8
    const val CFG = 1.4
    const val SAMPLER = "lcm"
    const val TIMEOUT_MS = 60_000L

    fun apply(request: HordeImageRequest): HordeImageRequest = request.copy(
        steps = STEPS,
        cfgScale = CFG,
        samplerName = SAMPLER,
    )
}
