package com.sendmefile77.chronosphere.horde

import android.content.Context
import java.io.File

internal enum class LocalDreamPromptStyle {
    ILLUSTRIOUS_DANBOORU,
    PHOTOREAL_KEEP,
}

internal data class LocalDreamModelPack(
    val id: String,
    val titleUk: String,
    val subtitleUk: String,
    val hintUk: String,
    val steps: Int,
    val cfgScale: Double,
    val samplerName: String,
    val timeoutMs: Long,
    val promptStyle: LocalDreamPromptStyle,
    val qualityPrefix: String,
    val extraNegative: String,
    val hordePreferredModels: List<String>,
)

internal object LocalDreamModelPacks {
    const val ILLUSTRIOUS = "illustrious"
    const val CYBERREALISTIC = "cyberrealistic"

    val illustrious = LocalDreamModelPack(
        id = ILLUSTRIOUS,
        titleUk = "Illustrious v16",
        subtitleUk = "Аніме / WAI Illustrious SDXL",
        hintUk = "У Local Dream відкрий Illustrious v16. Епоха лишається в промпті.",
        steps = 24,
        cfgScale = 5.5,
        samplerName = "k_euler_a",
        timeoutMs = 120_000L,
        promptStyle = LocalDreamPromptStyle.ILLUSTRIOUS_DANBOORU,
        qualityPrefix = "",
        extraNegative = "",
        hordePreferredModels = listOf(
            "WAI-NSFW-illustrious-SDXL",
            "AlbedoBase XL (SDXL)",
            "CyberRealistic Pony",
        ),
    )

    val cyberRealistic = LocalDreamModelPack(
        id = CYBERREALISTIC,
        titleUk = "CyberRealistic v10",
        subtitleUk = "Реалізм / CyberRealistic SDXL",
        hintUk = "У Local Dream відкрий CyberRealistic v10. Гра лишає епоху в промпті й ставить DPM++ 2M, 20 кроків, CFG 7.",
        steps = 20,
        cfgScale = 7.0,
        samplerName = "k_dpmpp_2m",
        timeoutMs = 120_000L,
        promptStyle = LocalDreamPromptStyle.PHOTOREAL_KEEP,
        qualityPrefix = "photorealistic, cinematic lighting, natural skin, detailed materials",
        extraNegative = "jpeg artifacts, signature, watermark, username, blurry, anime, illustration, cartoon, 1girl solo close-up",
        hordePreferredModels = listOf(
            "CyberRealistic XL",
            "CyberRealistic Pony",
            "AbsoluteReality",
            "Realistic Vision",
        ),
    )

    val all: List<LocalDreamModelPack> = listOf(illustrious, cyberRealistic)

    fun byId(id: String?): LocalDreamModelPack =
        all.firstOrNull { it.id == id } ?: illustrious
}

internal object LocalDreamModelPackStore {
    private const val FILE_NAME = "local_dream_model_pack.txt"

    fun load(context: Context): LocalDreamModelPack =
        LocalDreamModelPacks.byId(
            runCatching {
                File(context.noBackupFilesDir, FILE_NAME)
                    .takeIf { it.isFile }
                    ?.readText(Charsets.UTF_8)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull(),
        )

    fun save(context: Context, pack: LocalDreamModelPack) {
        val file = File(context.noBackupFilesDir, FILE_NAME)
        file.parentFile?.mkdirs()
        file.writeText(pack.id, Charsets.UTF_8)
        LocalDreamModelPackRuntime.select(pack)
    }
}

internal object LocalDreamModelPackRuntime {
    @Volatile
    private var currentPack: LocalDreamModelPack = LocalDreamModelPacks.illustrious

    fun current(): LocalDreamModelPack = currentPack

    fun select(pack: LocalDreamModelPack) {
        currentPack = pack
    }
}
