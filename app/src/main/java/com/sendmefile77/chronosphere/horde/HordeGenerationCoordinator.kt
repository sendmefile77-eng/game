package com.sendmefile77.chronosphere.horde

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-level owner for AI Horde generation jobs.
 *
 * Compose screens are only observers: leaving a tab must not cancel an already submitted Horde
 * request. Completed images are persisted in the existing disk cache and an observer that returns
 * later receives either the same in-flight job or the cached result.
 */
internal object HordeGenerationCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HordeClient()
    private val inFlight = ConcurrentHashMap<String, Deferred<HordePreparedImage>>()

    suspend fun load(
        filesDir: File,
        request: HordeImageRequest,
        timeoutMillis: Long,
        pollIntervalMillis: Long = 3_000L,
    ): HordePreparedImage {
        val cache = HordeImageCache(File(filesDir, "horde-images"))
        cache.read(request.cacheKey)?.let { cached ->
            if (request.saveResultAsReference) {
                val referenceKey = request.referenceCacheKey
                if (referenceKey != null) {
                    HordeCharacterReferenceStore(File(filesDir, "horde-character-references"))
                        .writeIfAbsent(referenceKey, cached, null)
                }
            }
            return HordePreparedImage(cached, model = null, usedReference = false)
        }

        val jobKey = listOf(
            request.cacheKey,
            request.seed,
            request.width.toString(),
            request.height.toString(),
            request.steps.toString(),
        ).joinToString("|")

        inFlight[jobKey]?.let { return it.await() }

        val created = scope.async(start = CoroutineStart.LAZY) {
            generateAndCache(
                filesDir = filesDir,
                request = request,
                timeoutMillis = timeoutMillis,
                pollIntervalMillis = pollIntervalMillis,
            )
        }
        val existing = inFlight.putIfAbsent(jobKey, created)
        val chosen = existing ?: created.also { deferred ->
            deferred.invokeOnCompletion { inFlight.remove(jobKey, deferred) }
            deferred.start()
        }
        if (existing != null) created.cancel()
        return chosen.await()
    }

    fun isLoading(request: HordeImageRequest): Boolean {
        val prefix = "${request.cacheKey}|"
        return inFlight.keys.any { it.startsWith(prefix) }
    }

    private suspend fun generateAndCache(
        filesDir: File,
        request: HordeImageRequest,
        timeoutMillis: Long,
        pollIntervalMillis: Long,
    ): HordePreparedImage {
        val cache = HordeImageCache(File(filesDir, "horde-images"))
        cache.read(request.cacheKey)?.let { cached ->
            return HordePreparedImage(cached, model = null, usedReference = false)
        }

        val references = HordeCharacterReferenceStore(File(filesDir, "horde-character-references"))
        val reference = request.referenceCacheKey?.let(references::read)
        val effectiveRequest = reference?.model?.let { canonicalModel ->
            request.copy(
                preferredModels = (listOf(canonicalModel) + request.preferredModels)
                    .distinctBy { it.lowercase() },
            )
        } ?: request

        val result = if (reference != null) {
            try {
                client.generate(
                    request = effectiveRequest,
                    sourceImageBytes = reference.imageBytes,
                    timeoutMillis = timeoutMillis,
                    pollIntervalMillis = pollIntervalMillis,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // A reference/model combination may be unavailable on the Horde. Retrying txt2img
                // preserves availability while keeping the background job alive.
                client.generate(
                    request = request,
                    sourceImageBytes = null,
                    timeoutMillis = timeoutMillis,
                    pollIntervalMillis = pollIntervalMillis,
                )
            }
        } else {
            client.generate(
                request = request,
                sourceImageBytes = null,
                timeoutMillis = timeoutMillis,
                pollIntervalMillis = pollIntervalMillis,
            )
        }

        cache.write(request.cacheKey, result.imageBytes)
        if (request.saveResultAsReference) {
            request.referenceCacheKey?.let { key ->
                references.writeIfAbsent(key, result.imageBytes, result.model)
            }
        }
        return HordePreparedImage(
            bytes = result.imageBytes,
            model = result.model,
            usedReference = reference != null,
        )
    }
}

internal data class HordePreparedImage(
    val bytes: ByteArray,
    val model: String?,
    val usedReference: Boolean,
)
