package com.sendmefile77.chronosphere.horde

import java.io.File
import java.security.MessageDigest

class HordeImageCache(
    private val root: File,
) {
    init {
        ensureRoot()
    }

    fun read(cacheKey: String): ByteArray? {
        require(cacheKey.isNotBlank())
        val file = fileFor(cacheKey)
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching { file.readBytes() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun write(cacheKey: String, bytes: ByteArray): File {
        require(cacheKey.isNotBlank())
        require(bytes.isNotEmpty())
        ensureRoot()

        val target = fileFor(cacheKey)
        val temporary = File(root, ".${target.name}.${System.nanoTime()}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            target.writeBytes(bytes)
            temporary.delete()
        }
        return target
    }

    fun remove(cacheKey: String): Boolean {
        require(cacheKey.isNotBlank())
        val file = fileFor(cacheKey)
        return !file.exists() || file.delete()
    }

    private fun ensureRoot() {
        if (!root.exists() && !root.mkdirs() && !root.isDirectory) {
            throw IllegalStateException("Unable to create AI Horde image cache directory")
        }
    }

    private fun fileFor(cacheKey: String): File = File(root, "${sha256(cacheKey)}.img")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
