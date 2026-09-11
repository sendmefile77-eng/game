package com.sendmefile77.chronosphere.adult

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Offline 1024x1536 precomposed adult rasters.
 * Invoked from Gradle so the classpath contains real PNGs without storing binaries in git.
 */
object AdultPackGenerator {
    const val WIDTH = 1024
    const val HEIGHT = 1536

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.first())
        writeAll(out)
    }

    fun writeAll(outDir: File) {
        val recipeDir = File(outDir, "scene_packs/adult/recipe")
        recipeDir.mkdirs()
        specs().forEach { spec ->
            File(recipeDir, spec.id + ".png").writeBytes(render(spec))
        }
    }

    internal fun specs(): List<PackSpec> = listOf(
        PackSpec("card.dressed.baseline", 0x161826, 0x244858, dressed = true),
        PackSpec("card.undressed.baseline", 0x161826, 0xC69A7A, dressed = false),
        PackSpec("card.dressed.hybrid", 0x1C1228, 0x462A58, dressed = true, tint = 0x503C78),
        PackSpec("card.undressed.hybrid", 0x1C1228, 0xA078AA, dressed = false, tint = 0x503C78),
        PackSpec("card.undressed.quad", 0x14101C, 0xC69A7A, dressed = false, arms = 4),
        PackSpec("card.undressed.tailed", 0x14101C, 0xC69A7A, dressed = false, tail = true),
        PackSpec("card.undressed.scaled", 0x101C16, 0x588A6C, dressed = false, scales = true),
        PackSpec("card.dressed.scaled", 0x101C16, 0x28503C, dressed = true, scales = true),
        PackSpec("courtship.garden.clothed", 0x182A1E, 0x244858, dressed = true, pair = true),
        PackSpec("union.chamber.missionary", 0x201216, 0xC69A7A, dressed = false, pair = true),
        PackSpec("scandal.court.exposure", 0x2A241E, 0xC69A7A, dressed = false),
        PackSpec("fertility.shrine.creampie", 0x2A1410, 0xC69A7A, dressed = false, pair = true),
        PackSpec("rough.bed.pounding", 0x160C0C, 0xC69A7A, dressed = false, pair = true),
        PackSpec("power.wartent.claim", 0x1E160E, 0xC69A7A, dressed = false, pair = true),
        PackSpec("orgy.feast.group", 0x201010, 0xC69A7A, dressed = false, group = true),
        PackSpec("public.plaza.exhibition", 0x34373C, 0xC69A7A, dressed = false, pair = true),
        PackSpec("bondage.cellar.restraint", 0x0E0A0A, 0xC69A7A, dressed = false),
        PackSpec("sacred.temple.union", 0x181628, 0xC69A7A, dressed = false, pair = true),
        PackSpec("union.hybrid.blend", 0x1A1022, 0xA078AA, dressed = false, pair = true, tint = 0x5A3282),
        PackSpec("union.divergent.quad", 0x16101C, 0xC69A7A, dressed = false, pair = true, arms = 4),
        PackSpec("hold.threshold.silhouette", 0x060608, 0x0A0A0C, silhouette = true),
        PackSpec("fallback.silhouette.morph", 0x08080C, 0x0C0C10, silhouette = true),
        PackSpec("fallback.silhouette.undressed", 0x0C080A, 0x120C0E, silhouette = true, dressed = false),
    )

    internal data class PackSpec(
        val id: String,
        val bg: Int,
        val fg: Int,
        val dressed: Boolean = true,
        val arms: Int = 2,
        val tail: Boolean = false,
        val scales: Boolean = false,
        val pair: Boolean = false,
        val group: Boolean = false,
        val tint: Int? = null,
        val silhouette: Boolean = false,
    )

    internal fun render(spec: PackSpec): ByteArray {
        val pixels = IntArray(WIDTH * HEIGHT) { spec.bg or 0xFF000000.toInt() }
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
            val l = x0.coerceIn(0, WIDTH - 1)
            val r = x1.coerceIn(0, WIDTH - 1)
            val t = y0.coerceIn(0, HEIGHT - 1)
            val b = y1.coerceIn(0, HEIGHT - 1)
            for (y in t..b) {
                val row = y * WIDTH
                for (x in l..r) pixels[row + x] = color or 0xFF000000.toInt()
            }
        }
        fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int, color: Int) {
            val rx2 = rx * rx
            val ry2 = ry * ry
            for (y in (cy - ry).coerceAtLeast(0)..(cy + ry).coerceAtMost(HEIGHT - 1)) {
                val dy = y - cy
                val row = y * WIDTH
                for (x in (cx - rx).coerceAtLeast(0)..(cx + rx).coerceAtMost(WIDTH - 1)) {
                    val dx = x - cx
                    if (dx * dx * ry2 + dy * dy * rx2 <= rx2 * ry2) pixels[row + x] = color or 0xFF000000.toInt()
                }
            }
        }
        val gold = 0xC4A050
        fill(36, 36, WIDTH - 36, 44, gold)
        fill(36, HEIGHT - 44, WIDTH - 36, HEIGHT - 36, gold)
        fill(36, 36, 44, HEIGHT - 36, gold)
        fill(WIDTH - 44, 36, WIDTH - 36, HEIGHT - 36, gold)
        if (spec.silhouette) {
            ellipse(512, 740, 210, 460, spec.fg)
            return encodePng(pixels)
        }
        val skin = spec.tint ?: spec.fg
        fun figure(cx: Int, cy: Int, scale: Int) {
            ellipse(cx, cy, 70 * scale / 100, 100 * scale / 100, if (spec.dressed) spec.fg else skin)
            ellipse(cx, cy - 140 * scale / 100, 48 * scale / 100, 55 * scale / 100, skin)
            if (!spec.dressed) {
                ellipse(cx - 36 * scale / 100, cy - 10 * scale / 100, 28 * scale / 100, 32 * scale / 100, skin)
                ellipse(cx + 36 * scale / 100, cy - 10 * scale / 100, 28 * scale / 100, 32 * scale / 100, skin)
                ellipse(cx - 36 * scale / 100, cy + 6 * scale / 100, 8 * scale / 100, 8 * scale / 100, 0x784848)
                ellipse(cx + 36 * scale / 100, cy + 6 * scale / 100, 8 * scale / 100, 8 * scale / 100, 0x784848)
            }
            ellipse(cx - 40 * scale / 100, cy + 210 * scale / 100, 28 * scale / 100, 110 * scale / 100, skin)
            ellipse(cx + 40 * scale / 100, cy + 210 * scale / 100, 28 * scale / 100, 110 * scale / 100, skin)
            val armXs = if (spec.arms >= 4) listOf(-110, 110, -80, 80) else listOf(-110, 110)
            armXs.forEach { ox -> ellipse(cx + ox * scale / 100, cy + 40 * scale / 100, 18 * scale / 100, 80 * scale / 100, skin) }
            if (spec.tail) ellipse(cx + 120 * scale / 100, cy + 160 * scale / 100, 90 * scale / 100, 40 * scale / 100, 0x3A261C)
            if (spec.scales) {
                for (i in 0..6) ellipse(cx, cy + i * 18 * scale / 100, 16 * scale / 100, 7 * scale / 100, 0x245640)
            }
        }
        if (spec.group) {
            listOf(280, 430, 590, 740).forEach { figure(it, 780, 70) }
        } else if (spec.pair) {
            figure(400, 720, 100)
            figure(640, 700, 95)
        } else {
            figure(512, 640, 120)
        }
        return encodePng(pixels)
    }

    private fun encodePng(pixels: IntArray): ByteArray {
        val raw = ByteArray((WIDTH * 3 + 1) * HEIGHT)
        var i = 0
        for (y in 0 until HEIGHT) {
            raw[i++] = 0
            val row = y * WIDTH
            for (x in 0 until WIDTH) {
                val c = pixels[row + x]
                raw[i++] = ((c shr 16) and 0xFF).toByte()
                raw[i++] = ((c shr 8) and 0xFF).toByte()
                raw[i++] = (c and 0xFF).toByte()
            }
        }
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(raw)
        deflater.finish()
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(tmp)
            buf.write(tmp, 0, n)
        }
        deflater.end()
        val idat = buf.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            writeInt(out, data.size)
            val bytes = type.toByteArray() + data
            out.write(bytes)
            val crc = CRC32()
            crc.update(bytes)
            writeInt(out, crc.value.toInt())
        }
        chunk("IHDR", byteArrayOf(
            (WIDTH shr 24).toByte(), (WIDTH shr 16).toByte(), (WIDTH shr 8).toByte(), WIDTH.toByte(),
            (HEIGHT shr 24).toByte(), (HEIGHT shr 16).toByte(), (HEIGHT shr 8).toByte(), HEIGHT.toByte(),
            8, 2, 0, 0, 0,
        ))
        chunk("IDAT", idat)
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
