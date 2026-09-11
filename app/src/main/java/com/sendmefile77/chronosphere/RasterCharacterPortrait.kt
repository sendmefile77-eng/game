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
 * Real raster constructor backed by the sliced local library in app assets.
 *
 * The atlas was cut from the approved adult semi-realistic style board. Runtime never uses AI:
 * it only selects deterministic local parts and composites them on-device.
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
            context.assets.open(RasterCharacterLibraryV01.ATLAS_PATH).use(BitmapFactory::decodeStream)
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

    val selection = remember(characterKey) { RasterCharacterLibraryV01.select(characterKey) }
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF1A2229), Color(0xFF10151A)),
            ),
            size = size,
        )
        drawRasterCharacter(atlas, selection, wardrobeState)
    }
}

internal object RasterCharacterLibraryV01 {
    const val ATLAS_PATH = "character_library/v0_1/character_parts_v01.webp"
    const val CELL_W = 96
    const val CELL_H = 120
    const val BODY_Y = 480
    const val BODY_H = 240
    const val VARIANTS = 6

    data class Selection(
        val femaleFamily: Boolean,
        val headIndex: Int,
        val garmentIndex: Int,
    )

    fun select(characterKey: String): Selection {
        val hash = stableHash(characterKey)
        return Selection(
            femaleFamily = (hash and 1) == 0,
            headIndex = ((hash ushr 3) % VARIANTS),
            garmentIndex = ((hash ushr 11) % VARIANTS),
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

    val familyColumn = if (selection.femaleFamily) 0 else 1
    val bodyX = familyColumn * RasterCharacterLibraryV01.CELL_W

    // Reuse only the torso/shoulder section of the canonical base body, so the selected head
    // remains the person's identity in both dressed and undressed states.
    val bodySrc = Rect(
        bodyX,
        RasterCharacterLibraryV01.BODY_Y + 38,
        bodyX + RasterCharacterLibraryV01.CELL_W,
        RasterCharacterLibraryV01.BODY_Y + 188,
    )
    drawPart(bodySrc, dst(42f, 158f, 278f, 480f))

    if (wardrobeState != WardrobeState.UNDRESSED) {
        val garmentRow = if (selection.femaleFamily) 2 else 3
        val garmentX = selection.garmentIndex * RasterCharacterLibraryV01.CELL_W
        val garmentY = garmentRow * RasterCharacterLibraryV01.CELL_H
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
        drawPart(garmentSrc, dst(46f, 168f, 274f, 458f), garmentAlpha)
    }

    val headRow = if (selection.femaleFamily) 0 else 1
    val headX = selection.headIndex * RasterCharacterLibraryV01.CELL_W
    val headY = headRow * RasterCharacterLibraryV01.CELL_H
    val headSrc = Rect(
        headX,
        headY,
        headX + RasterCharacterLibraryV01.CELL_W,
        headY + RasterCharacterLibraryV01.CELL_H,
    )
    drawPart(headSrc, dst(64f, 14f, 256f, 286f))
}

private const val LOGICAL_W = 320f
private const val LOGICAL_H = 480f
