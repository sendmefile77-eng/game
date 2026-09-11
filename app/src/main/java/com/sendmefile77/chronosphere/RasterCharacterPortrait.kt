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
            context.assets.open(CharacterBoardRuntime.ATLAS_PATH).use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
    val selection = remember(characterKey, ageYears) {
        CharacterBoardRuntime.select(characterKey, ageYears)
    }

    Canvas(modifier = modifier) {
        drawRect(Color(0xFF0F171C), size = size)
        if (atlas == null) {
            drawRect(Color(0xFF26171B), size = size)
            return@Canvas
        }
        drawBoardPortrait(atlas, selection, wardrobeState)
    }
}

internal object CharacterBoardRuntime {
    const val ATLAS_PATH = "character_library/v0_2/character_board_runtime_v01.webp"
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

    // Head is taken directly from the approved HEADS rows.
    val headY = if (selection.femaleFamily) CharacterBoardRuntime.FEMALE_HEAD_Y else CharacterBoardRuntime.MALE_HEAD_Y
    val headX = selection.headIndex * CharacterBoardRuntime.HEAD_W
    val headSrc = Rect(
        headX,
        headY,
        headX + CharacterBoardRuntime.HEAD_W,
        headY + CharacterBoardRuntime.HEAD_H,
    )

    if (wardrobeState == WardrobeState.UNDRESSED) {
        // Use the actual base-body front render from the same board. This is deliberately not a
        // procedural body: it keeps the art direction of the approved library intact.
        val torsoX = if (selection.femaleFamily) CharacterBoardRuntime.FEMALE_TORSO_X else CharacterBoardRuntime.MALE_TORSO_X
        val torsoSrc = Rect(
            torsoX,
            CharacterBoardRuntime.TORSO_Y,
            torsoX + CharacterBoardRuntime.TORSO_W,
            CharacterBoardRuntime.TORSO_Y + CharacterBoardRuntime.TORSO_H,
        )
        drawPart(torsoSrc, fitRect(88f, 135f, 232f, 480f))
        drawPart(headSrc, fitRect(90f, 4f, 230f, 215f))
    } else {
        // Clothes are also direct crops from the board. Render torso/clothes as a bust so the
        // dark source background stays visually continuous instead of producing a fake paper-doll seam.
        val garmentY = if (selection.femaleFamily) CharacterBoardRuntime.FEMALE_GARMENT_Y else CharacterBoardRuntime.MALE_GARMENT_Y
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
        drawPart(headSrc, fitRect(78f, 0f, 242f, 235f))
        drawPart(garmentSrc, fitRect(42f, 205f, 278f, 478f), garmentAlpha)
    }
}

private fun DrawScope.fitRect(left: Float, top: Float, right: Float, bottom: Float): RectF {
    val sx = size.width / 320f
    val sy = size.height / 480f
    return RectF(left * sx, top * sy, right * sx, bottom * sy)
}
