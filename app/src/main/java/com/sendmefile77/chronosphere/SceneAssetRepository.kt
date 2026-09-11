package com.sendmefile77.chronosphere

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.sendmefile77.chronosphere.scene.ResolvedScene
import java.io.InputStream

internal data class SceneAssetEntry(
    val logicalKey: String,
    val zIndex: Int,
    val path: String,
    val packId: String,
)

internal data class SceneAssetManifest(
    val packId: String,
    val version: Int,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val entries: Map<String, SceneAssetEntry>,
) {
    companion object {
        private const val HEADER = "CHRONOSPHERE_SCENE_ASSET_V1"

        fun parse(text: String, source: String): SceneAssetManifest {
            val lines = text.lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                .toList()
            require(lines.firstOrNull() == HEADER) { "Invalid scene asset header in $source" }
            val packFields = lines.drop(1).firstOrNull { it.startsWith("PACK\t") }
                ?.split('\t')
                ?: error("Missing PACK row in $source")
            require(packFields.size == 5) { "Invalid PACK row in $source" }
            val packId = packFields[1].trim()
            val version = packFields[2].toIntOrNull() ?: error("Invalid pack version in $source")
            val width = packFields[3].toIntOrNull() ?: error("Invalid canvas width in $source")
            val height = packFields[4].toIntOrNull() ?: error("Invalid canvas height in $source")
            require(packId.isNotBlank() && version >= 1 && width > 0 && height > 0)

            val entries = linkedMapOf<String, SceneAssetEntry>()
            lines.drop(1).filter { it.startsWith("ASSET\t") }.forEach { line ->
                val fields = line.split('\t')
                require(fields.size == 4) { "Invalid ASSET row in $source: $line" }
                val key = fields[1].trim()
                val z = fields[2].toIntOrNull() ?: error("Invalid zIndex for $key in $source")
                val path = fields[3].trim()
                require(key.isNotBlank() && path.isNotBlank() && z in -1000..1000)
                require(key !in entries) { "Duplicate scene asset key $key in $source" }
                entries[key] = SceneAssetEntry(key, z, path, packId)
            }
            return SceneAssetManifest(packId, version, width, height, entries)
        }
    }
}

/**
 * Loads local raster layers without coupling the app to implementation-module resource IDs.
 *
 * Base Android content is read from AssetManager. Optional JVM feature packs are packaged as
 * Java resources and read through the app ClassLoader. AssetManager deliberately wins when a
 * path exists in both places, while later manifests still override logical keys.
 */
internal class SceneAssetRepository(
    private val assets: AssetManager,
    private val resourceLoader: ClassLoader,
    manifestPaths: List<String> = DEFAULT_MANIFESTS,
) {
    private val entries: Map<String, SceneAssetEntry>
    val loadedPackIds: List<String>
    private val bitmapCache = mutableMapOf<String, ImageBitmap?>()

    init {
        val merged = linkedMapOf<String, SceneAssetEntry>()
        val packs = mutableListOf<String>()
        manifestPaths.forEach { manifestPath ->
            val manifest = runCatching {
                openLocal(manifestPath)?.bufferedReader()?.use { reader ->
                    SceneAssetManifest.parse(reader.readText(), manifestPath)
                }
            }.getOrNull() ?: return@forEach
            packs += manifest.packId
            // Later manifests deliberately override the same logical key.
            manifest.entries.forEach { (key, entry) -> merged[key] = entry }
        }
        entries = merged
        loadedPackIds = packs
    }

    fun layersFor(scene: ResolvedScene): List<SceneAssetEntry> {
        val keys = buildList {
            add(scene.backgroundKey)
            add(scene.bodyRigKey)
            add(scene.poseKey)
            add("recipe:${scene.recipeId}")
            addAll(scene.layerKeys)
            add(scene.lightingKey)
        }
        return keys.distinct()
            .mapNotNull(entries::get)
            .sortedWith(compareBy<SceneAssetEntry> { it.zIndex }.thenBy { it.logicalKey })
    }

    fun bitmap(entry: SceneAssetEntry): ImageBitmap? = bitmapCache.getOrPut(entry.path) {
        runCatching {
            openLocal(entry.path)?.use { input -> BitmapFactory.decodeStream(input)?.asImageBitmap() }
        }.getOrNull()
    }

    private fun openLocal(path: String): InputStream? {
        val fromAssets = runCatching { assets.open(path) }.getOrNull()
        if (fromAssets != null) return fromAssets
        return resourceLoader.getResourceAsStream(path.removePrefix("/"))
    }

    companion object {
        val DEFAULT_MANIFESTS = listOf(
            "scene_packs/base/manifest.tsv",
            "scene_packs/adult/manifest.tsv",
        )
    }
}
