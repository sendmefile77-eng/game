package com.sendmefile77.chronosphere

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HistoricalVisualTagTest {
    @Test
    fun clothTagDeterministicallySelectsRasterGarment() {
        val first = CharacterBoardRuntime.select("person-a", 28, setOf("cloth:undyed-linen", "jewel:copper-loop"))
        val second = CharacterBoardRuntime.select("person-a", 28, setOf("jewel:copper-loop", "cloth:undyed-linen"))
        assertEquals(first, second)
    }

    @Test
    fun differentHistoricalClothCanChangeAvailableRasterVariantWithoutChangingIdentityFamily() {
        val linen = CharacterBoardRuntime.select("person-a", 28, setOf("cloth:undyed-linen"))
        val tailored = CharacterBoardRuntime.select("person-a", 28, setOf("cloth:cut-tailored"))
        assertEquals(linen.femaleFamily, tailored.femaleFamily)
        assertEquals(linen.headIndex, tailored.headIndex)
        assertNotEquals(linen.garmentIndex, tailored.garmentIndex)
    }
}
