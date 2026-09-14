package com.sendmefile77.chronosphere.horde

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HordeAdultSubjectGuardTest {
    @Test
    fun sanitizeStripsSfwDogLanguageFromAdultPrompts() {
        val raw = "tamed dogs or herd animals living beside people, leashes, pens, feed piles and animals assisting daily work"
        val cleaned = HordeAdultSubjectGuard.sanitize(raw)
        assertFalse(cleaned.contains("dog", ignoreCase = true))
        assertTrue(cleaned.contains("leather") || cleaned.contains("no living animal") || cleaned.contains("human"))
    }

    @Test
    fun domesticationLanguageCannotRemainTheSubject() {
        val assembled = HordeAdultSubjectGuard.sanitize(
            "sex in the animal pen margin of camp, hides and leashes nearby, bodies marked by daily work with animals, tamed dogs",
        )
        assertFalse(assembled.contains("tamed dogs"))
        assertFalse(assembled.contains("animal pen margin"))
        assertTrue(assembled.contains("human") || assembled.contains("leather") || assembled.contains("shelter"))
    }
}
