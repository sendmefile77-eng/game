package com.sendmefile77.chronosphere

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterCharacterAssetIntegrityTest {
    @Test
    fun requiredRasterPacksReassembleToCompleteWebpFiles() {
        assertCompleteWebp(
            parts = listOf(
                "character_parts_v03_480.b64.00",
                "character_parts_v03_480.b64.01",
            ),
            expectedDecodedBytes = 26_520,
        )
        assertCompleteWebp(
            parts = listOf("female_undress_torsos_v01_384.b64.00"),
            expectedDecodedBytes = 10_274,
        )
    }

    private fun assertCompleteWebp(parts: List<String>, expectedDecodedBytes: Int) {
        val encoded = buildString {
            parts.forEach { name ->
                val relative = "src/main/assets/character_library/v0_1/$name"
                val file = sequenceOf(File(relative), File("app/$relative"))
                    .firstOrNull(File::isFile)
                    ?: error("Missing required character asset part: $name")
                append(file.readText())
            }
        }
        val bytes = Base64.getDecoder().decode(encoded)
        assertEquals(expectedDecodedBytes, bytes.size)
        assertTrue("Raster pack is too small to be a WebP", bytes.size >= 12)
        assertEquals("RIFF", String(bytes, 0, 4, StandardCharsets.US_ASCII))
        assertEquals("WEBP", String(bytes, 8, 4, StandardCharsets.US_ASCII))

        val declaredPayload =
            (bytes[4].toInt() and 0xff) or
                ((bytes[5].toInt() and 0xff) shl 8) or
                ((bytes[6].toInt() and 0xff) shl 16) or
                ((bytes[7].toInt() and 0xff) shl 24)
        assertEquals(
            "Raster pack is truncated or has trailing corruption",
            bytes.size.toLong(),
            declaredPayload.toLong() + 8L,
        )
    }
}
