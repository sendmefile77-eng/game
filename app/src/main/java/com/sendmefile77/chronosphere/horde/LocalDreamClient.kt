package com.sendmefile77.chronosphere.horde

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.max

/**
 * Client for Local Dream's on-device Stable Diffusion HTTP backend.
 *
 * Local Dream starts the server only after a model is loaded in the companion app. The endpoint
 * binds to 127.0.0.1:8081 and streams /generate as Server-Sent Events. The final image is raw RGB,
 * so it is converted to PNG before it enters Chronosphere's normal image/reference cache.
 */
internal class LocalDreamClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val generationMutex = Mutex()

    @Volatile
    private var availability: AvailabilityCache? = null

    suspend fun isAvailable(force: Boolean = false): Boolean {
        val now = System.nanoTime()
        availability?.takeIf { !force && elapsedMillis(it.checkedAtNanos, now) < AVAILABILITY_TTL_MILLIS }
            ?.let { return it.available }

        val available = withContext(Dispatchers.IO) {
            runCatching { probeBlocking() }.getOrDefault(false)
        }
        availability = AvailabilityCache(now, available)
        return available
    }

    suspend fun generate(
        request: HordeImageRequest,
        sourceImageBytes: ByteArray? = null,
        timeoutMillis: Long = 135_000L,
    ): LocalDreamGenerationResult = generationMutex.withLock {
        require(timeoutMillis >= 5_000L)
        require(sourceImageBytes == null || sourceImageBytes.isNotEmpty())
        try {
            withContext(Dispatchers.IO) {
                generateBlocking(request, sourceImageBytes, timeoutMillis)
            }.also {
                availability = AvailabilityCache(System.nanoTime(), true)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // A loaded model can reject an unsupported NPU resolution. Keep the backend marked as
            // reachable so the next request may still succeed with another aspect/resolution.
            availability = AvailabilityCache(System.nanoTime(), true)
            if (error is LocalDreamGenerationException) throw error
            throw LocalDreamGenerationException(error.message ?: "Local Dream generation failed", error)
        }
    }

    private fun probeBlocking(): Boolean {
        val payload = JSONObject().put("prompt", "chronosphere")
        val connection = openConnection("$baseUrl/tokenize", "POST", PROBE_TIMEOUT_MILLIS).apply {
            doOutput = true
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) return false
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            json.has("count") || json.has("max_length")
        } finally {
            connection.disconnect()
        }
    }

    private fun generateBlocking(
        request: HordeImageRequest,
        sourceImageBytes: ByteArray?,
        timeoutMillis: Long,
    ): LocalDreamGenerationResult {
        val payload = JSONObject()
            .put("prompt", request.positivePrompt)
            .put("negative_prompt", request.negativePrompt)
            .put("steps", request.steps.coerceIn(1, 50))
            .put("cfg", request.cfgScale.coerceIn(1.0, 30.0))
            .put("seed", localSeed(request.seed))
            .put("scheduler", schedulerFor(request.samplerName))
            .put("width", request.width)
            .put("height", request.height)
            .put("aspect_ratio", aspectRatioFor(request.width, request.height))
            .put("show_diffusion_process", false)

        if (sourceImageBytes != null) {
            payload.put("image", Base64.encodeToString(sourceImageBytes, Base64.NO_WRAP))
            payload.put("denoise_strength", request.referenceDenoisingStrength.coerceIn(0.05, 1.0))
        }

        val connection = openConnection(
            "$baseUrl/generate",
            "POST",
            timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        ).apply {
            doOutput = true
            setRequestProperty("Accept", "text/event-stream, application/json")
        }

        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw LocalDreamGenerationException(localDreamHttpMessage(code, body))
            }

            val event = StringBuilder()
            val data = StringBuilder()
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) {
                        decodeSseEvent(event.toString(), data.toString())?.let { return it }
                        event.setLength(0)
                        data.setLength(0)
                        continue
                    }
                    when {
                        line.startsWith("event:") -> event.append(line.substringAfter(':').trim())
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.substringAfter(':').trimStart())
                        }
                    }
                }
                decodeSseEvent(event.toString(), data.toString())?.let { return it }
            }
            throw LocalDreamGenerationException("Local Dream завершив потік без готового зображення")
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeSseEvent(eventName: String, data: String): LocalDreamGenerationResult? {
        if (data.isBlank()) return null
        val json = runCatching { JSONObject(data) }.getOrElse {
            if (eventName == "progress") return null
            throw LocalDreamGenerationException("Local Dream повернув пошкоджену SSE-відповідь", it)
        }
        val type = json.optString("type", eventName).lowercase()
        if (type == "progress") return null
        if (type == "error" || eventName.equals("error", ignoreCase = true)) {
            throw LocalDreamGenerationException(
                json.optString("message").takeIf { it.isNotBlank() } ?: "Local Dream повідомив про помилку",
            )
        }
        if (type != "complete" && !eventName.equals("complete", ignoreCase = true)) return null

        val width = json.optInt("width", 0)
        val height = json.optInt("height", 0)
        val channels = json.optInt("channels", 3)
        if (width <= 0 || height <= 0 || channels !in 3..4) {
            throw LocalDreamGenerationException("Local Dream повернув некоректні розміри зображення")
        }
        val encoded = json.optString("image").trim()
        if (encoded.isBlank()) throw LocalDreamGenerationException("Local Dream повернув порожнє зображення")
        val raw = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .getOrElse { throw LocalDreamGenerationException("Local Dream повернув некоректний RGB base64", it) }
        val png = rawRgbToPng(raw, width, height, channels)
        return LocalDreamGenerationResult(
            imageBytes = png,
            seed = json.optLong("seed").takeIf { json.has("seed") },
            width = width,
            height = height,
            generationTimeMillis = json.optLong("generation_time_ms").takeIf { json.has("generation_time_ms") },
        )
    }

    private fun rawRgbToPng(raw: ByteArray, width: Int, height: Int, channels: Int): ByteArray {
        val pixelCountLong = width.toLong() * height.toLong()
        if (pixelCountLong <= 0L || pixelCountLong > MAX_PIXELS) {
            throw LocalDreamGenerationException("Local Dream повернув надто велике зображення: ${width}×$height")
        }
        val pixelCount = pixelCountLong.toInt()
        val expected = pixelCountLong * channels
        if (expected > Int.MAX_VALUE || raw.size.toLong() < expected) {
            throw LocalDreamGenerationException(
                "Local Dream RGB має неправильний розмір: ${raw.size}, очікувалось $expected",
            )
        }

        val pixels = IntArray(pixelCount)
        var source = 0
        for (index in 0 until pixelCount) {
            val red = raw[source].toInt() and 0xff
            val green = raw[source + 1].toInt() and 0xff
            val blue = raw[source + 2].toInt() and 0xff
            val alpha = if (channels == 4) raw[source + 3].toInt() and 0xff else 0xff
            pixels[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            source += channels
        }

        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw LocalDreamGenerationException("Не вдалося перетворити Local Dream RGB у PNG")
                }
                output.toByteArray().also {
                    if (it.isEmpty()) throw LocalDreamGenerationException("Local Dream PNG вийшов порожнім")
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun openConnection(url: String, method: String, timeoutMillis: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMillis.coerceIn(500, 15_000)
            readTimeout = timeoutMillis.coerceAtLeast(1_000)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Chronosphere/0.1.0")
        }

    private fun localDreamHttpMessage(code: Int, body: String): String {
        val message = runCatching {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
        }.getOrNull()
        return "Local Dream HTTP $code${message?.let { ": $it" } ?: ""}"
    }

    internal fun localSeed(value: String): Long {
        var hash = -3750763034362895579L
        value.forEach { char -> hash = (hash xor char.code.toLong()) * 1099511628211L }
        return hash and 0xffffffffL
    }

    internal fun schedulerFor(samplerName: String): String = when (samplerName.lowercase()) {
        "k_dpmpp_2m", "dpmpp_2m", "dpm" -> "dpm_karras"
        "k_dpmpp_sde", "k_dpmpp_2m_sde", "dpm_sde" -> "dpm_sde_karras"
        "k_euler_a", "euler_a", "eulera" -> "euler_a_karras"
        "k_euler", "euler" -> "euler_karras"
        "lcm" -> "lcm"
        else -> "dpm_karras"
    }

    internal fun aspectRatioFor(width: Int, height: Int): String {
        val divisor = greatestCommonDivisor(abs(width), abs(height)).coerceAtLeast(1)
        return "${width / divisor}:${height / divisor}"
    }

    private fun greatestCommonDivisor(a: Int, b: Int): Int {
        var left = max(1, a)
        var right = max(1, b)
        while (right != 0) {
            val remainder = left % right
            left = right
            right = remainder
        }
        return left
    }

    private fun elapsedMillis(startedAtNanos: Long, nowNanos: Long = System.nanoTime()): Long =
        (nowNanos - startedAtNanos) / 1_000_000L

    private data class AvailabilityCache(
        val checkedAtNanos: Long,
        val available: Boolean,
    )

    companion object {
        private const val DEFAULT_BASE_URL = "http://127.0.0.1:8081"
        private const val PROBE_TIMEOUT_MILLIS = 1_500
        private const val AVAILABILITY_TTL_MILLIS = 20_000L
        private const val MAX_PIXELS = 16_777_216L
    }
}

internal data class LocalDreamGenerationResult(
    val imageBytes: ByteArray,
    val seed: Long?,
    val width: Int,
    val height: Int,
    val generationTimeMillis: Long?,
)

internal class LocalDreamGenerationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
