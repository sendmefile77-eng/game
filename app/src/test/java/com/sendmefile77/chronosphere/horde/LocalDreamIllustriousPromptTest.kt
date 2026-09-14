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
    fun footjobKeepsEraIdentityAndDropsIdlePortraitLanguage() {
        val horde = HordeImageRequest(
            cacheKey = "horde-adult-action-v12|FOOTJOB:pair:1:adult-a:adult-c",
            positivePrompt = "FOOTJOB: two nude adult women, one lying back, hide tent, ochre, olive skin, dark brown long wavy hair, hazel eyes, oval face, exact face match, natural standing or seated pose",
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
        assertTrue(local.positivePrompt.contains("olive skin"))
        assertTrue(local.positivePrompt.contains("dark brown long wavy hair"))
        assertTrue(local.positivePrompt.contains("hazel eyes"))
        assertTrue(local.positivePrompt.contains("person of this lineage") || local.positivePrompt.contains("adult person"))
        assertFalse(local.positivePrompt.contains("tamed dogs"))
        assertFalse(local.positivePrompt.contains("natural standing"))
        assertTrue(local.negativePrompt.contains("blowjob"))
        assertTrue(local.negativePrompt.contains("modern bedroom"))
        assertNull(local.referenceCacheKey)
        assertEquals(24, local.steps)
        assertEquals(5.5, local.cfgScale, 0.0001)
        assertEquals(24, horde.steps)
        assertTrue(local.cacheKey.contains("|ld-illust-v7"))
        assertFalse(local.cacheKey.contains("|material-v2"))
        assertTrue(local.cacheKey.contains("ld-pack-illustrious"))
    }

    @Test
    fun expandedSelectedActsSurviveIllustriousCompression() {
        val cases = listOf(
            "HANDJOB: adult woman and adult man" to "handjob",
            "CUNNILINGUS: two adult women" to "cunnilingus",
            "69: adult woman and adult man, sixty-nine" to "69",
            "PAIZURI: adult woman and adult man" to "paizuri",
            "SCISSORING: two adult women, tribadism" to "scissoring",
            "MUTUAL MASTURBATION: adult woman and adult man" to "mutual masturbation",
            "FACIAL: adult woman and adult man, facial finish" to "facial",
            "CREAMPIE: adult woman and adult man" to "creampie",
            "MMF THREESOME: one adult woman and two adult men" to "mmf threesome",
            "FFM THREESOME: two adult women and one adult man" to "ffm threesome",
        )

        cases.forEachIndexed { index, (prompt, expected) ->
            val local = LocalDreamIllustriousPrompt.apply(
                HordeImageRequest(
                    cacheKey = "horde-adult-action-v12|case-$index",
                    positivePrompt = "$prompt, medieval timber chamber, adult age 28",
                    nsfw = true,
                    ageYears = 28,
                    seed = "seed-$index",
                ),
            )
            assertTrue("$expected should survive compression", local.positivePrompt.contains(expected))
            assertTrue(local.positivePrompt.contains("medieval"))
            assertTrue(local.cacheKey.contains("|ld-illust-v7"))
        }
    }

    @Test
    fun analDoesNotAskForAKissAndKeepsEra() {
        val local = LocalDreamIllustriousPrompt.apply(
            HordeImageRequest(
                cacheKey = "horde-adult-action-v12|ANAL:pair:1:a:c",
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
    fun chimericNudeKeepsExtraLimbsAndDropsFurryNegative() {
        val local = LocalDreamIllustriousPrompt.apply(
            HordeImageRequest(
                cacheKey = "horde-resolved-scene-v14|chimera",
                positivePrompt = "adult woman, olive skin, exactly 4 arms, clearly visible anatomical tail, natural scales covering the body, hide tent",
                nsfw = true,
                ageYears = 28,
                seed = "seed",
            ),
        )
        assertTrue(local.positivePrompt.contains("exactly 4 arms"))
        assertTrue(local.positivePrompt.contains("tail"))
        assertTrue(local.positivePrompt.contains("chimera"))
        assertFalse(local.negativePrompt.contains("furry"))
        assertTrue(local.negativePrompt.contains("dog"))
    }

    @Test
    fun safePortraitKeepsConcreteHistoricalChoicesAfterIllustriousCompression() {
        val local = LocalDreamIllustriousPrompt.apply(
            HordeImageRequest(
                cacheKey = "horde-resolved-scene-v14|safe-information-ruler",
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
