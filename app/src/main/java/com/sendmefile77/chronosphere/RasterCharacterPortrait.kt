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
 * Offline portrait renderer backed by the approved character-board artwork.
 * The atlas contains transparent, normalized crops cut from the supplied board itself.
 * No procedural/cartoon fallback is used here.
 */
@Composable
internal fun RasterCharacterPortrait(
    characterKey: String,
    ageYears: Int,
    wardrobeState: WardrobeState,
    visualTags: Set<String> = emptySet(),
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
    val selection = remember(characterKey, ageYears, visualTags) {
        CharacterBoardRuntime.select(characterKey, ageYears, visualTags)
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
        val clothTag: String? = null,
        val jewelryTag: String? = null,
    )

    fun select(characterKey: String, ageYears: Int, visualTags: Set<String> = emptySet()): Selection {
        val hash = stableHash(characterKey)
        val head = if (ageYears >= 60) HEAD_VARIANTS - 1 else (hash ushr 3) % (HEAD_VARIANTS - 1)
        val cloth = tagValue(visualTags, "cloth:")
        val jewelry = tagValue(visualTags, "jewel:")
        val garment = garmentFor(cloth, jewelry, hash)
        return Selection(
            femaleFamily = (hash and 1) == 0,
            headIndex = head,
            garmentIndex = garment,
            clothTag = cloth,
            jewelryTag = jewelry,
        )
    }

    private fun garmentFor(cloth: String?, jewelry: String?, identityHash: Int): Int {
        if (cloth == null && jewelry == null) return (identityHash ushr 11) % GARMENT_VARIANTS
        val normalized = cloth.orEmpty().removePrefix("mended-")
        val base = when {
            normalized.contains("hide") -> 0
            normalized.contains("linen") || normalized.contains("plain") -> 1
            normalized.contains("wool") -> 2
            normalized.contains("status") || normalized.contains("layered") -> 3
            normalized.contains("mill") || normalized.contains("tailored") -> 4
            normalized.contains("sealed") || normalized.contains("duty") -> 5
            else -> positiveMod(stableHash(normalized.ifBlank { jewelry.orEmpty() }), GARMENT_VARIANTS)
        }
        // The current approved atlas has no independent jewellery layer. We never draw fake jewellery;
        // instead the tag deterministically selects between neighboring compatible board variants.
        return if (jewelry.isNullOrBlank()) base else {
            positiveMod(base + positiveMod(stableHash(jewelry), 2), GARMENT_VARIANTS)
        }
    }

    private fun tagValue(tags: Set<String>, prefix: String): String? = tags.asSequence()
        .filter { it.startsWith(prefix) && it.length > prefix.length }
        .map { it.removePrefix(prefix) }
        .sorted()
        .firstOrNull()

    private fun positiveMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus

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

        drawPart(garmentSrc, logicalRect(20f, 180f, 300f, 480f), garmentAlpha)
        drawPart(headSrc, logicalRect(85f, 5f, 235f, 230f))
    }
}

private fun DrawScope.logicalRect(left: Float, top: Float, right: Float, bottom: Float): RectF {
    val sx = size.width / 320f
    val sy = size.height / 480f
    return RectF(left * sx, top * sy, right * sx, bottom * sy)
}
