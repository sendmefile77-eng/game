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

internal const val GENERATED_IMAGE_CACHE_DIRECTORY = "generated-images-v2"

/**
 * Process-level owner for generated image jobs.
 *
 * Compose screens are only observers: leaving a tab must not cancel an already submitted request.
 * Local Dream is preferred when its on-device backend is reachable; AI Horde remains the automatic
 * network fallback. A caller may provide a Local-Dream-specific request profile without changing
 * the Horde request; this is useful for distilled on-device models which need far fewer steps.
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
        localDreamRequest: HordeImageRequest? = null,
        localDreamTimeoutMillis: Long? = null,
    ): HordePreparedImage {
        val cache = HordeImageCache(File(filesDir, GENERATED_IMAGE_CACHE_DIRECTORY))
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

        val localProfile = localDreamRequest ?: request
        val jobKey = listOf(
            request.cacheKey,
            request.seed,
            request.width.toString(),
            request.height.toString(),
            request.steps.toString(),
            localProfile.steps.toString(),
            localProfile.cfgScale.toString(),
            localProfile.samplerName,
        ).joinToString("|")

        inFlight[jobKey]?.let { return it.await() }

        val created = scope.async(start = CoroutineStart.LAZY) {
            generateAndCache(
                filesDir = filesDir,
                request = request,
                timeoutMillis = timeoutMillis,
                pollIntervalMillis = pollIntervalMillis,
                localDreamRequest = localProfile,
                localDreamTimeoutMillis = localDreamTimeoutMillis,
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

    suspend fun localDreamStatus(force: Boolean = false): LocalDreamStatus =
        localDreamClient.status(force)

    suspend fun isLocalDreamAvailable(force: Boolean = false): Boolean =
        localDreamStatus(force).available

    fun isLoading(request: HordeImageRequest): Boolean {
        val prefix = "${request.cacheKey}|"
        return inFlight.keys.any { it.startsWith(prefix) }
    }

    private suspend fun generateAndCache(
        filesDir: File,
        request: HordeImageRequest,
        timeoutMillis: Long,
        pollIntervalMillis: Long,
        localDreamRequest: HordeImageRequest,
        localDreamTimeoutMillis: Long?,
    ): HordePreparedImage {
        val cache = HordeImageCache(File(filesDir, GENERATED_IMAGE_CACHE_DIRECTORY))
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

        var localDreamFallbackNote: String? = null
        val localStatus = localDreamClient.status()
        val localResult = if (localStatus.available) {
            try {
                localDreamClient.generate(
                    request = localDreamRequest,
                    sourceImageBytes = reference?.imageBytes,
                    timeoutMillis = (localDreamTimeoutMillis ?: timeoutMillis).coerceAtLeast(5_000L),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                localDreamFallbackNote = "генерація Local Dream: ${compactReason(error)}"
                null
            }
        } else {
            localDreamFallbackNote = localStatus.detail
                ?.let { "Local Dream недоступний: ${it.take(180)}" }
                ?: "Local Dream недоступний: запустіть модель у Local Dream"
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
            fallbackNote = localDreamFallbackNote,
        )
    }

    private fun compactReason(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        return (message.takeIf { it.isNotBlank() } ?: error::class.java.simpleName).take(180)
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
    val fallbackNote: String? = null,
)
