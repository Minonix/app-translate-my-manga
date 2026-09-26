package com.tmm.mobile.mihon

import java.io.File
import java.io.InputStream
import java.io.OutputStream

// Double de test de MihonFs sur un dossier temporaire du disque : docId = chemin absolu.
class FakeMihonFs(val root: File) : MihonFs {
    var childrenCallCount = 0
        private set

    private fun file(docId: String) = File(docId)

    override fun children(dirDocId: String): List<MihonDocEntry> {
        childrenCallCount++
        val dir = file(dirDocId)
        return dir.listFiles().orEmpty().map { it.toEntry() }
    }

    override fun findChild(dirDocId: String, name: String): MihonDocEntry? =
        file(dirDocId).listFiles().orEmpty().firstOrNull { it.name == name }?.toEntry()

    override fun createDir(dirDocId: String, name: String): String {
        val dir = File(file(dirDocId), name)
        dir.mkdirs()
        return dir.absolutePath
    }

    override fun createFile(dirDocId: String, name: String, mimeType: String): String {
        val f = File(file(dirDocId), name)
        f.createNewFile()
        return f.absolutePath
    }

    override fun rename(docId: String, newName: String): String {
        val src = file(docId)
        val dst = File(src.parentFile, newName)
        src.renameTo(dst)
        return dst.absolutePath
    }

    override fun delete(docId: String) {
        file(docId).deleteRecursively()
    }

    override fun openInput(docId: String): InputStream = file(docId).inputStream()

    override fun openOutput(docId: String): OutputStream = file(docId).outputStream()

    private fun File.toEntry() = MihonDocEntry(
        docId = absolutePath,
        name = name,
        isDirectory = isDirectory,
        lastModified = lastModified(),
        size = if (isFile) length() else 0L,
    )
}
