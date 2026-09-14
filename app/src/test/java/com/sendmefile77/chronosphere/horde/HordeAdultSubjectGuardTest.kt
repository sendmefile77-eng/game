package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.TechnologyEra
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HordeAdultSubjectGuardTest {
    @Test
    fun animalTamingAdultCueNeverNamesALivingAnimal() {
        val cue = HordeAdultDecisionVisualCue.forSlug("animal_taming")
        assertFalse(cue.contains("dog", ignoreCase = true))
        assertFalse(cue.contains("herd", ignoreCase = true))
        assertTrue(cue.contains("human") || cue.contains("adult") || cue.contains("sex"))
    }

    @Test
    fun enrichmentKeepsAHumanSubjectWhenDomesticationIsChosen() {
        val prompt = HordeAdultVisualEnrichment.fragment(
            setOf(
                "era-choice:society:animal_taming",
                "foundation:animal_companions",
                "hist:tamed_animals",
                "person_role:ruler",
            ),
            TechnologyEra.TRIBAL,
            HordeAdultVisualEnrichment.Kind.PORTRAIT,
        )
        assertTrue(prompt.contains("human"))
        assertFalse(prompt.contains("tamed dogs"))
        assertFalse(prompt.contains("animal pen"))
    }

    @Test
    fun sanitizeStripsSfwDogLanguageFromAdultPrompts() {
        val raw = "tamed dogs or herd animals living beside people, leashes, pens, feed piles and animals assisting daily work"
        val cleaned = HordeAdultSubjectGuard.sanitize(raw)
        assertFalse(cleaned.contains("dog", ignoreCase = true))
        assertTrue(cleaned.contains("human") || cleaned.contains("leather") || cleaned.contains("no living animal"))
    }
}
