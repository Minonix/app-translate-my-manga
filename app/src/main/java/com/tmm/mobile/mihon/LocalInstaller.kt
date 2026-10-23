package com.tmm.mobile.mihon

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/** "cover.jpg", "Cover.webp"... : nom de couverture que la source Locale de Mihon reconnait. */
internal fun isCoverName(name: String): Boolean =
    name.substringBeforeLast('.').equals("cover", ignoreCase = true) && CbzPacker.isImage(name)

data class LocalInstallResult(val success: Boolean, val installedDocId: String?, val error: String?)

// PLAN_GEMINI.md 5.13 / CAHIER_DES_CHARGES.md 8.4 : ecriture sous nom temporaire cache, verification
// taille+SHA-256, remplacement de l'existant, renommage final. Jamais de .cbz partiel visible.
// R10 : ecriture en flux (pas de fichier entier en memoire) ; l'ancien chapitre est mis de cote sous un
// nom cache et n'est supprime qu'une fois le nouveau en place (restaure si le renommage final echoue).
class LocalInstaller(private val fs: MihonFs) {

    fun install(
        localRootDocId: String,
        localFolderName: String,
        chapterFileName: String,
        jobId: String,
        content: ByteArray,
        expectedSize: Long,
        expectedSha256: String,
    ): LocalInstallResult = install(
        localRootDocId, localFolderName, chapterFileName, jobId,
        { ByteArrayInputStream(content) }, expectedSize, expectedSha256,
    )

    fun install(
        localRootDocId: String,
        localFolderName: String,
        chapterFileName: String,
        jobId: String,
        openContent: () -> InputStream,
        expectedSize: Long,
        expectedSha256: String,
    ): LocalInstallResult {
        val folderDocId = fs.findChild(localRootDocId, localFolderName)?.docId
            ?: fs.createDir(localRootDocId, localFolderName)

        val tmpName = ".$chapterFileName.tmm-$jobId.tmp"
        val backupName = ".$chapterFileName.tmm-$jobId.old"
        // Restes d'un essai interrompu : un createFile sur un nom pris donnerait « nom (1) ».
        fs.findChild(folderDocId, tmpName)?.let { fs.delete(it.docId) }
        val tmpDocId = fs.createFile(folderDocId, tmpName, "application/octet-stream")
        val digest = MessageDigest.getInstance("SHA-256")
        var actualSize = 0L
        openContent().use { input ->
            fs.openOutput(tmpDocId).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    actualSize += read
                }
            }
        }

        if (actualSize != expectedSize) {
            fs.delete(tmpDocId)
            return LocalInstallResult(false, null, "taille inattendue: $actualSize != $expectedSize")
        }
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        if (actualSha != expectedSha256) {
            fs.delete(tmpDocId)
            return LocalInstallResult(false, null, "sha256 inattendu")
        }

        // L'ancien chapitre est renomme (pas supprime) : si la suite echoue, il est remis en place.
        val backupDocId = fs.findChild(folderDocId, chapterFileName)?.let { old ->
            fs.findChild(folderDocId, backupName)?.let { fs.delete(it.docId) }
            fs.rename(old.docId, backupName)
        }

        val renamedDocId = try {
            fs.rename(tmpDocId, chapterFileName)
        } catch (e: Exception) {
            restore(backupDocId, chapterFileName)
            throw e
        }
        val renamedEntry = fs.findChild(folderDocId, chapterFileName)
        if (renamedEntry == null || renamedEntry.docId != renamedDocId) {
            fs.delete(renamedDocId)
            restore(backupDocId, chapterFileName)
            return LocalInstallResult(false, null, "renommage final different du nom attendu")
        }

        backupDocId?.let { fs.delete(it) }
        return LocalInstallResult(true, renamedDocId, null)
    }

    /**
     * Pose `cover.<ext>` dans local/<serie>/ s'il n'y a encore aucune couverture. La source Locale de Mihon
     * la lit en priorite ; sans elle, elle tente d'extraire une page elle-meme et certaines series
     * traduites restaient sans couverture. [image] donne (extension, octets), null si rien a poser.
     * Retourne vrai si une couverture a ete ecrite.
     */
    fun ensureCover(localRootDocId: String, localFolderName: String, image: () -> Pair<String, ByteArray>?): Boolean {
        val folderDocId = fs.findChild(localRootDocId, localFolderName)?.docId ?: return false
        return ensureCoverIn(folderDocId, fs.children(folderDocId), image)
    }

    /** Meme chose quand le contenu du dossier est deja liste (scan de local/). */
    fun ensureCoverIn(folderDocId: String, children: List<MihonDocEntry>, image: () -> Pair<String, ByteArray>?): Boolean {
        if (children.any { !it.isDirectory && isCoverName(it.name) }) return false
        val (ext, bytes) = image() ?: return false
        val name = "cover.${ext.lowercase().ifBlank { "jpg" }}"
        val tmpName = ".$name.tmm.tmp"
        fs.findChild(folderDocId, tmpName)?.let { fs.delete(it.docId) }
        val tmpDocId = fs.createFile(folderDocId, tmpName, "application/octet-stream")
        try {
            fs.openOutput(tmpDocId).use { it.write(bytes) }
            fs.rename(tmpDocId, name)
        } catch (e: Exception) {
            runCatching { fs.delete(tmpDocId) }
            throw e
        }
        return true
    }

    private fun restore(backupDocId: String?, chapterFileName: String) {
        if (backupDocId != null) runCatching { fs.rename(backupDocId, chapterFileName) }
    }
}
