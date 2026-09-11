package com.sendmefile77.chronosphere.horde

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files

class HordeCharacterReferenceStoreTest {
    @Test
    fun firstSafeReferenceWinsAndKeepsItsModel() {
        val directory = Files.createTempDirectory("horde-ref-test").toFile()
        try {
            val store = HordeCharacterReferenceStore(directory)
            val firstBytes = byteArrayOf(1, 2, 3, 4)
            val secondBytes = byteArrayOf(9, 9, 9)

            store.writeIfAbsent("character-a", firstBytes, "Model A")
            store.writeIfAbsent("character-a", secondBytes, "Model B")

            val reference = store.read("character-a")
            assertNotNull(reference)
            assertArrayEquals(firstBytes, reference!!.imageBytes)
            assertEquals("Model A", reference.model)
        } finally {
            directory.deleteRecursively()
        }
    }
}
