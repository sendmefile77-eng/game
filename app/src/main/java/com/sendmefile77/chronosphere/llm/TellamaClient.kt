package com.sendmefile77.chronosphere.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class TellamaCompletion(
    val model: String,
    val content: String,
    val elapsedMs: Long,
)

internal data class TellamaStatus(
    val available: Boolean,
    val model: String? = null,
    val detail: String? = null,
)

/** One process-wide client keeps local text inference serialized across Chronicle and World UI. */
internal object TellamaRuntime {
    val client = TellamaClient()
}

/**
 * Tiny same-device client for Tellama's Local Only server.
 *
 * Tellama binds Local Only mode to 127.0.0.1:11434 and exposes Ollama-compatible routes.
 * No cloud URL, account or API key is used here. The model id is discovered dynamically so the
 * game works with Qwen2.5 1.5B now and a larger GGUF later without code changes.
 */
internal class TellamaClient(
    private val baseUrl: String = "http://127.0.0.1:11434",
) {
    private val generationMutex = Mutex()

    @Volatile
    private var cachedModel: String? = null

    suspend fun status(force: Boolean = false): TellamaStatus = withContext(Dispatchers.IO) {
        if (!force) cachedModel?.let { return@withContext TellamaStatus(true, it) }
        runCatching {
            val model = discoverModel() ?: return@runCatching TellamaStatus(
                available = false,
                detail = "Tellama працює, але модель не вибрана",
            )
            cachedModel = model
            TellamaStatus(true, model)
        }.getOrElse { error ->
            TellamaStatus(
                available = false,
                detail = error.message?.take(160) ?: "127.0.0.1:11434 не відповідає",
            )
        }
    }

    suspend fun completeJson(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 520,
        temperature: Double = 0.62,
        timeoutMillis: Int = 45_000,
    ): TellamaCompletion? = generationMutex.withLock {
        withContext(Dispatchers.IO) {
            val model = status().model ?: return@withContext null
            val request = JSONObject()
                .put("model", model)
                .put("stream", false)
                .put("format", "json")
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userPrompt)),
                )
                .put(
                    "options",
                    JSONObject()
                        .put("temperature", temperature.coerceIn(0.0, 1.2))
                        .put("num_predict", maxTokens.coerceIn(128, 900))
                        .put("num_ctx", 4096),
                )

            val started = System.currentTimeMillis()
            val response = postJson("/api/chat", request, timeoutMillis) ?: return@withContext null
            val content = response.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (content.isBlank()) return@withContext null
            TellamaCompletion(model, content, System.currentTimeMillis() - started)
        }
    }

    private fun discoverModel(): String? {
        val connection = open("/api/tags", "GET", 2_000)
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("Tellama /api/tags: HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val models = JSONObject(body).optJSONArray("models") ?: return null
            (0 until models.length())
                .asSequence()
                .mapNotNull { index ->
                    val item = models.optJSONObject(index) ?: return@mapNotNull null
                    item.optString("model").takeIf { it.isNotBlank() }
                        ?: item.optString("name").takeIf { it.isNotBlank() }
                }
                .firstOrNull()
        } finally {
            connection.disconnect()
        }
    }

    private fun postJson(path: String, payload: JSONObject, timeoutMillis: Int): JSONObject? {
        val connection = open(path, "POST", timeoutMillis)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.doOutput = true
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                if (code == 401 || code == 403) error("Tellama Local Only очікується без API-ключа; перевірте режим сервера")
                error("Tellama /api/chat: HTTP $code ${text.take(100)}")
            }
            if (text.isBlank()) null else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String, timeoutMillis: Int): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(timeoutMillis, 3_000)
            readTimeout = timeoutMillis
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
}
