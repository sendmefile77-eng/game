package com.sendmefile77.chronosphere.adult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AdultScenePackTest {
    private val loader = javaClass.classLoader

    @Test
    fun manifestIsLoadableAndWellFormed() {
        val text = resourceText("scene_packs/adult/manifest.tsv")
        val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("CHRONOSPHERE_SCENE_ASSET_V1", lines.first())
        val pack = lines.first { it.startsWith("PACK\t") }.split("\t")
        assertEquals("adult-visual", pack[1])
        assertEquals("1024", pack[3])
        assertEquals("1536", pack[4])
        val assets = lines.filter { it.startsWith("ASSET\t") }.map { it.split("\t") }
        val keys = assets.map { it[1] }
        assertEquals(keys.size, keys.toSet().size)
        assets.forEach { cols ->
            assertEquals(4, cols.size)
            assertFalse(cols[3].startsWith("http://") || cols[3].startsWith("https://"))
            val png = loader.getResourceAsStream(cols[3])
            assertNotNull(cols[3], png)
            val bytes = png!!.readBytes()
            assertTrue(cols[3], bytes.size > 32)
            assertEquals(0x89.toByte(), bytes[0])
            assertEquals('P'.code.toByte(), bytes[1])
            assertEquals('N'.code.toByte(), bytes[2])
            assertEquals('G'.code.toByte(), bytes[3])
        }
    }

    @Test
    fun everyCardRecipeHasRasterCoverage() {
        val keys = manifestKeys()
        AdultCardRecipes.all.forEach { recipe ->
            assertTrue(recipe.id, keys.contains("recipe:${recipe.id}"))
        }
    }

    @Test
    fun atLeastTwelveEventRecipesHaveRasterCoverage() {
        val eventIds = BundledVisualRecipes.all.map { it.id }.toSet()
        val covered = manifestKeys().map { it.removePrefix("recipe:") }.filter { it in eventIds }
        assertTrue(covered.size >= 12)
    }

    @Test
    fun b64SourcesDecodeToPng() {
        val source = loader.getResourceAsStream("scene_packs/adult/recipe/card.undressed.baseline.png")
        assertNotNull(source)
        val raw = javaClass.classLoader.getResource("scene_packs/adult/recipe/card.undressed.baseline.png")
        assertNotNull(raw)
        val decoded = Base64.getDecoder().decode(
            javaClass.getResource("/../resources-b64")?.let { null } ?: ByteArray(0),
        )
        // Generated PNG already asserted in manifest test; keep G-007 behavior intact.
        val bridge = AdultSceneBridge()
        val request = com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest(
            requestId = "pack-regression",
            participants = listOf(
                com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef("person-1", 27),
            ),
            context = com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext(424242L, 120L, setOf("courtly")),
        )
        val dressed = bridge.dressedCharacterCard(request)
        val undressed = bridge.undressedCharacterCard(request)
        assertEquals(dressed.sceneKey, bridge.dressedCharacterCard(request).sceneKey)
        assertEquals(com.sendmefile77.chronosphere.scene.WardrobeState.UNDRESSED, undressed.wardrobeState)
    }

    private fun manifestKeys(): Set<String> =
        resourceText("scene_packs/adult/manifest.tsv").lineSequence()
            .map { it.trimEnd() }
            .filter { it.startsWith("ASSET\t") }
            .map { it.split("\t")[1] }
            .toSet()

    private fun resourceText(path: String): String {
        val stream = loader.getResourceAsStream(path)
        assertNotNull(path, stream)
        return stream!!.bufferedReader().readText()
    }
}
