package com.sendmefile77.chronosphere.horde

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class HordeClient(
    private val apiKey: String = ANONYMOUS_API_KEY,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val clientAgent: String = DEFAULT_CLIENT_AGENT,
) {
    @Volatile
    private var modelCache: ModelCache? = null

    private val censoredWorkers = linkedMapOf<String, Long>()

    suspend fun generate(
        request: HordeImageRequest,
        sourceImageBytes: ByteArray? = null,
        timeoutMillis: Long = 60_000L,
        pollIntervalMillis: Long = 3_000L,
    ): HordeGenerationResult {
        require(timeoutMillis >= 5_000L)
        require(pollIntervalMillis >= 1_000L)
        require(sourceImageBytes == null || sourceImageBytes.isNotEmpty())

        val selectedModels = resolvePreferredModels(request.preferredModels)
        val startedAt = System.nanoTime()
        var censorshipRetries = 0

        while (true) {
            val remainingMillis = timeoutMillis - elapsedMillis(startedAt)
            if (remainingMillis < 5_000L) {
                throw HordeGenerationException("AI Horde не встигла завершити генерацію за ${timeoutMillis / 1000} с")
            }

            try {
                return generateOnce(
                    request = request,
                    selectedModels = selectedModels,
                    sourceImageBytes = sourceImageBytes,
                    blockedWorkerIds = recentCensoredWorkerIds(),
                    timeoutMillis = remainingMillis,
                    pollIntervalMillis = pollIntervalMillis,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (censored: HordeCensoredGenerationException) {
                if (request.ageYears < 18) {
                    throw HordeGenerationException("AI Horde відхилила зображення захисним фільтром")
                }

                censored.workerId?.let(::rememberCensoredWorker)
                censorshipRetries += 1
                if (censorshipRetries > MAX_CENSORSHIP_RETRIES) {
                    throw HordeGenerationException(
                        "AI Horde кілька разів повернула цензурований кадр. Спробуйте ще раз пізніше.",
                    )
                }
                delay(CENSORSHIP_RETRY_DELAY_MILLIS)
            }
        }
    }

    private suspend fun generateOnce(
        request: HordeImageRequest,
        selectedModels: List<String>,
        sourceImageBytes: ByteArray?,
        blockedWorkerIds: List<String>,
        timeoutMillis: Long,
        pollIntervalMillis: Long,
    ): HordeGenerationResult {
        var jobId: String? = null
        try {
            jobId = withContext(Dispatchers.IO) {
                submit(request, selectedModels, sourceImageBytes, blockedWorkerIds)
            }
            val startedAt = System.nanoTime()
            while (elapsedMillis(startedAt) < timeoutMillis) {
                val check = withContext(Dispatchers.IO) { getJson("$baseUrl/generate/check/$jobId") }
                if (check.optBoolean("faulted", false)) {
                    throw HordeGenerationException("AI Horde reported a failed generation job")
                }
                if (check.optBoolean("done", false)) break
                delay(pollIntervalMillis)
            }

            if (elapsedMillis(startedAt) >= timeoutMillis) {
                throw HordeGenerationException("AI Horde did not finish within ${timeoutMillis / 1000} seconds")
            }

            val status = withContext(Dispatchers.IO) { getJson("$baseUrl/generate/status/$jobId") }
            val generations = status.optJSONArray("generations")
                ?: throw HordeGenerationException("AI Horde returned no generations array")
            if (generations.length() == 0) {
                throw HordeGenerationException("AI Horde completed the job without an image")
            }

            val generation = generations.getJSONObject(0)
            val generationState = generation.optString("state", "ok")
            val censored = generation.optBoolean("censored", false) || generationState == "censored"
            val censorshipReason = censorshipReason(generation)

            if (censorshipReason == "csam") {
                throw HordeGenerationException("AI Horde відхилила запит через захисний фільтр. Генерацію зупинено.")
            }
            if (censored) {
                throw HordeCensoredGenerationException(
                    workerId = generation.optString("worker_id").takeIf { it.isNotBlank() },
                    workerName = generation.optString("worker_name").takeIf { it.isNotBlank() },
                    model = generation.optString("model").takeIf { it.isNotBlank() },
                )
            }
            if (generationState.isNotBlank() && generationState != "ok") {
                throw HordeGenerationException("AI Horde rejected the generated image: $generationState")
            }

            val imageRef = generation.optString("img").trim()
            if (imageRef.isBlank()) throw HordeGenerationException("AI Horde returned an empty image reference")

            val bytes = withContext(Dispatchers.IO) { readImageBytes(imageRef) }
            return HordeGenerationResult(
                requestId = jobId,
                generationId = generation.optString("id").takeIf { it.isNotBlank() },
                imageBytes = bytes,
                model = generation.optString("model").takeIf { it.isNotBlank() },
                seed = generation.optString("seed").takeIf { it.isNotBlank() },
                censored = false,
            )
        } catch (cancelled: CancellationException) {
            jobId?.let { id ->
                withContext(NonCancellable + Dispatchers.IO) { runCatching { cancel(id) } }
            }
            throw cancelled
        } catch (censored: HordeCensoredGenerationException) {
            throw censored
        } catch (error: HordeGenerationException) {
            jobId?.let { id -> withContext(Dispatchers.IO) { runCatching { cancel(id) } } }
            throw error
        } catch (error: Throwable) {
            jobId?.let { id -> withContext(Dispatchers.IO) { runCatching { cancel(id) } } }
            throw HordeGenerationException(error.message ?: "AI Horde request failed", error)
        }
    }

    private fun submit(
        request: HordeImageRequest,
        models: List<String>,
        sourceImageBytes: ByteArray?,
        blockedWorkerIds: List<String>,
    ): String {
        val params = JSONObject()
            .put("sampler_name", request.samplerName)
            .put("cfg_scale", request.cfgScale)
            .put("steps", request.steps)
            .put("width", request.width)
            .put("height", request.height)
            .put("n", 1)
            .put("seed", request.seed)
            .put("karras", true)

        val payload = JSONObject()
            .put("prompt", request.apiPrompt())
            .put("params", params)
            .put("nsfw", request.nsfw)
            .put("censor_nsfw", false)
            .put("trusted_workers", false)
            .put("validated_backends", true)
            .put("slow_workers", true)
            .put("extra_slow_workers", false)
            .put("r2", true)
            .put("shared", false)
            .put("allow_downgrade", true)

        if (models.isNotEmpty()) payload.put("models", JSONArray(models))
        if (blockedWorkerIds.isNotEmpty()) {
            payload.put("workers", JSONArray(blockedWorkerIds.take(MAX_WORKER_BLACKLIST_SIZE)))
            payload.put("worker_blacklist", true)
        }
        if (sourceImageBytes != null) {
            params.put("denoising_strength", request.referenceDenoisingStrength)
            payload.put("source_image", Base64.encodeToString(sourceImageBytes, Base64.NO_WRAP))
            payload.put("source_processing", "img2img")
        }

        val response = postJson("$baseUrl/generate/async", payload)
        val id = response.optString("id").trim()
        if (id.isBlank()) {
            val message = response.optString("message").takeIf { it.isNotBlank() }
            throw HordeGenerationException(message ?: "AI Horde did not return a request id")
        }
        return id
    }

    private fun censorshipReason(generation: JSONObject): String? {
        val metadata = generation.optJSONArray("gen_metadata") ?: return null
        for (index in 0 until metadata.length()) {
            val entry = metadata.optJSONObject(index) ?: continue
            if (entry.optString("type") == "censorship") {
                return entry.optString("value").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    @Synchronized
    private fun rememberCensoredWorker(workerId: String) {
        if (workerId.isBlank()) return
        pruneCensoredWorkersLocked()
        censoredWorkers.remove(workerId)
        censoredWorkers[workerId] = System.nanoTime()
        while (censoredWorkers.size > MAX_REMEMBERED_CENSORED_WORKERS) {
            val oldest = censoredWorkers.keys.firstOrNull() ?: break
            censoredWorkers.remove(oldest)
        }
    }

    @Synchronized
    private fun recentCensoredWorkerIds(): List<String> {
        pruneCensoredWorkersLocked()
        return censoredWorkers.keys.toList().takeLast(MAX_WORKER_BLACKLIST_SIZE)
    }

    private fun pruneCensoredWorkersLocked() {
        val now = System.nanoTime()
        val iterator = censoredWorkers.entries.iterator()
        while (iterator.hasNext()) {
            val (_, seenAt) = iterator.next()
            if ((now - seenAt) / 1_000_000L >= CENSORED_WORKER_TTL_MILLIS) iterator.remove()
        }
    }

    private fun resolvePreferredModels(preferredModels: List<String>): List<String> {
        if (preferredModels.isEmpty()) return emptyList()
        val active = runCatching { activeModelNames() }.getOrElse { return emptyList() }
        val activeByLower = active.associateBy { it.lowercase() }
        return preferredModels.mapNotNull { preferred -> activeByLower[preferred.lowercase()] }.distinct().take(3)
    }

    private fun activeModelNames(): Set<String> {
        val now = System.nanoTime()
        modelCache?.takeIf { elapsedMillis(it.createdAtNanos) < MODEL_CACHE_TTL_MILLIS }?.let { return it.names }

        val connection = openConnection("$baseUrl/status/models", "GET")
        val body = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw HordeGenerationException("AI Horde model list failed with HTTP ${connection.responseCode}")
        }
        val array = JSONArray(body)
        val names = buildSet {
            for (index in 0 until array.length()) {
                val name = array.optJSONObject(index)?.optString("name")?.trim().orEmpty()
                if (name.isNotBlank()) add(name)
            }
        }
        modelCache = ModelCache(now, names)
        return names
    }

    private fun cancel(id: String) {
        val connection = openConnection("$baseUrl/generate/status/$id", "DELETE")
        readResponse(connection)
    }

    private fun getJson(url: String): JSONObject {
        val connection = openConnection(url, "GET")
        val body = readResponse(connection)
        if (connection.responseCode !in 200..299) throw httpError(connection.responseCode, body)
        return JSONObject(body)
    }

    private fun postJson(url: String, payload: JSONObject): JSONObject {
        val connection = openConnection(url, "POST").apply { doOutput = true }
        connection.outputStream.use { stream -> stream.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val body = readResponse(connection)
        if (connection.responseCode !in 200..299) throw httpError(connection.responseCode, body)
        return JSONObject(body)
    }

    private fun openConnection(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Client-Agent", clientAgent)
        }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun httpError(code: Int, body: String): HordeGenerationException {
        val message = runCatching {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("rc").takeIf { it.isNotBlank() }
        }.getOrNull()
        return HordeGenerationException(message ?: "AI Horde HTTP $code")
    }

    private fun readImageBytes(imageRef: String): ByteArray {
        if (imageRef.startsWith("https://") || imageRef.startsWith("http://")) {
            val connection = (URL(imageRef).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Chronosphere/0.1.0")
            }
            if (connection.responseCode !in 200..299) {
                throw HordeGenerationException("Generated image download failed with HTTP ${connection.responseCode}")
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input -> input.copyTo(output) }
            return output.toByteArray().also {
                if (it.isEmpty()) throw HordeGenerationException("Generated image download was empty")
            }
        }

        val base64 = imageRef.substringAfter("base64,", imageRef)
        return runCatching { Base64.decode(base64, Base64.DEFAULT) }
            .getOrElse { throw HordeGenerationException("AI Horde returned invalid base64 image data", it) }
            .also { if (it.isEmpty()) throw HordeGenerationException("AI Horde returned empty image data") }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long = (System.nanoTime() - startedAtNanos) / 1_000_000L

    private data class ModelCache(val createdAtNanos: Long, val names: Set<String>)

    private class HordeCensoredGenerationException(
        val workerId: String?,
        val workerName: String?,
        val model: String?,
    ) : Exception("AI Horde worker censored the generated image")

    companion object {
        const val ANONYMOUS_API_KEY = "0000000000"
        private const val DEFAULT_BASE_URL = "https://aihorde.net/api/v2"
        private const val DEFAULT_CLIENT_AGENT = "Chronosphere:0.1.0:https://github.com/sendmefile77-eng/game"
        private const val MODEL_CACHE_TTL_MILLIS = 60_000L
        private const val MAX_CENSORSHIP_RETRIES = 2
        private const val CENSORSHIP_RETRY_DELAY_MILLIS = 750L
        private const val MAX_WORKER_BLACKLIST_SIZE = 5
        private const val MAX_REMEMBERED_CENSORED_WORKERS = 12
        private const val CENSORED_WORKER_TTL_MILLIS = 30 * 60 * 1000L
    }
}
