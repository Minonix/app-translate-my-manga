package com.tmm.mobile.mihon

import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

// PLAN_GEMINI.md 5.6/5.7 : les sauvegardes Mihon (.tachibk) sont un flux protobuf gzip.
object BackupReader {
    @OptIn(ExperimentalSerializationApi::class)
    fun read(input: InputStream): MihonBackup {
        val bytes = GZIPInputStream(input).use { it.readBytes() }
        return ProtoBuf.decodeFromByteArray(MihonBackup.serializer(), bytes)
    }
}
