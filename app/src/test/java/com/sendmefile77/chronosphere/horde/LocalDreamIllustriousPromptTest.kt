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
        assertTrue(local.positivePrompt.contains("hearth fire"))
        assertTrue(local.positivePrompt.contains("wide shot"))
        assertTrue(local.positivePrompt.indexOf("hide tent") < local.positivePrompt.indexOf("footjob"))
        assertFalse(local.positivePrompt.contains("exact face match"))
        assertFalse(local.positivePrompt.contains("natural standing"))
        assertTrue(local.negativePrompt.contains("modern bedroom"))
        assertNull(local.referenceCacheKey)
        assertEquals(24, local.steps)
        assertEquals(5.5, local.cfgScale, 0.0001)
        assertEquals(24, horde.steps)
        assertTrue(local.cacheKey.contains("|ld-illust-v3"))
        assertFalse(local.cacheKey.contains("|material-v2"))
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
        assertTrue(local.positivePrompt.contains("prehistoric tribal camp"))
        assertFalse(local.cacheKey.contains("|material-v2"))
    }

    @Test
    fun safePortraitKeepsConcreteHistoricalChoicesAfterIllustriousCompression() {
        val local = LocalDreamIllustriousPrompt.apply(
            HordeImageRequest(
                cacheKey = "horde-resolved-scene-v11|safe-information-ruler",
                positivePrompt = "information-age society, contemporary city street, data-rich civic control rooms and automated public infrastructure, homes and small local spaces functioning as workplaces and classrooms, telepresence screens and reduced commuter traffic, ubiquitous connected devices and public network terminals, fully clothed",
                nsfw = false,
                ageYears = 38,
                seed = "safe-seed",
                referenceCacheKey = "horde-character-reference-v5|safe-information-ruler",
                saveResultAsReference = true,
            ),
        )

        assertFalse(local.nsfw)
        assertTrue(local.positivePrompt.contains("civic control room"))
        assertTrue(local.positivePrompt.contains("telepresence"))
        assertTrue(local.positivePrompt.contains("connected devices"))
        assertTrue(local.positivePrompt.contains("fully clothed"))
        assertFalse(local.negativePrompt.contains("clothed"))
        assertTrue(local.negativePrompt.contains("nudity"))
        assertTrue(local.cacheKey.contains("|material-v2"))
        assertTrue(local.saveResultAsReference)
        assertTrue(local.referenceCacheKey?.contains("safe-information-ruler") == true)
    }
}
