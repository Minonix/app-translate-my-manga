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

    // Octets encodes a la main avec les numeros de mihonapp/mihon (BackupManga : genre=7, status=8,
    // chapters=16, categories=17) : un aller-retour via notre propre modele ne detecte pas un decalage.
    @Test fun read_decodesRealMihonFieldNumbers() {
        fun varint(n: Long): ByteArray {
            val out = ByteArrayOutputStream(); var v = n
            while (v >= 0x80) { out.write(((v and 0x7f) or 0x80).toInt()); v = v ushr 7 }
            out.write(v.toInt()); return out.toByteArray()
        }
        fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())
        fun ld(field: Int, b: ByteArray) = tag(field, 2) + varint(b.size.toLong()) + b
        fun str(field: Int, s: String) = ld(field, s.toByteArray())
        fun num(field: Int, n: Long) = tag(field, 0) + varint(n)

        val chapter = str(1, "/manga/1/ch1") + str(2, "Chapitre 1") + num(6, 12) + num(10, 3)
        val manga = num(1, 42) + str(2, "/manga/1") + str(3, "One Piece") + str(6, "desc") +
            str(7, "Action") + str(7, "Aventure") + num(8, 1) + num(13, 1700000000000) + num(14, 0) +
            ld(16, chapter) + num(17, 2) + num(100, 1) + num(101, 0)
        val backup = ld(1, manga) + ld(2, str(1, "Lecture") + num(2, 2) + num(100, 0)) +
            ld(101, str(1, "MangaSource") + num(2, 42))
        val gzipped = ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(backup) } }.toByteArray()

        val decoded = BackupReader.read(gzipped.inputStream())

        val m = decoded.backupManga.single()
        assertEquals(1, m.status)
        assertEquals(1700000000000, m.dateAdded)
        assertEquals(listOf(2L), m.categories)
        assertEquals("Chapitre 1", m.chapters.single().name)
    }
}
