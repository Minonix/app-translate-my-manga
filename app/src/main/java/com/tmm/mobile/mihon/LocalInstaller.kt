package com.tmm.mobile.mihon

import java.security.MessageDigest

data class LocalInstallResult(val success: Boolean, val installedDocId: String?, val error: String?)

// PLAN_GEMINI.md 5.13 / CAHIER_DES_CHARGES.md 8.4 : ecriture sous nom temporaire cache, verification
// taille+SHA-256, remplacement de l'existant, renommage final. Jamais de .cbz partiel visible.
class LocalInstaller(private val fs: MihonFs) {

    fun install(
        localRootDocId: String,
        localFolderName: String,
        chapterFileName: String,
        jobId: String,
        content: ByteArray,
        expectedSize: Long,
        expectedSha256: String,
    ): LocalInstallResult {
        val folderDocId = fs.findChild(localRootDocId, localFolderName)?.docId
            ?: fs.createDir(localRootDocId, localFolderName)

        val tmpName = ".$chapterFileName.tmm-$jobId.tmp"
        val tmpDocId = fs.createFile(folderDocId, tmpName, "application/octet-stream")
        fs.openOutput(tmpDocId).use { it.write(content) }

        val actualSize = content.size.toLong()
        if (actualSize != expectedSize) {
            fs.delete(tmpDocId)
            return LocalInstallResult(false, null, "taille inattendue: $actualSize != $expectedSize")
        }
        val actualSha = sha256(content)
        if (actualSha != expectedSha256) {
            fs.delete(tmpDocId)
            return LocalInstallResult(false, null, "sha256 inattendu")
        }

        fs.findChild(folderDocId, chapterFileName)?.let { fs.delete(it.docId) }

        val renamedDocId = fs.rename(tmpDocId, chapterFileName)
        val renamedEntry = fs.findChild(folderDocId, chapterFileName)
        if (renamedEntry == null || renamedEntry.docId != renamedDocId) {
            fs.delete(renamedDocId)
            return LocalInstallResult(false, null, "renommage final different du nom attendu")
        }

        return LocalInstallResult(true, renamedDocId, null)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
