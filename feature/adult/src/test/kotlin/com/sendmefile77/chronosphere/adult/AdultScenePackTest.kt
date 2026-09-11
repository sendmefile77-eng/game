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
import java.nio.file.Files

class AdultScenePackTest {
    private val loader = javaClass.classLoader

    @Test
    fun manifestIsLoadableAndHasNoUrlsOrDuplicateKeys() {
        val text = resourceText("scene_packs/adult/manifest.tsv")
        val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("CHRONOSPHERE_SCENE_ASSET_V1", lines.first())
        val pack = lines.first { it.startsWith("PACK\t") }.split("\t")
        assertEquals(listOf("PACK", "adult-visual", "1", "1024", "1536"), pack)
        val assets = lines.filter { it.startsWith("ASSET\t") }.map { it.split("\t") }
        val keys = assets.map { it[1] }
        assertEquals(keys.size, keys.toSet().size)
        assets.forEach { cols ->
            assertEquals(4, cols.size)
            assertFalse(cols[3].startsWith("http://") || cols[3].startsWith("https://"))
        }
    }

    @Test
    fun generatorWritesNonEmptyPngsForEveryManifestPath() {
        val dir = Files.createTempDirectory("adult-pack").toFile()
        AdultPackGenerator.writeAll(dir)
        manifestPaths().forEach { path ->
            val file = dir.resolve(path)
            assertTrue(path, file.isFile)
            val bytes = file.readBytes()
            assertTrue(path, bytes.size > 32)
            assertEquals(0x89.toByte(), bytes[0])
            assertEquals('P'.code.toByte(), bytes[1])
            assertEquals('N'.code.toByte(), bytes[2])
            assertEquals('G'.code.toByte(), bytes[3])
        }
    }

    @Test
    fun classloaderServesGeneratedPngsWhenPackTaskRan() {
        manifestPaths().forEach { path ->
            val stream = loader.getResourceAsStream(path)
            if (stream != null) {
                val bytes = stream.readBytes()
                assertTrue(path, bytes.size > 32)
                assertEquals(0x89.toByte(), bytes[0])
            }
        }
    }

    @Test
    fun everyCardRecipeHasManifestCoverage() {
        val keys = manifestKeys()
        AdultCardRecipes.all.forEach { recipe ->
            assertTrue(recipe.id, keys.contains("recipe:${recipe.id}"))
        }
    }

    @Test
    fun atLeastTwelveEventRecipesHaveRasterCoverage() {
        val eventIds = BundledVisualRecipes.all.map { it.id }.toSet()
        val covered = manifestKeys().map { it.removePrefix("recipe:") }.filter { it in eventIds }
        assertTrue("covered=${covered.size} $covered", covered.size >= 12)
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
        assertFalse(undressed.fallbackUsed && undressed.wardrobeState != WardrobeState.UNDRESSED)
    }

    private fun manifestKeys(): Set<String> =
        resourceText("scene_packs/adult/manifest.tsv").lineSequence()
            .map { it.trimEnd() }
            .filter { it.startsWith("ASSET\t") }
            .map { it.split("\t")[1] }
            .toSet()

    private fun manifestPaths(): List<String> =
        resourceText("scene_packs/adult/manifest.tsv").lineSequence()
            .map { it.trimEnd() }
            .filter { it.startsWith("ASSET\t") }
            .map { it.split("\t")[3] }
            .toList()

    private fun resourceText(path: String): String {
        val stream = loader.getResourceAsStream(path)
        assertNotNull(path, stream)
        return stream!!.bufferedReader().readText()
    }
}
