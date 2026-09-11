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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.sendmefile77.chronosphere.scene.WardrobeState

/**
 * Offline portrait renderer backed by the actual approved character-board artwork.
 * The atlas contains transparent, normalized crops cut from the supplied board itself.
 * No procedural/cartoon fallback is used here.
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
            context.assets.open(CharacterBoardRuntime.ATLAS_PATH).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
    val selection = remember(characterKey, ageYears) {
        CharacterBoardRuntime.select(characterKey, ageYears)
    }

    Canvas(modifier = modifier) {
        drawRect(Color(0xFF0F171C), size = size)
        if (atlas == null) {
            // Do not silently substitute the old cartoon renderer. A broken asset pack must be visible.
            drawRect(Color(0xFF26171B), size = size)
            return@Canvas
        }
        drawBoardPortrait(atlas, selection, wardrobeState)
    }
}

internal object CharacterBoardRuntime {
    const val ATLAS_PATH = "character_library/v0_2/character_board_runtime_alpha_v01.webp"
    const val HEAD_VARIANTS = 5
    const val GARMENT_VARIANTS = 6

    // Runtime atlas layout, in pixels.
    const val HEAD_W = 100
    const val HEAD_H = 150
    const val FEMALE_HEAD_Y = 0
    const val MALE_HEAD_Y = 150

    const val TORSO_W = 150
    const val TORSO_H = 300
    const val FEMALE_TORSO_X = 500
    const val MALE_TORSO_X = 650
    const val TORSO_Y = 0

    const val GARMENT_W = 120
    const val GARMENT_H = 130
    const val FEMALE_GARMENT_Y = 300
    const val MALE_GARMENT_Y = 430

    data class Selection(
        val femaleFamily: Boolean,
        val headIndex: Int,
        val garmentIndex: Int,
    )

    fun select(characterKey: String, ageYears: Int): Selection {
        val hash = stableHash(characterKey)
        val head = if (ageYears >= 60) HEAD_VARIANTS - 1 else (hash ushr 3) % (HEAD_VARIANTS - 1)
        return Selection(
            femaleFamily = (hash and 1) == 0,
            headIndex = head,
            garmentIndex = (hash ushr 11) % GARMENT_VARIANTS,
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

private fun DrawScope.drawBoardPortrait(
    atlas: Bitmap,
    selection: CharacterBoardRuntime.Selection,
    wardrobeState: WardrobeState,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun drawPart(src: Rect, target: RectF, alpha: Int = 255) {
        paint.alpha = alpha
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(atlas, src, target, paint)
        }
    }

    val headY = if (selection.femaleFamily) {
        CharacterBoardRuntime.FEMALE_HEAD_Y
    } else {
        CharacterBoardRuntime.MALE_HEAD_Y
    }
    val headX = selection.headIndex * CharacterBoardRuntime.HEAD_W
    val headSrc = Rect(
        headX,
        headY,
        headX + CharacterBoardRuntime.HEAD_W,
        headY + CharacterBoardRuntime.HEAD_H,
    )

    if (wardrobeState == WardrobeState.UNDRESSED) {
        val torsoX = if (selection.femaleFamily) {
            CharacterBoardRuntime.FEMALE_TORSO_X
        } else {
            CharacterBoardRuntime.MALE_TORSO_X
        }
        // Use the upper 70% of the normalized base-body crop. A bust/three-quarter composition
        // keeps adult proportions natural inside the landscape phone card.
        val torsoSrc = Rect(
            torsoX,
            CharacterBoardRuntime.TORSO_Y,
            torsoX + CharacterBoardRuntime.TORSO_W,
            CharacterBoardRuntime.TORSO_Y + 210,
        )
        drawPart(torsoSrc, logicalRect(45f, 150f, 275f, 480f))
        drawPart(headSrc, logicalRect(100f, 5f, 220f, 185f))
    } else {
        val garmentY = if (selection.femaleFamily) {
            CharacterBoardRuntime.FEMALE_GARMENT_Y
        } else {
            CharacterBoardRuntime.MALE_GARMENT_Y
        }
        val garmentX = selection.garmentIndex * CharacterBoardRuntime.GARMENT_W
        val garmentSrc = Rect(
            garmentX,
            garmentY,
            garmentX + CharacterBoardRuntime.GARMENT_W,
            garmentY + CharacterBoardRuntime.GARMENT_H,
        )
        val garmentAlpha = when (wardrobeState) {
            WardrobeState.PARTIAL, WardrobeState.DAMAGED -> 228
            else -> 255
        }

        // Garment first, then head. Both are transparent cut-outs from the same approved sheet,
        // so the neck/collar overlap reads as one portrait rather than as two rectangular crops.
        drawPart(garmentSrc, logicalRect(20f, 180f, 300f, 480f), garmentAlpha)
        drawPart(headSrc, logicalRect(85f, 5f, 235f, 230f))
    }
}

private fun DrawScope.logicalRect(left: Float, top: Float, right: Float, bottom: Float): RectF {
    val sx = size.width / 320f
    val sy = size.height / 480f
    return RectF(left * sx, top * sy, right * sx, bottom * sy)
}
