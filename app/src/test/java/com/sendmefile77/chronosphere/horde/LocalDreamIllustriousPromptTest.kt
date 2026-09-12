package com.sendmefile77.chronosphere.horde

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalDreamIllustriousPromptTest {
    @Before
    fun useIllustriousPack() {
        LocalDreamModelPackRuntime.select(LocalDreamModelPacks.illustrious)
    }

    @After
    fun restoreDefault() {
        LocalDreamModelPackRuntime.select(LocalDreamModelPacks.illustrious)
    }

    @Test
    fun footjobKeepsEraAndDropsPortraitReference() {
        val horde = HordeImageRequest(
            cacheKey = "horde-adult-action-v8|FOOTJOB:1:adult-a:adult-c",
            positivePrompt = "FOOTJOB: two nude adult women, one lying back, hide tent, ochre, exact face match, natural standing or seated pose",
            nsfw = true,
            ageYears = 24,
            steps = 24,
            cfgScale = 6.0,
            seed = "seed",
            referenceCacheKey = "horde-character-reference-v5|adult-a",
        )
        val local = LocalDreamFastProfile.apply(horde)
        assertTrue(local.positivePrompt.contains("footjob"))
        assertTrue(local.positivePrompt.contains("2girls"))
        assertTrue(local.positivePrompt.contains("soles"))
        assertTrue(local.positivePrompt.contains("hide tent"))
        assertTrue(local.positivePrompt.contains("ochre"))
        assertFalse(local.positivePrompt.contains("exact face match"))
        assertFalse(local.positivePrompt.contains("natural standing"))
        assertNull(local.referenceCacheKey)
        assertEquals(24, local.steps)
        assertEquals(5.5, local.cfgScale, 0.0001)
        assertEquals(24, horde.steps)
        assertTrue(local.cacheKey.contains("|ld-illust-v2"))
        assertTrue(local.cacheKey.contains("ld-pack-illustrious"))
    }

    @Test
    fun analDoesNotAskForAKissAndKeepsEra() {
        val local = LocalDreamIllustriousPrompt.apply(
            HordeImageRequest(
                cacheKey = "horde-adult-action-v8|ANAL:1:a:c",
                positivePrompt = "ANAL SEX: two nude adult women, kissing as fallback, hide tent",
                nsfw = true,
                ageYears = 24,
                seed = "seed",
            ),
        )
        assertTrue(local.positivePrompt.contains("anal"))
        assertTrue(local.negativePrompt.contains("kiss"))
        assertTrue(local.positivePrompt.contains("hide tent"))
    }
}
