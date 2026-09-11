package com.sendmefile77.chronosphere.horde

import java.io.File
import java.security.MessageDigest

data class HordeCharacterReference(
    val imageBytes: ByteArray,
    val model: String?,
) {
    init { require(imageBytes.isNotEmpty()) }
}

/**
 * Stores one safe canonical portrait per visual identity. References are intentionally separate
 * from ordinary scene cache entries so NSFW generations can never replace the canonical image.
 */
class HordeCharacterReferenceStore(
    private val root: File,
) {
    init { ensureRoot() }

    fun read(referenceKey: String): HordeCharacterReference? {
        require(referenceKey.isNotBlank())
        val id = sha256(referenceKey)
        val image = File(root, "$id.webp")
        if (!image.isFile || image.length() <= 0L) return null
        val bytes = runCatching { image.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val modelFile = File(root, "$id.model")
        val model = runCatching {
            modelFile.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim()
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return HordeCharacterReference(bytes, model)
    }

    @Synchronized
    fun writeIfAbsent(referenceKey: String, bytes: ByteArray, model: String?): HordeCharacterReference {
        require(referenceKey.isNotBlank())
        require(bytes.isNotEmpty())
        read(referenceKey)?.let { return it }
        ensureRoot()

        val id = sha256(referenceKey)
        val image = File(root, "$id.webp")
        val temporary = File(root, ".$id.${System.nanoTime()}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(image)) {
            image.writeBytes(bytes)
            temporary.delete()
        }
        val cleanModel = model?.trim()?.takeIf { it.isNotBlank() }
        cleanModel?.let { File(root, "$id.model").writeText(it, Charsets.UTF_8) }
        return HordeCharacterReference(bytes, cleanModel)
    }

    fun remove(referenceKey: String): Boolean {
        require(referenceKey.isNotBlank())
        val id = sha256(referenceKey)
        val imageDeleted = File(root, "$id.webp").let { !it.exists() || it.delete() }
        val modelDeleted = File(root, "$id.model").let { !it.exists() || it.delete() }
        return imageDeleted && modelDeleted
    }

    private fun ensureRoot() {
        if (!root.exists() && !root.mkdirs() && !root.isDirectory) {
            throw IllegalStateException("Unable to create AI Horde character reference directory")
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
