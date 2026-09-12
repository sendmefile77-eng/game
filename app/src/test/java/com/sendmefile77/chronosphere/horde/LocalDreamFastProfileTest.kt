package com.sendmefile77.chronosphere.horde

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDreamFastProfileTest {
    @Test
    fun distilledOnDeviceProfileStaysWellBelowHordeStepCounts() {
        val horde = HordeImageRequest(
            cacheKey = "test",
            positivePrompt = "adult portrait",
            nsfw = false,
            ageYears = 24,
            steps = 36,
            cfgScale = 7.2,
            seed = "seed",
        )
        val local = LocalDreamFastProfile.apply(horde)
        assertEquals(8, local.steps)
        assertEquals(1.4, local.cfgScale, 0.0001)
        assertEquals("lcm", local.samplerName)
        assertEquals(36, horde.steps)
    }
}
