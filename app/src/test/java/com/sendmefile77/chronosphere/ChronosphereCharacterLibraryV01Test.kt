package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.scene.WardrobeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChronosphereCharacterLibraryV01Test {
    @Test
    fun sameCharacterGetsSameParts() {
        val first = ChronosphereCharacterLibraryV01.select("person-alpha", 32)
        val second = ChronosphereCharacterLibraryV01.select("person-alpha", 32)
        assertEquals(first, second)
    }

    @Test
    fun differentCharactersCanDiverge() {
        val first = ChronosphereCharacterLibraryV01.select("person-alpha", 32)
        val second = ChronosphereCharacterLibraryV01.select("person-beta", 32)
        assertNotEquals(first, second)
    }

    @Test
    fun ageOnlyChangesAgeSensitivePaletteNotIdentityParts() {
        val young = ChronosphereCharacterLibraryV01.select("person-alpha", 32)
        val old = ChronosphereCharacterLibraryV01.select("person-alpha", 72)
        assertEquals(young.face, old.face)
        assertEquals(young.eyes, old.eyes)
        assertEquals(young.brows, old.brows)
        assertEquals(young.nose, old.nose)
        assertEquals(young.mouth, old.mouth)
        assertEquals(young.hair, old.hair)
        assertEquals(young.frame, old.frame)
        assertEquals(young.garment, old.garment)
        assertNotEquals(young.hairColor, old.hairColor)
    }
}
