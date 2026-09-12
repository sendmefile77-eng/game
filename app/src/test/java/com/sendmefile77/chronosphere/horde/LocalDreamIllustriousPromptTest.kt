package com.sendmefile77.chronosphere.horde

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDreamIllustriousPromptTest {
    @Test
    fun footjobBecomesShortDanbooruAndDropsThePortraitReference() {
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
        assertFalse(local.positivePrompt.contains("hide tent"))
        assertFalse(local.positivePrompt.contains("exact face match"))
        assertFalse(local.positivePrompt.contains("natural standing"))
        assertNull(local.referenceCacheKey)
        assertEquals(8, local.steps)
        assertEquals(1.4, local.cfgScale, 0.0001)
        assertEquals(24, horde.steps)
        assertTrue(local.cacheKey.endsWith("|ld-illust-v1"))
    }

    @Test
    fun analDoesNotAskForAKiss() {
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
        assertFalse(local.positivePrompt.contains("hide tent"))
    }
}
