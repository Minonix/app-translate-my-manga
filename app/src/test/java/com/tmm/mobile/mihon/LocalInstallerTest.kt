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

    @Test fun concurrentInstallers_shareOneSeriesFolder() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val delegate = FakeMihonFs(root)
        val firstLookup = java.util.concurrent.CountDownLatch(1)
        val secondLookup = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val secondThread = java.util.concurrent.atomic.AtomicReference<Thread>()
        val fs = object : MihonFs by delegate {
            override fun findChild(dirDocId: String, name: String): MihonDocEntry? {
                val found = delegate.findChild(dirDocId, name)
                if (dirDocId == root.absolutePath && name == "Serie") {
                    if (calls.incrementAndGet() == 1) {
                        firstLookup.countDown()
                        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
                        while (secondLookup.count != 0L && secondThread.get()?.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                            Thread.yield()
                        }
                        assertTrue("Le second installateur doit atteindre la recherche ou le verrou",
                            secondLookup.count == 0L || secondThread.get()?.state == Thread.State.BLOCKED)
                    } else secondLookup.countDown()
                }
                return found
            }
            override fun createDir(dirDocId: String, name: String): String = synchronized(delegate) {
                val actual = if (delegate.findChild(dirDocId, name) == null) name else "$name (1)"
                delegate.createDir(dirDocId, actual)
            }
        }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val content = byteArrayOf(1, 2, 3)
        fun submit(chapter: String) = pool.submit<LocalInstallResult> {
            if (chapter == "b") secondThread.set(Thread.currentThread())
            LocalInstaller(fs).install(root.absolutePath, "Serie", "$chapter.cbz", chapter,
                content, content.size.toLong(), sha256(content))
        }
        try {
            val first = submit("a")
            assertTrue(firstLookup.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val second = submit("b")
            assertTrue(first.get(5, java.util.concurrent.TimeUnit.SECONDS).success)
            assertTrue(second.get(5, java.util.concurrent.TimeUnit.SECONDS).success)
            assertEquals(listOf("Serie"), root.list()!!.toList())
            assertEquals(setOf("a.cbz", "b.cbz"), File(root, "Serie").list()!!.toSet())
        } finally {
            pool.shutdownNow()
        }
    }

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

    @Test fun ensureCover_replacesBlankCoverOnly() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val folder = File(root, "Serie").apply { mkdirs() }
        File(folder, "cover.png").writeBytes(byteArrayOf(0))
        // Octet 0 = page blanche pour ce detecteur factice.
        val installer = LocalInstaller(FakeMihonFs(root)) { it.firstOrNull() == 0.toByte() }

        assertFalse(installer.ensureCover(root.absolutePath, "Serie") { "jpg" to byteArrayOf(0) })
        assertTrue(installer.ensureCover(root.absolutePath, "Serie") { "jpg" to byteArrayOf(5) })
        assertEquals(listOf("cover.jpg"), folder.list()!!.toList())
        assertFalse(installer.ensureCover(root.absolutePath, "Serie") { error("couverture pleine gardee") })
    }

    @Test fun ensureCover_staleScanDoesNotOverwriteAnotherInstallerCover() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val folder = File(root, "Serie").apply { mkdirs() }
        val fs = FakeMihonFs(root)
        val stale = fs.children(folder.absolutePath)
        assertTrue(LocalInstaller(fs).ensureCoverIn(folder.absolutePath, stale) { "jpg" to byteArrayOf(1) })
        assertFalse(LocalInstaller(fs).ensureCoverIn(folder.absolutePath, stale) { error("couverture deja ecrite") })
        assertEquals(listOf(1), File(folder, "cover.jpg").readBytes().map { it.toInt() })
    }

    @Test fun ensureCover_nothingToWrite_leavesFolderUntouched() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        File(root, "S").mkdirs()
        assertFalse(LocalInstaller(FakeMihonFs(root)).ensureCover(root.absolutePath, "S") { null })
        assertTrue(File(root, "S").list()!!.isEmpty())
    }
}
