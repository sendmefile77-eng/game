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

    fun configureApiKey(value: String?) {
        client.setApiKey(value)
    }
}

/**
 * Same-device client for Tellama's authenticated Ollama-compatible server.
 *
 * Chronosphere only talks to 127.0.0.1:11434. Tellama requires an API key even in same-phone mode;
 * every request therefore carries Authorization: Bearer <key>. The key is injected at runtime from
 * Chronosphere's private preferences and is never stored in game saves or source code.
 */
internal class TellamaClient(
    private val baseUrl: String = "http://127.0.0.1:11434",
) {
    private val generationMutex = Mutex()

    @Volatile
    private var cachedModel: String? = null

    @Volatile
    private var apiKey: String? = null

    fun setApiKey(value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotBlank() }
        if (normalized != apiKey) {
            apiKey = normalized
            cachedModel = null
        }
    }

    fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()

    suspend fun status(force: Boolean = false): TellamaStatus = withContext(Dispatchers.IO) {
        if (!hasApiKey()) {
            return@withContext TellamaStatus(
                available = false,
                detail = "Вкажіть API-ключ Tellama у вкладці «Світ»",
            )
        }
        if (!force) cachedModel?.let { return@withContext TellamaStatus(true, it) }
        runCatching {
            val model = discoverModel() ?: return@runCatching TellamaStatus(
                available = false,
                detail = "Tellama працює, але модель не вибрана для сервера",
            )
            cachedModel = model
            TellamaStatus(true, model)
        }.getOrElse { error ->
            TellamaStatus(
                available = false,
                detail = error.message?.take(180) ?: "127.0.0.1:11434 не відповідає",
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
        val connection = open("/api/tags", "GET", 2_500)
        return try {
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) throwHttpError("/api/tags", code, body)
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
            if (code !in 200..299) throwHttpError(path, code, text)
            if (text.isBlank()) null else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun throwHttpError(path: String, code: Int, body: String): Nothing {
        if (code == 401 || code == 403) {
            error("Tellama відхилила API-ключ. Створіть новий ключ у Tellama → Server і збережіть його в Хроносфері")
        }
        error("Tellama $path: HTTP $code ${body.take(120)}")
    }

    private fun open(path: String, method: String, timeoutMillis: Int): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(timeoutMillis, 3_000)
            readTimeout = timeoutMillis
            useCaches = false
            setRequestProperty("Accept", "application/json")
            apiKey?.let { key -> setRequestProperty("Authorization", "Bearer $key") }
        }
}
