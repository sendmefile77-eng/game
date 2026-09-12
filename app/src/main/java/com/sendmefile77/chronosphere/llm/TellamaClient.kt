package com.sendmefile77.chronosphere.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
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
 * Chronosphere only talks to 127.0.0.1:11434. Tellama requires an API key for the server;
 * every request carries Authorization: Bearer <key>. /api/chat is streaming-only in current
 * Tellama releases, so responses are consumed as NDJSON until done=true.
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
            val model = discoverModelWithRetry() ?: return@runCatching TellamaStatus(
                available = false,
                detail = "Tellama працює, але модель не вибрана для сервера",
            )
            cachedModel = model
            TellamaStatus(true, model)
        }.getOrElse { error ->
            TellamaStatus(
                available = false,
                detail = friendlyConnectionError(error),
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
                .put("stream", true)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userPrompt)),
                )
                .put(
                    "options",
                    JSONObject()
                        .put("temperature", temperature.coerceIn(0.0, 2.0))
                        .put("num_predict", maxTokens.coerceIn(128, 900))
                        .put("top_p", 0.86),
                )

            val started = System.currentTimeMillis()
            val content = postChatNdjson("/api/chat", request, timeoutMillis).trim()
            if (content.isBlank()) return@withContext null
            TellamaCompletion(model, content, System.currentTimeMillis() - started)
        }
    }

    private suspend fun discoverModelWithRetry(): String? {
        var lastError: Throwable? = null
        repeat(4) { attempt ->
            try {
                return discoverModel()
            } catch (error: Throwable) {
                lastError = error
                if (!isConnectionProblem(error) || attempt == 3) throw error
                delay(250L + attempt * 250L)
            }
        }
        throw lastError ?: IllegalStateException("Tellama connection failed")
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

    private fun postChatNdjson(path: String, payload: JSONObject, timeoutMillis: Int): String {
        val connection = open(path, "POST", timeoutMillis)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.doOutput = true
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throwHttpError(path, code, text)
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                collectChatNdjson(lines)
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun collectChatNdjson(lines: Sequence<String>): String {
        val content = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val event = JSONObject(trimmed)
            val errorObject = event.optJSONObject("error")
            if (errorObject != null) {
                val message = errorObject.optString("message").ifBlank { "Tellama generation error" }
                error(message)
            }
            event.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotEmpty() }
                ?.let(content::append)
            if (event.optBoolean("done", false)) break
        }
        return content.toString()
    }

    private fun throwHttpError(path: String, code: Int, body: String): Nothing {
        if (code == 401 || code == 403) {
            error("Tellama відхилила API-ключ. Створіть новий ключ у Tellama → Server і збережіть його в Хроносфері")
        }
        if (code == 503) {
            error("Tellama runtime зайнятий або модель ще завантажується")
        }
        error("Tellama $path: HTTP $code ${body.take(120)}")
    }

    private fun friendlyConnectionError(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        val raw = error.message.orEmpty()
        return when {
            root is ConnectException || raw.contains("Failed to connect", ignoreCase = true) ||
                raw.contains("Connection refused", ignoreCase = true) ->
                "Сервер Tellama не працює на 127.0.0.1:11434. У Tellama → Server має бути кнопка «Stop server». Якщо сервер гасне після перемикання в Хроносферу — дозвольте Tellama фонову роботу та режим батареї «Без обмежень»."
            root is SocketTimeoutException || raw.contains("timed out", ignoreCase = true) ->
                "Tellama запущена, але не відповіла вчасно. Дочекайтеся завантаження моделі й натисніть «Перевірити» ще раз."
            else -> raw.take(240).ifBlank { "127.0.0.1:11434 не відповідає" }
        }
    }

    private fun isConnectionProblem(error: Throwable): Boolean {
        val root = generateSequence(error) { it.cause }.last()
        val raw = error.message.orEmpty()
        return root is ConnectException || root is SocketTimeoutException ||
            raw.contains("Failed to connect", ignoreCase = true) ||
            raw.contains("Connection refused", ignoreCase = true)
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
