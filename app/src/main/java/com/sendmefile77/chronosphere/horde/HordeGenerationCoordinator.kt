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
 * Process-level owner for generated image jobs.
 *
 * Compose screens are only observers: leaving a tab must not cancel an already submitted request.
 * Local Dream is preferred when its on-device backend is reachable; AI Horde remains the automatic
 * network fallback. Completed images share the existing disk cache and canonical reference store.
 */
internal object HordeGenerationCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hordeClient = HordeClient()
    private val localDreamClient = LocalDreamClient()
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
            return HordePreparedImage(
                bytes = cached,
                model = null,
                usedReference = false,
                provider = ImageGenerationProvider.CACHE,
                actualWidth = null,
                actualHeight = null,
            )
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

    suspend fun isLocalDreamAvailable(force: Boolean = false): Boolean =
        localDreamClient.isAvailable(force)

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
            return HordePreparedImage(
                bytes = cached,
                model = null,
                usedReference = false,
                provider = ImageGenerationProvider.CACHE,
                actualWidth = null,
                actualHeight = null,
            )
        }

        val references = HordeCharacterReferenceStore(File(filesDir, "horde-character-references"))
        val reference = request.referenceCacheKey?.let(references::read)

        val localResult = if (localDreamClient.isAvailable()) {
            try {
                localDreamClient.generate(
                    request = request,
                    sourceImageBytes = reference?.imageBytes,
                    timeoutMillis = timeoutMillis,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        if (localResult != null) {
            cache.write(request.cacheKey, localResult.imageBytes)
            if (request.saveResultAsReference) {
                request.referenceCacheKey?.let { key ->
                    references.writeIfAbsent(key, localResult.imageBytes, LOCAL_DREAM_REFERENCE_MODEL)
                }
            }
            return HordePreparedImage(
                bytes = localResult.imageBytes,
                model = null,
                usedReference = reference != null,
                provider = ImageGenerationProvider.LOCAL_DREAM,
                actualWidth = localResult.width,
                actualHeight = localResult.height,
            )
        }

        val effectiveRequest = reference?.model
            ?.takeUnless { it == LOCAL_DREAM_REFERENCE_MODEL }
            ?.let { canonicalModel ->
                request.copy(
                    preferredModels = (listOf(canonicalModel) + request.preferredModels)
                        .distinctBy { it.lowercase() },
                )
            } ?: request

        val result = if (reference != null) {
            try {
                hordeClient.generate(
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
                hordeClient.generate(
                    request = request,
                    sourceImageBytes = null,
                    timeoutMillis = timeoutMillis,
                    pollIntervalMillis = pollIntervalMillis,
                )
            }
        } else {
            hordeClient.generate(
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
            provider = ImageGenerationProvider.AI_HORDE,
            actualWidth = request.width,
            actualHeight = request.height,
        )
    }

    private const val LOCAL_DREAM_REFERENCE_MODEL = "local-dream"
}

internal enum class ImageGenerationProvider(val displayNameUk: String) {
    LOCAL_DREAM("Local Dream"),
    AI_HORDE("AI Horde"),
    CACHE("Кеш"),
}

internal data class HordePreparedImage(
    val bytes: ByteArray,
    val model: String?,
    val usedReference: Boolean,
    val provider: ImageGenerationProvider = ImageGenerationProvider.AI_HORDE,
    val actualWidth: Int? = null,
    val actualHeight: Int? = null,
)
