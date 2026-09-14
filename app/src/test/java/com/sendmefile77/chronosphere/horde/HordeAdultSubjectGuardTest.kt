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

    @Test
    fun personLockAllowsChimeraAndForbidsQuadrupedSubject() {
        assertTrue(HordeAdultSubjectGuard.PERSON_LOCK.contains("chimera"))
        assertTrue(HordeAdultSubjectGuard.PERSON_LOCK.contains("never a quadruped"))
        assertTrue(HordeAdultSubjectGuard.looksChimeric("exactly 4 arms, visible anatomical tail, natural scales"))
        assertFalse(HordeAdultSubjectGuard.looksChimeric("fair skin, long wavy hair"))
        assertFalse(HordeAdultSubjectGuard.animalSubjectNegatives(chimeric = true).contains("furry"))
        assertTrue(HordeAdultSubjectGuard.animalSubjectNegatives(chimeric = false).contains("furry"))
    }

    @Test
    fun identityFragmentKeepsFaceAndMorphology() {
        val fragment = HordeAdultSubjectGuard.identityFragment(
            "female, olive skin, dark brown long wavy hair, hazel eyes, oval face, athletic build, exactly 4 arms, clearly visible anatomical tail",
        )
        assertTrue(fragment.contains("olive skin"))
        assertTrue(fragment.contains("dark brown long wavy hair"))
        assertTrue(fragment.contains("hazel eyes"))
        assertTrue(fragment.contains("exactly 4 arms"))
        assertTrue(fragment.contains("tail"))
    }
}
