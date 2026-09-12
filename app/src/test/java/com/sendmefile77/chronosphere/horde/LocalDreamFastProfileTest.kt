package com.sendmefile77.chronosphere.horde

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalDreamFastProfileTest {
    @Before
    fun resetToIllustrious() {
        LocalDreamModelPackRuntime.select(LocalDreamModelPacks.illustrious)
    }

    @After
    fun restoreDefault() {
        LocalDreamModelPackRuntime.select(LocalDreamModelPacks.illustrious)
    }

    @Test
    fun illustriousPackUsesFullCheckpointSamplingNotLcm() {
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
        assertEquals(24, local.steps)
        assertEquals(5.5, local.cfgScale, 0.0001)
        assertEquals("k_euler_a", local.samplerName)
        assertEquals(36, horde.steps)
        assertTrue(local.cacheKey.contains("ld-pack-illustrious"))
    }

    @Test
    fun cyberRealisticPackKeepsEraPromptAndUsesScreenshotSampling() {
        LocalDreamModelPackRuntime.select(LocalDreamModelPacks.cyberRealistic)
        val horde = HordeImageRequest(
            cacheKey = "horde-chronicle-event-v7-city-erotica|settle",
            positivePrompt = "prehistoric tribal society, hide tents, hearth fire, settlement Galenhaven",
            nsfw = true,
            ageYears = 21,
            steps = 22,
            cfgScale = 5.5,
            seed = "seed",
        )
        val local = LocalDreamFastProfile.apply(horde)
        assertEquals(20, local.steps)
        assertEquals(7.0, local.cfgScale, 0.0001)
        assertEquals("k_dpmpp_2m", local.samplerName)
        assertTrue(local.positivePrompt.contains("hide tents"))
        assertTrue(local.positivePrompt.contains("Galenhaven"))
        assertTrue(local.positivePrompt.contains("photorealistic"))
        assertFalse(local.positivePrompt.contains("1girl"))
        assertTrue(local.negativePrompt.contains("anime"))
        assertTrue(local.cacheKey.contains("ld-pack-cyberrealistic"))
        assertEquals("CyberRealistic XL", local.preferredModels.first())
    }
}
