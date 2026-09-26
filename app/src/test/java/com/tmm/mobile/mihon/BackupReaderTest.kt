package com.tmm.mobile.mihon

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupReaderTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test fun read_decodesGzippedProtobufBackup() {
        val backup = MihonBackup(
            backupManga = listOf(
                MihonBackupManga(
                    source = 42L,
                    url = "/manga/1",
                    title = "One Piece",
                    categories = listOf(0L),
                    chapters = listOf(MihonBackupChapter(url = "/manga/1/ch1", name = "Chapitre 1", chapterNumber = 1f)),
                ),
            ),
            backupCategories = listOf(MihonBackupCategory(name = "Lecture", order = 0)),
            backupSources = listOf(MihonBackupSource(name = "MangaSource", sourceId = 42L)),
        )
        val bytes = ProtoBuf.encodeToByteArray(MihonBackup.serializer(), backup)
        val gzipped = ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(bytes) } }.toByteArray()

        val decoded = BackupReader.read(gzipped.inputStream())

        assertEquals(1, decoded.backupManga.size)
        assertEquals("One Piece", decoded.backupManga[0].title)
        assertEquals("Lecture", decoded.backupCategories[0].name)
        assertEquals("MangaSource", decoded.backupSources[0].name)
    }
}
