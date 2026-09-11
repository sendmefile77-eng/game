package com.sendmefile77.chronosphere

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sendmefile77.chronosphere.scene.WardrobeState
import kotlin.math.min

/**
 * Raster-backed offline character constructor built from the approved mature/semi-realistic board.
 * The source board was physically cut and normalized into canonical 320x480 layers, then packed into
 * one local atlas. Runtime only composites pre-cut local art; there is no AI or network dependency.
 */
internal object RasterCharacterLibraryV01 {
    const val CANVAS_WIDTH = 320
    const val CANVAS_HEIGHT = 480
    const val ATLAS_PATH = "character_library/v0_1/layers_atlas_v01.webp.b64"

    enum class Sex { FEMALE, MALE }

    data class Region(
        val x: Int,
        val y: Int,
        val width: Int = CANVAS_WIDTH,
        val height: Int = CANVAS_HEIGHT,
    )

    data class Selection(
        val sex: Sex,
        val headIndex: Int,
        val garmentIndex: Int,
    ) {
        val bodyId: String = when (sex) {
            Sex.FEMALE -> "body_female_base"
            Sex.MALE -> "body_male_base"
        }
        val headId: String = when (sex) {
            Sex.FEMALE -> "head_female_$headIndex"
            Sex.MALE -> "head_male_$headIndex"
        }
        val garmentId: String = when (sex) {
            Sex.FEMALE -> "garment_female_$garmentIndex"
            Sex.MALE -> "garment_male_$garmentIndex"
        }
    }

    private val regions = mapOf(
        "background_neutral" to Region(0, 0),
        "body_female_base" to Region(320, 0),
        "body_male_base" to Region(640, 0),
        "head_female_0" to Region(960, 0),
        "head_female_1" to Region(0, 480),
        "head_female_2" to Region(320, 480),
        "head_female_3" to Region(640, 480),
        "head_male_0" to Region(960, 480),
        "head_male_1" to Region(0, 960),
        "head_male_2" to Region(320, 960),
        "head_male_3" to Region(640, 960),
        "garment_female_0" to Region(960, 960),
        "garment_female_1" to Region(0, 1440),
        "garment_female_2" to Region(320, 1440),
        "garment_female_3" to Region(640, 1440),
        "garment_male_0" to Region(960, 1440),
        "garment_male_1" to Region(0, 1920),
        "garment_male_2" to Region(320, 1920),
        "garment_male_3" to Region(640, 1920),
    )

    fun region(id: String): Region? = regions[id]

    fun select(characterKey: String, ageYears: Int): Selection {
        val hash = stableHash(characterKey)
        // PeopleState currently has no biological-sex field. Keep it identity-stable for now;
        // when sex becomes simulation data this one line can consume it without changing assets.
        val sex = if ((hash and 1L) == 0L) Sex.FEMALE else Sex.MALE
        val youngHead = ((hash ushr 3) % 3L).toInt()
        val headIndex = if (ageYears >= 58) 3 else youngHead
        val garmentIndex = ((hash ushr 11) and 3L).toInt()
        return Selection(sex = sex, headIndex = headIndex, garmentIndex = garmentIndex)
    }

    private fun stableHash(value: String): Long {
        var hash = -3750763034362895579L
        value.forEach { c ->
            hash = hash xor c.code.toLong()
            hash *= 1099511628211L
        }
        return hash
    }
}

@Composable
internal fun RasterCharacterPortrait(
    characterKey: String,
    ageYears: Int,
    wardrobeState: WardrobeState,
    modifier: Modifier,
) {
    val assets = LocalContext.current.applicationContext.assets
    val atlas = remember(assets) { loadCharacterAtlas(assets) }
    val selection = remember(characterKey, ageYears) {
        RasterCharacterLibraryV01.select(characterKey, ageYears)
    }

    if (atlas == null) {
        ModularCharacterPortrait(
            characterKey = characterKey,
            ageYears = ageYears,
            wardrobeState = wardrobeState,
            modifier = modifier,
        )
        return
    }

    Canvas(modifier = modifier) {
        drawAtlasCell(atlas, requireRegion("background_neutral"))
        drawAtlasCell(atlas, requireRegion(selection.bodyId))
        if (wardrobeState != WardrobeState.UNDRESSED) {
            drawAtlasCell(atlas, requireRegion(selection.garmentId))
        }
        drawAtlasCell(atlas, requireRegion(selection.headId))
    }
}

private fun loadCharacterAtlas(assets: AssetManager): ImageBitmap? = runCatching {
    val encoded = assets.open(RasterCharacterLibraryV01.ATLAS_PATH)
        .bufferedReader()
        .use { it.readText() }
        .filterNot(Char::isWhitespace)
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

private fun requireRegion(id: String): RasterCharacterLibraryV01.Region =
    checkNotNull(RasterCharacterLibraryV01.region(id)) { "Missing raster character region: $id" }

private fun DrawScope.drawAtlasCell(
    atlas: ImageBitmap,
    region: RasterCharacterLibraryV01.Region,
) {
    val scale = min(
        size.width / RasterCharacterLibraryV01.CANVAS_WIDTH.toFloat(),
        size.height / RasterCharacterLibraryV01.CANVAS_HEIGHT.toFloat(),
    )
    val targetWidth = (RasterCharacterLibraryV01.CANVAS_WIDTH * scale).toInt().coerceAtLeast(1)
    val targetHeight = (RasterCharacterLibraryV01.CANVAS_HEIGHT * scale).toInt().coerceAtLeast(1)
    val left = ((size.width - targetWidth) / 2f).toInt()
    val top = ((size.height - targetHeight) / 2f).toInt()

    drawImage(
        image = atlas,
        srcOffset = IntOffset(region.x, region.y),
        srcSize = IntSize(region.width, region.height),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(targetWidth, targetHeight),
        filterQuality = FilterQuality.Medium,
    )
}
