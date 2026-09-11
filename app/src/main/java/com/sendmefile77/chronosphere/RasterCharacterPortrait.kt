package com.sendmefile77.chronosphere

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.sendmefile77.chronosphere.scene.WardrobeState

/**
 * Offline raster character constructor. Runtime never generates images and never uses network/AI.
 *
 * The previous v02 atlas in main was physically truncated, so BitmapFactory returned null and the
 * UI silently fell back to the old vector/cartoon portrait. v03 is a validated raster atlas and a
 * missing/corrupt pack is now surfaced as an asset error instead of silently changing art style.
 */
@Composable
internal fun RasterCharacterPortrait(
    characterKey: String,
    ageYears: Int,
    wardrobeState: WardrobeState,
    modifier: Modifier,
) {
    val context = LocalContext.current.applicationContext
    val atlas = remember(context) {
        decodeBase64AssetBitmap(context.assets, RasterCharacterLibraryV01.ATLAS_BASE64_PARTS)
    }
    val femaleUndressTorsoAtlas = remember(context) {
        decodeBase64AssetBitmap(context.assets, RasterCharacterLibraryV01.FEMALE_UNDRESS_TORSO_BASE64_PARTS)
    }
    val selection = remember(characterKey, ageYears) {
        RasterCharacterLibraryV01.select(characterKey, ageYears)
    }

    if (atlas == null) {
        BrokenRasterPackPortrait(modifier)
        return
    }

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF1A2229), Color(0xFF10151A))),
            size = size,
        )

        val useFemaleUndressTorso =
            ageYears >= 18 &&
                wardrobeState == WardrobeState.UNDRESSED &&
                selection.femaleFamily &&
                femaleUndressTorsoAtlas != null

        if (useFemaleUndressTorso) {
            drawFemaleUndressedCharacter(
                baseAtlas = atlas,
                torsoAtlas = femaleUndressTorsoAtlas!!,
                selection = selection,
            )
        } else {
            drawRasterCharacter(atlas, selection, wardrobeState)
        }
    }
}

private fun decodeBase64AssetBitmap(
    assets: android.content.res.AssetManager,
    paths: List<String>,
): Bitmap? = runCatching {
    val encoded = buildString {
        paths.forEach { path ->
            assets.open(path).bufferedReader().use { reader -> append(reader.readText()) }
        }
    }
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.takeIf { bitmap ->
        bitmap.width > 0 && bitmap.height > 0
    }
}.getOrNull()

@Composable
private fun BrokenRasterPackPortrait(modifier: Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF20191B), Color(0xFF120F11))),
            size = size,
        )
        val stroke = size.minDimension * 0.018f
        drawLine(
            color = Color(0xFFB65B62),
            start = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.35f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.65f),
            strokeWidth = stroke,
        )
        drawLine(
            color = Color(0xFFB65B62),
            start = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.35f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.65f),
            strokeWidth = stroke,
        )
    }
}

internal object RasterCharacterLibraryV01 {
    val ATLAS_BASE64_PARTS = listOf(
        "character_library/v0_1/character_parts_v03_480.b64.00",
        "character_library/v0_1/character_parts_v03_480.b64.01",
    )
    val FEMALE_UNDRESS_TORSO_BASE64_PARTS = listOf(
        "character_library/v0_1/female_undress_torsos_v01_384.b64.00",
    )

    const val CELL_W = 70
    const val CELL_H = 85
    const val VARIANTS = 6

    const val FEMALE_HEAD_Y = 0
    const val MALE_HEAD_Y = 85
    const val FEMALE_GARMENT_Y = 170
    const val MALE_GARMENT_Y = 255

    const val TORSO_Y = 340
    const val TORSO_W = 110
    const val TORSO_H = 140

    const val FEMALE_UNDRESS_TORSO_CELL_W = 128
    const val FEMALE_UNDRESS_TORSO_CELL_H = 150
    const val FEMALE_UNDRESS_TORSO_COLUMNS = 3
    const val FEMALE_UNDRESS_TORSO_VARIANTS = 9
    const val FEMALE_UNDRESS_MATURE_INDEX = 8

    data class Selection(
        val femaleFamily: Boolean,
        val headIndex: Int,
        val garmentIndex: Int,
        val femaleUndressTorsoIndex: Int,
    )

    fun select(characterKey: String, ageYears: Int): Selection {
        val hash = stableHash(characterKey)
        val youngHead = (hash ushr 3) % (VARIANTS - 1)
        val head = if (ageYears >= 60) VARIANTS - 1 else youngHead
        val youngTorso = (hash ushr 17) % (FEMALE_UNDRESS_TORSO_VARIANTS - 1)
        val undressTorso = if (ageYears >= 40) FEMALE_UNDRESS_MATURE_INDEX else youngTorso
        return Selection(
            femaleFamily = (hash and 1) == 0,
            headIndex = head,
            garmentIndex = (hash ushr 11) % VARIANTS,
            femaleUndressTorsoIndex = undressTorso,
        )
    }

    private fun stableHash(value: String): Int {
        var hash = 0x811C9DC5.toInt()
        value.forEach { c ->
            hash = hash xor c.code
            hash *= 16777619
        }
        return hash
    }
}

private fun DrawScope.drawFemaleUndressedCharacter(
    baseAtlas: Bitmap,
    torsoAtlas: Bitmap,
    selection: RasterCharacterLibraryV01.Selection,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val torsoIndex = selection.femaleUndressTorsoIndex
    val column = torsoIndex % RasterCharacterLibraryV01.FEMALE_UNDRESS_TORSO_COLUMNS
    val row = torsoIndex / RasterCharacterLibraryV01.FEMALE_UNDRESS_TORSO_COLUMNS
    val torsoSource = Rect(
        column * RasterCharacterLibraryV01.FEMALE_UNDRESS_TORSO_CELL_W,
        row * RasterCharacterLibraryV01.FEMALE_UNDRESS_TORSO_CELL_H,
        (column + 1) * RasterCharacterLibraryV01.FEMALE_UNDRESS_TORSO_CELL_W,
        (row + 1) * RasterCharacterLibraryV01.FEMALE_UNDRESS_TORSO_CELL_H,
    )
    val torsoTarget = logicalRect(35f, 150f, 285f, 480f)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(torsoAtlas, torsoSource, torsoTarget, paint)
    }

    val headX = selection.headIndex * RasterCharacterLibraryV01.CELL_W
    val headSource = Rect(
        headX,
        RasterCharacterLibraryV01.FEMALE_HEAD_Y,
        headX + RasterCharacterLibraryV01.CELL_W,
        RasterCharacterLibraryV01.FEMALE_HEAD_Y + RasterCharacterLibraryV01.CELL_H,
    )
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(
            baseAtlas,
            headSource,
            logicalRect(75f, 8f, 245f, 225f),
            paint,
        )
    }
}

private fun DrawScope.drawRasterCharacter(
    atlas: Bitmap,
    selection: RasterCharacterLibraryV01.Selection,
    wardrobeState: WardrobeState,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun drawPart(src: Rect, target: RectF, alpha: Int = 255) {
        paint.alpha = alpha
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(atlas, src, target, paint)
        }
    }

    val torsoX = if (selection.femaleFamily) 0 else RasterCharacterLibraryV01.TORSO_W
    val torsoSrc = Rect(
        torsoX,
        RasterCharacterLibraryV01.TORSO_Y,
        torsoX + RasterCharacterLibraryV01.TORSO_W,
        RasterCharacterLibraryV01.TORSO_Y + RasterCharacterLibraryV01.TORSO_H,
    )
    drawPart(torsoSrc, logicalRect(55f, 165f, 265f, 480f))

    if (wardrobeState != WardrobeState.UNDRESSED) {
        val garmentY = if (selection.femaleFamily) {
            RasterCharacterLibraryV01.FEMALE_GARMENT_Y
        } else {
            RasterCharacterLibraryV01.MALE_GARMENT_Y
        }
        val garmentX = selection.garmentIndex * RasterCharacterLibraryV01.CELL_W
        val garmentSrc = Rect(
            garmentX,
            garmentY,
            garmentX + RasterCharacterLibraryV01.CELL_W,
            garmentY + RasterCharacterLibraryV01.CELL_H,
        )
        val garmentAlpha = when (wardrobeState) {
            WardrobeState.PARTIAL, WardrobeState.DAMAGED -> 220
            else -> 255
        }
        drawPart(garmentSrc, logicalRect(60f, 205f, 260f, 465f), garmentAlpha)
    }

    val headY = if (selection.femaleFamily) {
        RasterCharacterLibraryV01.FEMALE_HEAD_Y
    } else {
        RasterCharacterLibraryV01.MALE_HEAD_Y
    }
    val headX = selection.headIndex * RasterCharacterLibraryV01.CELL_W
    val headSrc = Rect(
        headX,
        headY,
        headX + RasterCharacterLibraryV01.CELL_W,
        headY + RasterCharacterLibraryV01.CELL_H,
    )
    drawPart(headSrc, logicalRect(75f, 8f, 245f, 225f))
}

private fun DrawScope.logicalRect(left: Float, top: Float, right: Float, bottom: Float): RectF {
    val sx = size.width / LOGICAL_W
    val sy = size.height / LOGICAL_H
    return RectF(left * sx, top * sy, right * sx, bottom * sy)
}

private const val LOGICAL_W = 320f
private const val LOGICAL_H = 480f
