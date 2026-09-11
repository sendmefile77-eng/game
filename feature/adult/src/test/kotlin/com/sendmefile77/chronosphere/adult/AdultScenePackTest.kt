package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import com.sendmefile77.chronosphere.scene.WardrobeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultScenePackTest {
    private val loader = javaClass.classLoader

    @Test
    fun manifestIsLoadableAndWellFormed() {
        val text = resourceText("scene_packs/adult/manifest.tsv")
        val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("CHRONOSPHERE_SCENE_ASSET_V1", lines.first())
        assertEquals(listOf("PACK", "adult-visual", "1", "1024", "1536"), lines.first { it.startsWith("PACK\t") }.split("\t"))
        val assets = lines.filter { it.startsWith("ASSET\t") }.map { it.split("\t") }
        val keys = assets.map { it[1] }
        assertEquals(keys.size, keys.toSet().size)
        assets.forEach { cols ->
            assertEquals(4, cols.size)
            assertFalse(cols[3].startsWith("http://") || cols[3].startsWith("https://"))
            val bytes = requireResource(cols[3])
            assertTrue(cols[3], bytes.size > 32)
            val png = bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
            val webp = bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
            assertTrue(cols[3], png || webp)
        }
    }

    @Test
    fun everyCardRecipeHasManifestAndRaster() {
        val keys = manifestKeys()
        AdultCardRecipes.all.forEach { recipe ->
            val key = "recipe:${recipe.id}"
            assertTrue(recipe.id, key in keys)
            requireResource("scene_packs/adult/recipe/${recipe.id}.png")
        }
    }

    @Test
    fun atLeastTwelveEventRecipesHaveRasterCoverage() {
        val eventIds = BundledVisualRecipes.all.map { it.id }.toSet()
        val covered = manifestKeys().map { it.removePrefix("recipe:") }.filter { it in eventIds }
        assertTrue("covered=${covered.size} $covered", covered.size >= 12)
        covered.forEach { id -> requireResource("scene_packs/adult/recipe/$id.png") }
    }

    @Test
    fun assetPackDoesNotChangeG007BridgeBehavior() {
        val bridge = AdultSceneBridge()
        val request = AdultEventRequest(
            requestId = "pack-regression",
            participants = listOf(AdultParticipantRef("person-1", 27)),
            context = AdultWorldContext(424242L, 120L, setOf("courtly")),
        )
        val dressed = bridge.dressedCharacterCard(request)
        val undressed = bridge.undressedCharacterCard(request)
        assertEquals(dressed, bridge.dressedCharacterCard(request))
        assertEquals(WardrobeState.UNDRESSED, undressed.wardrobeState)
    }

    private fun manifestKeys(): Set<String> =
        resourceText("scene_packs/adult/manifest.tsv").lineSequence()
            .map { it.trimEnd() }
            .filter { it.startsWith("ASSET\t") }
            .map { it.split("\t")[1] }
            .toSet()

    private fun requireResource(path: String): ByteArray {
        val stream = loader.getResourceAsStream(path)
        assertNotNull(path, stream)
        return stream!!.readBytes()
    }

    private fun resourceText(path: String): String = requireResource(path).toString(Charsets.UTF_8)
}
