package com.sendmefile77.chronosphere.storage

data class SaveMetadata(
    val id: String,
    val worldSeed: Long,
    val tick: Long,
    val schemaVersion: Int,
)

interface SaveStore {
    fun list(): List<SaveMetadata>
    fun write(metadata: SaveMetadata, payload: ByteArray)
    fun read(id: String): ByteArray?
    fun delete(id: String)
}
