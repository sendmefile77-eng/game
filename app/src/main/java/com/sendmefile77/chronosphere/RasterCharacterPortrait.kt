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
 * Real raster character constructor backed by a local atlas physically cut from the approved
 * mature semi-realistic character-library board. There is no runtime AI or network access.
 *
 * v0.2 currently assembles a stable head + base torso + wardrobe layer. The same atlas also
 * contains eye / nose / mouth samples reserved for the next finer-grained face pass.
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
    const val ATLAS_PATH = "character_library/v0_1/character_parts_v02.webp"

    // The source library was packed at 1024x1024 and downsampled to this runtime atlas.
    const val ATLAS_SIZE = 320
    const val CELL_W = 40
    const val CELL_H = 50
    const val VARIANTS = 6

    const val FEMALE_HEAD_Y = 0
    const val MALE_HEAD_Y = 50
    const val FEMALE_GARMENT_Y = 100
    const val MALE_GARMENT_Y = 150

    // Headless torso cells retained at the lower-left of the atlas.
    const val TORSO_Y = 200
    const val TORSO_W = 60
    const val TORSO_H = 100

    data class Selection(
        val femaleFamily: Boolean,
        val headIndex: Int,
        val garmentIndex: Int,
    )

    fun select(characterKey: String): Selection {
        val hash = stableHash(characterKey)
        return Selection(
            femaleFamily = (hash and 1) == 0,
            headIndex = (hash ushr 3) % VARIANTS,
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

    // 1. Canonical headless body. It remains identical when wardrobe changes.
    val torsoX = if (selection.femaleFamily) 0 else RasterCharacterLibraryV01.TORSO_W
    val torsoSrc = Rect(
        torsoX,
        RasterCharacterLibraryV01.TORSO_Y,
        torsoX + RasterCharacterLibraryV01.TORSO_W,
        RasterCharacterLibraryV01.TORSO_Y + RasterCharacterLibraryV01.TORSO_H,
    )
    drawPart(torsoSrc, dst(55f, 165f, 265f, 480f))

    // 2. Real wardrobe cutout from the source art board. UNDRESSED means the base underwear/body
    // remains visible; DRESSED/PARTIAL/DAMAGED use the same identity and only change this layer.
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
        drawPart(garmentSrc, dst(60f, 210f, 260f, 460f), garmentAlpha)
    }

    // 3. Identity head always goes last. The deterministic index is derived only from person.id,
    // so changing age display / wardrobe / scene cannot silently create another person.
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
    drawPart(headSrc, dst(75f, 10f, 245f, 230f))
}

private const val LOGICAL_W = 320f
private const val LOGICAL_H = 480f
