package com.sendmefile77.chronosphere

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
 * Real offline raster constructor backed by a local atlas physically cut from the approved
 * mature semi-realistic character-library board. No runtime AI and no network access.
 *
 * Assembly order is stable: base torso -> wardrobe -> identity head. The atlas also keeps
 * separate eye / nose / mouth samples for the next finer-grained face pass.
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
        runCatching {
            context.assets.open(RasterCharacterLibraryV01.ATLAS_PATH).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
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

    val selection = remember(characterKey, ageYears) {
        RasterCharacterLibraryV01.select(characterKey, ageYears)
    }
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF1A2229), Color(0xFF10151A))),
            size = size,
        )
        drawRasterCharacter(atlas, selection, wardrobeState)
    }
}

internal object RasterCharacterLibraryV01 {
    const val ATLAS_PATH = "character_library/v0_1/character_parts_v02.webp"
    const val ATLAS_SIZE = 320
    const val CELL_W = 40
    const val CELL_H = 50
    const val VARIANTS = 6

    const val FEMALE_HEAD_Y = 0
    const val MALE_HEAD_Y = 50
    const val FEMALE_GARMENT_Y = 100
    const val MALE_GARMENT_Y = 150

    const val TORSO_Y = 200
    const val TORSO_W = 60
    const val TORSO_H = 100

    data class Selection(
        val femaleFamily: Boolean,
        val headIndex: Int,
        val garmentIndex: Int,
    )

    fun select(characterKey: String, ageYears: Int): Selection {
        val hash = stableHash(characterKey)
        val youngHead = (hash ushr 3) % (VARIANTS - 1)
        // The sixth head in each row is the mature/elder variant from the same source board.
        val head = if (ageYears >= 60) VARIANTS - 1 else youngHead
        return Selection(
            femaleFamily = (hash and 1) == 0,
            headIndex = head,
            garmentIndex = (hash ushr 11) % VARIANTS,
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

private fun DrawScope.drawRasterCharacter(
    atlas: Bitmap,
    selection: RasterCharacterLibraryV01.Selection,
    wardrobeState: WardrobeState,
) {
    val sx = size.width / LOGICAL_W
    val sy = size.height / LOGICAL_H
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun dst(left: Float, top: Float, right: Float, bottom: Float): RectF = RectF(
        left * sx,
        top * sy,
        right * sx,
        bottom * sy,
    )

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
    drawPart(torsoSrc, dst(55f, 165f, 265f, 480f))

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
        drawPart(garmentSrc, dst(60f, 205f, 260f, 465f), garmentAlpha)
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
    drawPart(headSrc, dst(75f, 10f, 245f, 235f))
}

private const val LOGICAL_W = 320f
private const val LOGICAL_H = 480f
