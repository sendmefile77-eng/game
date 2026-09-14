package com.sendmefile77.chronosphere.horde

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal const val GENERATED_IMAGE_CACHE_DIRECTORY = "generated-images-v2"

internal object HordeGenerationCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hordeClient = HordeClient()
    private val localDreamClient = LocalDreamClient()
    private val inFlight = ConcurrentHashMap<String, Deferred<HordePreparedImage>>()
    private val progressByCacheKey = ConcurrentHashMap<String, MutableStateFlow<ImageJobProgress>>()
    private val preparedByCacheKey = ConcurrentHashMap<String, HordePreparedImage>()
    private val retryNonceByCacheKey = ConcurrentHashMap<String, Int>()

    suspend fun load(
        filesDir: File,
        request: HordeImageRequest,
        timeoutMillis: Long,
        pollIntervalMillis: Long = 3_000L,
        localDreamRequest: HordeImageRequest? = null,
        localDreamTimeoutMillis: Long? = null,
    ): HordePreparedImage {
        preparedByCacheKey[request.cacheKey]?.let { return it }

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
            ).also { preparedByCacheKey[request.cacheKey] = it }
        }

        val localProfile = localDreamRequest ?: LocalDreamFastProfile.apply(request)
        val jobKey = listOf(
            request.cacheKey,
            localProfile.cacheKey,
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

    fun observeProgress(cacheKey: String): StateFlow<ImageJobProgress> =
        progressFlow(cacheKey)

    fun peekPrepared(cacheKey: String): HordePreparedImage? = preparedByCacheKey[cacheKey]

    fun retryNonce(cacheKey: String): Int = retryNonceByCacheKey[cacheKey] ?: 0

    fun nextRetryNonce(cacheKey: String): Int =
        retryNonceByCacheKey.merge(cacheKey, 1) { oldValue, increment -> oldValue + increment } ?: 1

    /**
     * Stops work for a scene that is no longer visible. Generation lives in an app-level scope so
     * it must be cancelled explicitly when Compose disposes the old scene; otherwise obsolete
     * portraits keep occupying Local Dream and later turns appear to be stuck at the start.
     */
    fun cancel(cacheKey: String) {
        val prefix = "$cacheKey|"
        inFlight.entries.toList().forEach { (jobKey, deferred) ->
            if (jobKey.startsWith(prefix) && inFlight.remove(jobKey, deferred)) {
                deferred.cancel()
            }
        }
        progressFlow(cacheKey).value = ImageJobProgress.idle()
    }

    fun invalidate(filesDir: File, cacheKey: String) {
        cancel(cacheKey)
        preparedByCacheKey.remove(cacheKey)
        HordeImageCache(File(filesDir, GENERATED_IMAGE_CACHE_DIRECTORY)).remove(cacheKey)
        progressFlow(cacheKey).value = ImageJobProgress.idle()
    }

    private fun progressFlow(cacheKey: String): MutableStateFlow<ImageJobProgress> =
        progressByCacheKey.getOrPut(cacheKey) { MutableStateFlow(ImageJobProgress.idle()) }

    private fun publish(cacheKey: String, progress: ImageJobProgress) {
        progressFlow(cacheKey).value = progress
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
            ).also { preparedByCacheKey[request.cacheKey] = it }
        }

        val references = HordeCharacterReferenceStore(File(filesDir, "horde-character-references"))
        val reference = request.referenceCacheKey?.let(references::read)
        val localReferenceBytes =
            if (localDreamRequest.referenceCacheKey != null) reference?.imageBytes else null

        var localDreamFallbackNote: String? = null
        val localStatus = localDreamClient.status()
        publish(
            request.cacheKey,
            if (localStatus.available) {
                ImageJobProgress.connecting(localStatus)
            } else {
                ImageJobProgress.unavailable(localStatus)
            },
        )
        val localResult = if (localStatus.available) {
            try {
                // Do not publish a fake 0/N diffusion step here. LocalDreamClient is serialized by
                // a mutex, so 0/N used to remain on screen while this job was only waiting for an
                // older request. The first determinate step is now shown only after the backend
                // actually reports progress.
                localDreamClient.generate(
                    request = localDreamRequest,
                    sourceImageBytes = localReferenceBytes,
                    timeoutMillis = (localDreamTimeoutMillis ?: timeoutMillis).coerceAtLeast(5_000L),
                    onProgress = { step ->
                        publish(request.cacheKey, ImageJobProgress.fromLocalDream(step))
                    },
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
                usedReference = localReferenceBytes != null,
                provider = ImageGenerationProvider.LOCAL_DREAM,
                actualWidth = localResult.width,
                actualHeight = localResult.height,
            ).also { preparedByCacheKey[request.cacheKey] = it }
        }

        publish(
            request.cacheKey,
            ImageJobProgress.horde(localDreamFallbackNote),
        )

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
        ).also { preparedByCacheKey[request.cacheKey] = it }
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

internal enum class ImageJobPhase {
    IDLE,
    CONNECTING,
    LOCAL_DREAM,
    UNAVAILABLE,
    HORDE,
}

internal data class ImageJobProgress(
    val phase: ImageJobPhase = ImageJobPhase.IDLE,
    val step: Int = 0,
    val totalSteps: Int = 0,
    val detail: String? = null,
) {
    val determinate: Boolean
        get() = phase == ImageJobPhase.LOCAL_DREAM && totalSteps > 0

    val fraction: Float
        get() = if (totalSteps <= 0) 0f else (step.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)

    val captionUk: String
        get() = when (phase) {
            ImageJobPhase.IDLE -> "очікування…"
            ImageJobPhase.CONNECTING -> "Local Dream · запускаємо…"
            ImageJobPhase.LOCAL_DREAM -> if (totalSteps > 0) {
                "Local Dream · крок $step/$totalSteps"
            } else {
                "Local Dream · дифузія…"
            }
            ImageJobPhase.UNAVAILABLE -> detail ?: "Local Dream недоступний · перехід на AI Horde…"
            ImageJobPhase.HORDE -> detail?.let { "AI Horde · $it" } ?: "AI Horde · чекаємо чергу…"
        }

    companion object {
        fun idle(): ImageJobProgress = ImageJobProgress()

        fun connecting(status: LocalDreamStatus): ImageJobProgress = ImageJobProgress(
            phase = ImageJobPhase.CONNECTING,
            detail = status.detail,
        )

        fun fromLocalDream(progress: LocalDreamProgress): ImageJobProgress = ImageJobProgress(
            phase = ImageJobPhase.LOCAL_DREAM,
            step = progress.step,
            totalSteps = progress.totalSteps,
        )

        fun unavailable(status: LocalDreamStatus): ImageJobProgress = ImageJobProgress(
            phase = ImageJobPhase.UNAVAILABLE,
            detail = status.detail?.take(180),
        )

        fun horde(reason: String?): ImageJobProgress = ImageJobProgress(
            phase = ImageJobPhase.HORDE,
            detail = reason?.take(180),
        )
    }
}
