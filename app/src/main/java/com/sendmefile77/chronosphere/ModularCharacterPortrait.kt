package com.sendmefile77.chronosphere

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sendmefile77.chronosphere.scene.WardrobeState
import kotlin.math.absoluteValue

internal object ChronosphereCharacterLibraryV01 {
    enum class Face { OVAL, LONG, SQUARE, HEART }
    enum class Eyes { ALMOND, DEEP_SET, NARROW, ROUND }
    enum class Brows { STRAIGHT, ARCHED, HEAVY, SOFT }
    enum class Nose { STRAIGHT, NARROW, BROAD, AQUILINE }
    enum class Mouth { NEUTRAL, FULL, THIN, WIDE }
    enum class Hair { CROP, SIDE_PART, WAVY, BOB, TIED_BACK, SHORT_CURL }
    enum class Frame { NARROW, BALANCED, BROAD }
    enum class Garment { SIMPLE, HIGH_COLLAR, COAT, TUNIC }

    data class Selection(
        val face: Face,
        val eyes: Eyes,
        val brows: Brows,
        val nose: Nose,
        val mouth: Mouth,
        val hair: Hair,
        val frame: Frame,
        val garment: Garment,
        val skin: Color,
        val hairColor: Color,
        val garmentColor: Color,
    )

    private val skins = listOf(
        Color(0xFFF0C9AE), Color(0xFFDDAA88), Color(0xFFC98B6B),
        Color(0xFFA9694F), Color(0xFF80503D), Color(0xFF56362E),
    )
    private val hairs = listOf(
        Color(0xFF1B1716), Color(0xFF34231D), Color(0xFF5A3926),
        Color(0xFF8A5F3C), Color(0xFFB89A72), Color(0xFF6B5650),
    )
    private val garments = listOf(
        Color(0xFF283848), Color(0xFF3E4A3D), Color(0xFF4C3436),
        Color(0xFF51445F), Color(0xFF5B5043), Color(0xFF253F43),
    )

    fun select(characterKey: String, ageYears: Int): Selection {
        val h = stableHash(characterKey)
        fun pick(size: Int, shift: Int): Int = ((h ushr shift).absoluteValue % size)
        val baseHair = hairs[pick(hairs.size, 20)]
        val agedHair = if (ageYears >= 58) Color(0xFF8C8580) else baseHair
        return Selection(
            face = Face.entries[pick(Face.entries.size, 0)],
            eyes = Eyes.entries[pick(Eyes.entries.size, 3)],
            brows = Brows.entries[pick(Brows.entries.size, 6)],
            nose = Nose.entries[pick(Nose.entries.size, 9)],
            mouth = Mouth.entries[pick(Mouth.entries.size, 12)],
            hair = Hair.entries[pick(Hair.entries.size, 15)],
            frame = Frame.entries[pick(Frame.entries.size, 18)],
            garment = Garment.entries[pick(Garment.entries.size, 22)],
            skin = skins[pick(skins.size, 25)],
            hairColor = agedHair,
            garmentColor = garments[pick(garments.size, 28)],
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

@Composable
internal fun ModularCharacterPortrait(
    characterKey: String,
    ageYears: Int,
    wardrobeState: WardrobeState,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val selection = remember(characterKey, ageYears) {
        ChronosphereCharacterLibraryV01.select(characterKey, ageYears)
    }
    Canvas(modifier = modifier) {
        drawPortrait(selection, ageYears, wardrobeState)
    }
}

private fun DrawScope.drawPortrait(
    p: ChronosphereCharacterLibraryV01.Selection,
    ageYears: Int,
    wardrobeState: WardrobeState,
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f

    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF1B2229), Color(0xFF11161B))),
        size = size,
    )

    val frameWidth = when (p.frame) {
        ChronosphereCharacterLibraryV01.Frame.NARROW -> w * 0.46f
        ChronosphereCharacterLibraryV01.Frame.BALANCED -> w * 0.54f
        ChronosphereCharacterLibraryV01.Frame.BROAD -> w * 0.62f
    }
    val shoulderY = h * 0.74f
    val garmentTop = h * 0.66f

    drawRoundRect(
        color = darken(p.skin, 0.90f),
        topLeft = Offset(cx - w * 0.065f, h * 0.52f),
        size = Size(w * 0.13f, h * 0.22f),
        cornerRadius = CornerRadius(w * 0.025f),
    )
    val shoulders = Path().apply {
        moveTo(cx - frameWidth * 0.52f, h)
        cubicTo(cx - frameWidth * 0.52f, shoulderY, cx - w * 0.15f, shoulderY, cx - w * 0.08f, garmentTop)
        lineTo(cx + w * 0.08f, garmentTop)
        cubicTo(cx + w * 0.15f, shoulderY, cx + frameWidth * 0.52f, shoulderY, cx + frameWidth * 0.52f, h)
        close()
    }
    val shoulderColor = if (wardrobeState == WardrobeState.UNDRESSED) p.skin else p.garmentColor
    drawPath(shoulders, shoulderColor)
    if (wardrobeState != WardrobeState.UNDRESSED) drawGarmentDetails(p, cx, garmentTop, shoulderY, frameWidth, h)

    val faceTop = h * 0.13f
    val faceHeight = h * 0.50f
    val faceWidth = when (p.face) {
        ChronosphereCharacterLibraryV01.Face.OVAL -> w * 0.31f
        ChronosphereCharacterLibraryV01.Face.LONG -> w * 0.29f
        ChronosphereCharacterLibraryV01.Face.SQUARE -> w * 0.34f
        ChronosphereCharacterLibraryV01.Face.HEART -> w * 0.32f
    }
    val facePath = facePath(p.face, cx, faceTop, faceWidth, faceHeight)
    drawPath(facePath, p.skin)
    drawPath(facePath, Color(0xFF281D1A).copy(alpha = 0.35f), style = Stroke(width = w * 0.005f))

    val shade = Path().apply {
        moveTo(cx + faceWidth * 0.18f, faceTop + faceHeight * 0.04f)
        cubicTo(cx + faceWidth * 0.48f, faceTop + faceHeight * 0.18f, cx + faceWidth * 0.48f, faceTop + faceHeight * 0.72f, cx + faceWidth * 0.16f, faceTop + faceHeight * 0.92f)
        cubicTo(cx + faceWidth * 0.30f, faceTop + faceHeight * 0.66f, cx + faceWidth * 0.30f, faceTop + faceHeight * 0.30f, cx + faceWidth * 0.18f, faceTop + faceHeight * 0.04f)
        close()
    }
    drawPath(shade, Color.Black.copy(alpha = 0.055f))

    drawHair(p, cx, faceTop, faceWidth, faceHeight)
    drawBrows(p, cx, faceTop, faceWidth, faceHeight)
    drawEyes(p, cx, faceTop, faceWidth, faceHeight)
    drawNose(p, cx, faceTop, faceWidth, faceHeight)
    drawMouth(p, cx, faceTop, faceWidth, faceHeight)
    if (ageYears >= 45) drawAgeLines(cx, faceTop, faceWidth, faceHeight, ageYears)
}

private fun facePath(face: ChronosphereCharacterLibraryV01.Face, cx: Float, top: Float, width: Float, height: Float): Path = Path().apply {
    val half = width / 2f
    moveTo(cx, top)
    when (face) {
        ChronosphereCharacterLibraryV01.Face.OVAL -> {
            cubicTo(cx - half * 0.82f, top, cx - half, top + height * 0.22f, cx - half * 0.86f, top + height * 0.58f)
            cubicTo(cx - half * 0.70f, top + height * 0.88f, cx - half * 0.30f, top + height, cx, top + height)
            cubicTo(cx + half * 0.30f, top + height, cx + half * 0.70f, top + height * 0.88f, cx + half * 0.86f, top + height * 0.58f)
            cubicTo(cx + half, top + height * 0.22f, cx + half * 0.82f, top, cx, top)
        }
        ChronosphereCharacterLibraryV01.Face.LONG -> {
            cubicTo(cx - half * 0.78f, top, cx - half * 0.94f, top + height * 0.20f, cx - half * 0.82f, top + height * 0.58f)
            cubicTo(cx - half * 0.67f, top + height * 0.90f, cx - half * 0.26f, top + height, cx, top + height)
            cubicTo(cx + half * 0.26f, top + height, cx + half * 0.67f, top + height * 0.90f, cx + half * 0.82f, top + height * 0.58f)
            cubicTo(cx + half * 0.94f, top + height * 0.20f, cx + half * 0.78f, top, cx, top)
        }
        ChronosphereCharacterLibraryV01.Face.SQUARE -> {
            cubicTo(cx - half * 0.90f, top, cx - half, top + height * 0.22f, cx - half * 0.92f, top + height * 0.62f)
            cubicTo(cx - half * 0.86f, top + height * 0.88f, cx - half * 0.38f, top + height, cx, top + height)
            cubicTo(cx + half * 0.38f, top + height, cx + half * 0.86f, top + height * 0.88f, cx + half * 0.92f, top + height * 0.62f)
            cubicTo(cx + half, top + height * 0.22f, cx + half * 0.90f, top, cx, top)
        }
        ChronosphereCharacterLibraryV01.Face.HEART -> {
            cubicTo(cx - half * 0.94f, top, cx - half, top + height * 0.26f, cx - half * 0.84f, top + height * 0.56f)
            cubicTo(cx - half * 0.58f, top + height * 0.84f, cx - half * 0.18f, top + height, cx, top + height)
            cubicTo(cx + half * 0.18f, top + height, cx + half * 0.58f, top + height * 0.84f, cx + half * 0.84f, top + height * 0.56f)
            cubicTo(cx + half, top + height * 0.26f, cx + half * 0.94f, top, cx, top)
        }
    }
    close()
}

private fun DrawScope.drawEyes(p: ChronosphereCharacterLibraryV01.Selection, cx: Float, top: Float, fw: Float, fh: Float) {
    val y = top + fh * 0.39f
    val gap = fw * 0.19f
    val ew = when (p.eyes) {
        ChronosphereCharacterLibraryV01.Eyes.NARROW -> fw * 0.22f
        ChronosphereCharacterLibraryV01.Eyes.ROUND -> fw * 0.19f
        else -> fw * 0.21f
    }
    val eh = when (p.eyes) {
        ChronosphereCharacterLibraryV01.Eyes.ROUND -> fh * 0.052f
        ChronosphereCharacterLibraryV01.Eyes.NARROW -> fh * 0.030f
        else -> fh * 0.042f
    }
    listOf(cx - gap, cx + gap).forEach { ex ->
        drawOval(Color(0xFFE9E2DA), Offset(ex - ew / 2f, y - eh / 2f), Size(ew, eh))
        val iris = if (p.eyes == ChronosphereCharacterLibraryV01.Eyes.DEEP_SET) Color(0xFF4A594E) else Color(0xFF66716A)
        drawCircle(iris, eh * 0.42f, Offset(ex, y))
        drawCircle(Color(0xFF17191A), eh * 0.20f, Offset(ex, y))
        drawLine(Color(0xFF30241F), Offset(ex - ew / 2f, y), Offset(ex + ew / 2f, y), size.width * 0.006f)
    }
}

private fun DrawScope.drawBrows(p: ChronosphereCharacterLibraryV01.Selection, cx: Float, top: Float, fw: Float, fh: Float) {
    val y = top + fh * 0.31f
    val gap = fw * 0.19f
    val bw = fw * 0.22f
    val stroke = if (p.brows == ChronosphereCharacterLibraryV01.Brows.HEAVY) size.width * 0.012f else size.width * 0.008f
    listOf(-1f, 1f).forEach { side ->
        val center = cx + gap * side
        val rise = when (p.brows) {
            ChronosphereCharacterLibraryV01.Brows.ARCHED -> fh * 0.025f
            ChronosphereCharacterLibraryV01.Brows.SOFT -> fh * 0.012f
            else -> 0f
        }
        drawLine(darken(p.hairColor, 0.72f), Offset(center - bw / 2f, y + rise * side), Offset(center + bw / 2f, y - rise * side), stroke)
    }
}

private fun DrawScope.drawNose(p: ChronosphereCharacterLibraryV01.Selection, cx: Float, top: Float, fw: Float, fh: Float) {
    val bridgeTop = top + fh * 0.39f
    val tipY = top + fh * 0.64f
    val width = when (p.nose) {
        ChronosphereCharacterLibraryV01.Nose.NARROW -> fw * 0.055f
        ChronosphereCharacterLibraryV01.Nose.BROAD -> fw * 0.105f
        else -> fw * 0.075f
    }
    val xShift = if (p.nose == ChronosphereCharacterLibraryV01.Nose.AQUILINE) fw * 0.018f else 0f
    drawLine(Color(0xFF6F4F42).copy(alpha = 0.52f), Offset(cx + xShift, bridgeTop), Offset(cx - width * 0.20f, tipY - fh * 0.02f), size.width * 0.0045f)
    drawLine(Color(0xFF6F4F42).copy(alpha = 0.46f), Offset(cx - width / 2f, tipY), Offset(cx + width / 2f, tipY), size.width * 0.004f)
}

private fun DrawScope.drawMouth(p: ChronosphereCharacterLibraryV01.Selection, cx: Float, top: Float, fw: Float, fh: Float) {
    val y = top + fh * 0.77f
    val mw = when (p.mouth) {
        ChronosphereCharacterLibraryV01.Mouth.WIDE -> fw * 0.31f
        ChronosphereCharacterLibraryV01.Mouth.THIN -> fw * 0.23f
        else -> fw * 0.27f
    }
    val upper = if (p.mouth == ChronosphereCharacterLibraryV01.Mouth.FULL) fh * 0.025f else fh * 0.015f
    val lower = if (p.mouth == ChronosphereCharacterLibraryV01.Mouth.FULL) fh * 0.032f else fh * 0.020f
    val lip = Color(0xFF9A5F5C)
    val path = Path().apply {
        moveTo(cx - mw / 2f, y)
        quadraticBezierTo(cx, y - upper, cx + mw / 2f, y)
        quadraticBezierTo(cx, y + lower, cx - mw / 2f, y)
        close()
    }
    drawPath(path, lip.copy(alpha = 0.86f))
    drawLine(darken(lip, 0.65f), Offset(cx - mw / 2f, y), Offset(cx + mw / 2f, y), size.width * 0.003f)
}

private fun DrawScope.drawHair(p: ChronosphereCharacterLibraryV01.Selection, cx: Float, top: Float, fw: Float, fh: Float) {
    val half = fw / 2f
    val hair = p.hairColor
    val scalp = Path().apply {
        moveTo(cx - half * 0.90f, top + fh * 0.28f)
        cubicTo(cx - half * 0.95f, top + fh * 0.05f, cx - half * 0.55f, top - fh * 0.08f, cx, top - fh * 0.06f)
        cubicTo(cx + half * 0.55f, top - fh * 0.08f, cx + half * 0.95f, top + fh * 0.05f, cx + half * 0.90f, top + fh * 0.28f)
        when (p.hair) {
            ChronosphereCharacterLibraryV01.Hair.SIDE_PART -> {
                cubicTo(cx + half * 0.55f, top + fh * 0.13f, cx + half * 0.10f, top + fh * 0.02f, cx - half * 0.12f, top + fh * 0.08f)
                cubicTo(cx - half * 0.40f, top + fh * 0.02f, cx - half * 0.60f, top + fh * 0.15f, cx - half * 0.90f, top + fh * 0.28f)
            }
            ChronosphereCharacterLibraryV01.Hair.CROP -> cubicTo(cx + half * 0.50f, top + fh * 0.12f, cx, top + fh * 0.09f, cx - half * 0.90f, top + fh * 0.28f)
            else -> cubicTo(cx + half * 0.50f, top + fh * 0.16f, cx, top + fh * 0.08f, cx - half * 0.90f, top + fh * 0.28f)
        }
        close()
    }
    drawPath(scalp, hair)
    when (p.hair) {
        ChronosphereCharacterLibraryV01.Hair.WAVY, ChronosphereCharacterLibraryV01.Hair.BOB -> {
            val length = if (p.hair == ChronosphereCharacterLibraryV01.Hair.BOB) fh * 0.64f else fh * 0.82f
            drawRoundRect(hair, Offset(cx - half * 1.05f, top + fh * 0.18f), Size(half * 0.40f, length), CornerRadius(half * 0.15f))
            drawRoundRect(hair, Offset(cx + half * 0.65f, top + fh * 0.18f), Size(half * 0.40f, length), CornerRadius(half * 0.15f))
        }
        ChronosphereCharacterLibraryV01.Hair.TIED_BACK -> drawCircle(hair, fw * 0.10f, Offset(cx, top + fh * 0.04f))
        ChronosphereCharacterLibraryV01.Hair.SHORT_CURL -> repeat(7) { i ->
            val x = cx - half * 0.72f + i * (fw * 0.12f)
            drawCircle(lighten(hair, if (i % 2 == 0) 1.12f else 0.93f), fw * 0.045f, Offset(x, top + fh * 0.04f + (i % 2) * fh * 0.025f))
        }
        else -> Unit
    }
}

private fun DrawScope.drawGarmentDetails(p: ChronosphereCharacterLibraryV01.Selection, cx: Float, top: Float, shoulderY: Float, frameWidth: Float, bottom: Float) {
    val seam = lighten(p.garmentColor, 1.28f).copy(alpha = 0.55f)
    when (p.garment) {
        ChronosphereCharacterLibraryV01.Garment.SIMPLE -> drawLine(seam, Offset(cx, top + (bottom - top) * 0.10f), Offset(cx, bottom), size.width * 0.003f)
        ChronosphereCharacterLibraryV01.Garment.HIGH_COLLAR -> drawRoundRect(darken(p.garmentColor, 0.78f), Offset(cx - size.width * 0.075f, top - size.height * 0.04f), Size(size.width * 0.15f, size.height * 0.09f), CornerRadius(size.width * 0.02f))
        ChronosphereCharacterLibraryV01.Garment.COAT -> {
            drawLine(seam, Offset(cx, top), Offset(cx, bottom), size.width * 0.006f)
            drawLine(seam.copy(alpha = 0.35f), Offset(cx - frameWidth * 0.30f, shoulderY), Offset(cx - frameWidth * 0.15f, bottom), size.width * 0.004f)
            drawLine(seam.copy(alpha = 0.35f), Offset(cx + frameWidth * 0.30f, shoulderY), Offset(cx + frameWidth * 0.15f, bottom), size.width * 0.004f)
        }
        ChronosphereCharacterLibraryV01.Garment.TUNIC -> {
            drawLine(seam, Offset(cx - size.width * 0.10f, top + size.height * 0.04f), Offset(cx, top + size.height * 0.11f), size.width * 0.004f)
            drawLine(seam, Offset(cx + size.width * 0.10f, top + size.height * 0.04f), Offset(cx, top + size.height * 0.11f), size.width * 0.004f)
        }
    }
}

private fun DrawScope.drawAgeLines(cx: Float, top: Float, fw: Float, fh: Float, ageYears: Int) {
    val strength = ((ageYears - 44).coerceAtMost(40) / 40f) * 0.25f
    val line = Color(0xFF674F45).copy(alpha = 0.16f + strength)
    listOf(-1f, 1f).forEach { side ->
        val ex = cx + fw * 0.19f * side
        drawLine(line, Offset(ex + fw * 0.10f * side, top + fh * 0.43f), Offset(ex + fw * 0.18f * side, top + fh * 0.45f), size.width * 0.0025f)
    }
    if (ageYears >= 60) drawLine(line, Offset(cx - fw * 0.18f, top + fh * 0.24f), Offset(cx + fw * 0.18f, top + fh * 0.24f), size.width * 0.002f)
}

private fun darken(color: Color, factor: Float): Color = Color((color.red * factor).coerceIn(0f, 1f), (color.green * factor).coerceIn(0f, 1f), (color.blue * factor).coerceIn(0f, 1f), color.alpha)
private fun lighten(color: Color, factor: Float): Color = Color((color.red * factor).coerceIn(0f, 1f), (color.green * factor).coerceIn(0f, 1f), (color.blue * factor).coerceIn(0f, 1f), color.alpha)
