package com.sendmefile77.chronosphere.adult

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultPackValidatorTest {
    @Test
    fun rejectsBlankAndDuplicateCodes() {
        val pack = AdultContentPack(
            id = "bad",
            matchTags = emptySet(),
            priority = 1,
            events = listOf(
                validRule("ONE"),
                validRule("ONE"),
                validRule("X").copy(code = " "),
            ),
        )
        val errors = AdultPackValidator.validate(pack)
        assertTrue(errors.any { it.contains("duplicate") })
        assertTrue(errors.any { it.contains("blank event code") })
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeCoefficients() {
        val pack = AdultContentPack(
            id = "nan",
            matchTags = emptySet(),
            priority = 1,
            events = listOf(
                validRule("BROKEN").copy(
                    intimacy = Double.NaN,
                    scandal = Double.POSITIVE_INFINITY,
                    fertility = 4.0,
                    baseWeight = -0.2,
                ),
            ),
        )
        val errors = AdultPackValidator.validate(pack)
        assertTrue(errors.any { it.contains("intimacy") })
        assertTrue(errors.any { it.contains("scandal") })
        assertTrue(errors.any { it.contains("fertility") })
        assertTrue(errors.any { it.contains("baseWeight") })
    }

    @Test
    fun rejectsBlankMediaKeysAndTags() {
        val pack = AdultContentPack(
            id = "media",
            matchTags = emptySet(),
            priority = 1,
            events = listOf(
                validRule("NO_MEDIA").copy(mediaKey = "", mediaTags = setOf("")),
            ),
        )
        val errors = AdultPackValidator.validate(pack)
        assertTrue(errors.any { it.contains("media key") })
        assertTrue(errors.any { it.contains("blank media tag") })
    }

    @Test
    fun bundledPacksAreValid() {
        BundledAdultPacks.all.forEach { pack ->
            assertTrue(AdultPackValidator.validate(pack).isEmpty())
            pack.events.forEach { event ->
                assertFalse(event.mediaKey.isBlank())
                assertTrue(event.mediaKey.startsWith("adult://"))
            }
        }
    }

    private fun validRule(code: String) = AdultEventRule(
        code = code,
        intimacy = 0.5,
        scandal = 0.2,
        fertility = 0.1,
        setting = "chamber",
        mediaKey = "adult://scene/${code.lowercase()}/chamber",
        mediaTags = setOf("sex"),
    )
}
