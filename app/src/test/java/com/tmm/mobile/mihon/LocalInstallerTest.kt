package com.tmm.mobile.mihon

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInstallerTest {
    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun install_writesRenamesAndVerifies() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val fs = FakeMihonFs(root)
        val content = byteArrayOf(1, 2, 3, 4)
        val installer = LocalInstaller(fs)

        val result = installer.install(
            localRootDocId = root.absolutePath,
            localFolderName = "OnePiece",
            chapterFileName = "ch1.cbz",
            jobId = "job1",
            content = content,
            expectedSize = content.size.toLong(),
            expectedSha256 = sha256(content),
        )

        assertTrue(result.success)
        val installed = File(root, "OnePiece/ch1.cbz")
        assertTrue(installed.exists())
        assertTrue(File(root, "OnePiece").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun install_failsOnSizeMismatch_leavesNoPartialFile() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val fs = FakeMihonFs(root)
        val content = byteArrayOf(1, 2, 3)
        val installer = LocalInstaller(fs)

        val result = installer.install(
            localRootDocId = root.absolutePath,
            localFolderName = "OnePiece",
            chapterFileName = "ch1.cbz",
            jobId = "job1",
            content = content,
            expectedSize = 999L,
            expectedSha256 = sha256(content),
        )

        assertFalse(result.success)
        assertNull(result.installedDocId)
        assertTrue(File(root, "OnePiece").listFiles().orEmpty().isEmpty())
    }

    @Test fun install_replacesExistingChapterFile() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val fs = FakeMihonFs(root)
        val folder = File(root, "OnePiece").apply { mkdirs() }
        File(folder, "ch1.cbz").writeBytes(byteArrayOf(9, 9))

        val content = byteArrayOf(1, 2, 3)
        val installer = LocalInstaller(fs)
        val result = installer.install(
            localRootDocId = root.absolutePath,
            localFolderName = "OnePiece",
            chapterFileName = "ch1.cbz",
            jobId = "job2",
            content = content,
            expectedSize = content.size.toLong(),
            expectedSha256 = sha256(content),
        )

        assertTrue(result.success)
        assertEquals(content.toList(), File(folder, "ch1.cbz").readBytes().toList())
    }

    @Test fun ensureCover_writesCoverOnceAndNeverOverwrites() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        File(root, "Off to an Invincible Start").mkdirs()
        val installer = LocalInstaller(FakeMihonFs(root))

        assertTrue(installer.ensureCover(root.absolutePath, "Off to an Invincible Start") { "webp" to byteArrayOf(7, 8) })
        val folder = File(root, "Off to an Invincible Start")
        assertEquals(listOf("cover.webp"), folder.list()!!.toList())
        assertFalse(installer.ensureCover(root.absolutePath, "Off to an Invincible Start") { error("pas appele : couverture deja la") })
        assertEquals(listOf(7, 8), File(folder, "cover.webp").readBytes().map { it.toInt() })
    }

    @Test fun ensureCover_nothingToWrite_leavesFolderUntouched() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        File(root, "S").mkdirs()
        assertFalse(LocalInstaller(FakeMihonFs(root)).ensureCover(root.absolutePath, "S") { null })
        assertTrue(File(root, "S").list()!!.isEmpty())
    }
}
