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
 * Real offline raster constructor backed by local atlases physically cut from the approved
 * mature semi-realistic character-library boards. No runtime AI and no network access.
 *
 * Ordinary portraits keep the layered head/body/wardrobe constructor. Adult female-family
 * UNDRESSED cards can use focused normalized local anatomy atlases supplied by the user.
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
    val femaleLowerFrontAtlas = remember(context) {
        runCatching {
            context.assets.open(RasterCharacterLibraryV01.FEMALE_LOWER_FRONT_ATLAS_PATH).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
    val femaleUpperTorsoAtlas = remember(context) {
        runCatching {
            context.assets.open(RasterCharacterLibraryV01.FEMALE_UPPER_TORSO_ATLAS_PATH).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
    val selection = remember(characterKey, ageYears) {
        RasterCharacterLibraryV01.select(characterKey, ageYears)
    }

    // User-supplied focused atlases are adult, female-only and UNDRESSED-only.
    if (
        ageYears >= 18 &&
        wardrobeState == WardrobeState.UNDRESSED &&
        selection.femaleFamily
    ) {
        if (femaleUpperTorsoAtlas != null) {
            Canvas(modifier = modifier) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF1A2229), Color(0xFF10151A))),
                    size = size,
                )
                drawFemaleUpperTorso(femaleUpperTorsoAtlas, selection.upperTorsoIndex)
            }
            return
        }
        if (femaleLowerFrontAtlas != null) {
            Canvas(modifier = modifier) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF1A2229), Color(0xFF10151A))),
                    size = size,
                )
                drawFemaleLowerFront(femaleLowerFrontAtlas, selection.lowerFrontIndex)
            }
            return
        }
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
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF1A2229), Color(0xFF10151A))),
            size = size,
        )
        drawRasterCharacter(atlas, selection, wardrobeState)
    }
}

internal object RasterCharacterLibraryV01 {
    const val ATLAS_PATH = "character_library/v0_1/character_parts_v02.webp"
    const val FEMALE_LOWER_FRONT_ATLAS_PATH =
        "character_library/v0_1/female_lower_front_v01.webp"
    const val FEMALE_UPPER_TORSO_ATLAS_PATH =
        "character_library/v0_1/female_upper_torso_v01.webp"

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

    const val FEMALE_LOWER_FRONT_CELL_W = 240
    const val FEMALE_LOWER_FRONT_CELL_H = 256
    const val FEMALE_LOWER_FRONT_COLUMNS = 4
    const val FEMALE_LOWER_FRONT_VARIANTS = 12

    const val FEMALE_UPPER_TORSO_CELL_W = 128
    const val FEMALE_UPPER_TORSO_CELL_H = 150
    const val FEMALE_UPPER_TORSO_COLUMNS = 3
    const val FEMALE_UPPER_TORSO_VARIANTS = 9
    const val FEMALE_UPPER_TORSO_MATURE_INDEX = 8

    data class Selection(
        val femaleFamily: Boolean,
        val headIndex: Int,
        val garmentIndex: Int,
        val lowerFrontIndex: Int,
        val upperTorsoIndex: Int,
    )

    fun select(characterKey: String, ageYears: Int): Selection {
        val hash = stableHash(characterKey)
        val youngHead = (hash ushr 3) % (VARIANTS - 1)
        // The sixth head in each row is the mature/elder variant from the same source board.
        val head = if (ageYears >= 60) VARIANTS - 1 else youngHead
        val youngUpperTorso = (hash ushr 19) % (FEMALE_UPPER_TORSO_VARIANTS - 1)
        return Selection(
            femaleFamily = (hash and 1) == 0,
            headIndex = head,
            garmentIndex = (hash ushr 11) % VARIANTS,
            lowerFrontIndex = (hash ushr 16) % FEMALE_LOWER_FRONT_VARIANTS,
            // The ninth selected cell is the supplied mature-40s variant.
            upperTorsoIndex =
                if (ageYears >= 40) FEMALE_UPPER_TORSO_MATURE_INDEX else youngUpperTorso,
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

private fun DrawScope.drawFemaleUpperTorso(atlas: Bitmap, variantIndex: Int) {
    val column = variantIndex % RasterCharacterLibraryV01.FEMALE_UPPER_TORSO_COLUMNS
    val row = variantIndex / RasterCharacterLibraryV01.FEMALE_UPPER_TORSO_COLUMNS
    val left = column * RasterCharacterLibraryV01.FEMALE_UPPER_TORSO_CELL_W
    val top = row * RasterCharacterLibraryV01.FEMALE_UPPER_TORSO_CELL_H
    val source = Rect(
        left,
        top,
        left + RasterCharacterLibraryV01.FEMALE_UPPER_TORSO_CELL_W,
        top + RasterCharacterLibraryV01.FEMALE_UPPER_TORSO_CELL_H,
    )
    drawFocusedAtlasCell(
        atlas = atlas,
        source = source,
        sourceWidth = RasterCharacterLibraryV01.FEMALE_UPPER_TORSO_CELL_W,
        sourceHeight = RasterCharacterLibraryV01.FEMALE_UPPER_TORSO_CELL_H,
    )
}

private fun DrawScope.drawFemaleLowerFront(atlas: Bitmap, variantIndex: Int) {
    val column = variantIndex % RasterCharacterLibraryV01.FEMALE_LOWER_FRONT_COLUMNS
    val row = variantIndex / RasterCharacterLibraryV01.FEMALE_LOWER_FRONT_COLUMNS
    val left = column * RasterCharacterLibraryV01.FEMALE_LOWER_FRONT_CELL_W
    val top = row * RasterCharacterLibraryV01.FEMALE_LOWER_FRONT_CELL_H
    val source = Rect(
        left,
        top,
        left + RasterCharacterLibraryV01.FEMALE_LOWER_FRONT_CELL_W,
        top + RasterCharacterLibraryV01.FEMALE_LOWER_FRONT_CELL_H,
    )
    drawFocusedAtlasCell(
        atlas = atlas,
        source = source,
        sourceWidth = RasterCharacterLibraryV01.FEMALE_LOWER_FRONT_CELL_W,
        sourceHeight = RasterCharacterLibraryV01.FEMALE_LOWER_FRONT_CELL_H,
    )
}

private fun DrawScope.drawFocusedAtlasCell(
    atlas: Bitmap,
    source: Rect,
    sourceWidth: Int,
    sourceHeight: Int,
) {
    // Fit without independent X/Y stretching so anatomy is not deformed by phone aspect ratio.
    val scale = minOf(
        size.width / sourceWidth,
        size.height / sourceHeight,
    )
    val width = sourceWidth * scale
    val height = sourceHeight * scale
    val target = RectF(
        (size.width - width) / 2f,
        (size.height - height) / 2f,
        (size.width + width) / 2f,
        (size.height + height) / 2f,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(atlas, source, target, paint)
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
