package com.tmm.mobile.mihon

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** "cover.jpg", "Cover.webp"... : nom de couverture que la source Locale de Mihon reconnait. */
internal fun isCoverName(name: String): Boolean =
    name.substringBeforeLast('.').equals("cover", ignoreCase = true) && CbzPacker.isImage(name)

data class LocalInstallResult(val success: Boolean, val installedDocId: String?, val error: String?)

// PLAN_GEMINI.md 5.13 / CAHIER_DES_CHARGES.md 8.4 : ecriture sous nom temporaire cache, verification
// taille+SHA-256, remplacement de l'existant, renommage final. Jamais de .cbz partiel visible.
// R10 : ecriture en flux (pas de fichier entier en memoire) ; l'ancien chapitre est mis de cote sous un
// nom cache et n'est supprime qu'une fois le nouveau en place (restaure si le renommage final echoue).
class LocalInstaller(
    private val fs: MihonFs,
    // Detecteur de page blanche, injectable pour les tests (les octets factices ne se decodent pas).
    private val isBlank: (ByteArray) -> Boolean = BlankPage::isBlank,
) {
    companion object {
        // Partage entre les workers, meme si chacun ouvre son propre MihonTree.
        // ponytail: verrou global court, limite a la recherche/creation du dossier SAF.
        private val folderLock = Any()
        // Nombre borne de verrous : les series differentes restent paralleles.
        private val seriesLocks = Array(64) { Any() }
        private fun seriesLock(docId: String) = seriesLocks[(docId.hashCode() and Int.MAX_VALUE) % seriesLocks.size]

        /** Premiers CBZ du dossier dont une page a pu servir de couverture. */
        internal const val COVER_SOURCE_CHAPTERS = 3
    }


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
        val folderDocId = synchronized(folderLock) {
            val existing = fs.findChild(localRootDocId, localFolderName)
            require(existing == null || existing.isDirectory) { "Le nom de serie est deja un fichier" }
            existing?.docId ?: fs.createDir(localRootDocId, localFolderName)
        }

        return synchronized(seriesLock(folderDocId)) {
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
            LocalInstallResult(true, renamedDocId, null)
        }
    }

    /**
     * Pose ou remplace `cover.*` dans local/<serie>/. La source Locale de Mihon la lit en priorite ; sans
     * elle, elle tente d'extraire une page elle-meme et certaines series traduites restaient sans couverture.
     * [thumbnail] donne la vignette reseau de la serie (null si la serie n'en a pas), [page] une page d'un
     * chapitre ; chacun rend (extension, octets) ou null. Regles :
     * - pas de couverture : vignette, sinon page ;
     * - couverture blanche (credits poses par une ancienne version) : vignette, sinon page non blanche ;
     * - couverture tiree d'une page (identique a l'une des premieres pages des premiers CBZ) : vignette
     *   seulement, une page n'en remplace jamais une autre ;
     * - toute autre couverture est gardee, sans aucun telechargement.
     * Le nom d'une couverture existante est garde : la sauvegarde Mihon pointe vers lui.
     * Retourne vrai si une couverture a ete ecrite.
     */
    fun ensureCover(
        localRootDocId: String,
        localFolderName: String,
        thumbnail: (() -> Pair<String, ByteArray>?)? = null,
        page: () -> Pair<String, ByteArray>?,
    ): Boolean {
        val folderDocId = fs.findChild(localRootDocId, localFolderName)?.docId ?: return false
        return ensureCoverIn(folderDocId, emptyList(), thumbnail, page)
    }

    /** Meme chose quand le contenu du dossier est deja liste (scan de local/). */
    @Suppress("UNUSED_PARAMETER")
    fun ensureCoverIn(
        folderDocId: String,
        children: List<MihonDocEntry>,
        thumbnail: (() -> Pair<String, ByteArray>?)? = null,
        page: () -> Pair<String, ByteArray>?,
    ): Boolean {
        // La liste du scan peut dater d'avant une autre installation : relire sous le verrou.
        val before = synchronized(seriesLock(folderDocId)) { coverState(folderDocId, canReplacePageCover = thumbnail != null) }
        if (before.kind == CoverKind.KEPT) return false
        // Reseau hors verrou : les 64 verrous sont partages par toutes les series.
        val network = thumbnail?.invoke()
        return synchronized(seriesLock(folderDocId)) {
            val existing = coverFiles(fs.children(folderDocId))
            // Couverture changee pendant le telechargement (autre installation, autre scan) : on la laisse.
            if (existing.map(::fingerprint) != before.existing.map(::fingerprint)) return false
            val (ext, bytes) = network
                ?: (if (before.kind == CoverKind.FROM_PAGE) null else page())
                ?: return false
            if (existing.isNotEmpty() && isBlank(bytes)) return false
            // Deja la meme image (vignette egale a la premiere page) : rien a reecrire.
            if (existing.size == 1 && before.bytes.single()?.contentEquals(bytes) == true) return false
            writeCover(folderDocId, existing, existing.firstOrNull()?.name ?: "cover.${imageExtension(bytes, ext)}", bytes)
            true
        }
    }

    private enum class CoverKind { MISSING, BLANK, FROM_PAGE, KEPT }

    private class CoverState(val kind: CoverKind, val existing: List<MihonDocEntry>, val bytes: List<ByteArray?>)

    private fun coverFiles(children: List<MihonDocEntry>): List<MihonDocEntry> =
        children.filter { !it.isDirectory && isCoverName(it.name) }.sortedBy { it.name }

    private fun fingerprint(entry: MihonDocEntry) = Triple(entry.name, entry.size, entry.lastModified)

    private fun coverState(folderDocId: String, canReplacePageCover: Boolean): CoverState {
        val children = fs.children(folderDocId)
        val existing = coverFiles(children)
        if (existing.isEmpty()) return CoverState(CoverKind.MISSING, existing, emptyList())
        val bytes = existing.map { runCatching { fs.openInput(it.docId).use { input -> input.readBytes() } }.getOrNull() }
        val kind = when {
            bytes.any { it != null && isBlank(it) } -> CoverKind.BLANK
            // Sans vignette pour la remplacer, inutile d'ouvrir les chapitres.
            canReplacePageCover && bytes.any { it != null && isFromPage(it, children) } -> CoverKind.FROM_PAGE
            else -> CoverKind.KEPT
        }
        return CoverState(kind, existing, bytes)
    }

    /** Vrai si [cover] est l'une des premieres pages des premiers CBZ du dossier (couverture posee par TMM). */
    private fun isFromPage(cover: ByteArray, children: List<MihonDocEntry>): Boolean =
        children.filter { !it.isDirectory && it.name.endsWith(".cbz", ignoreCase = true) && CbzPacker.isEligible(it.name) }
            .sortedWith(compareBy(CbzPacker.NATURAL_ORDER) { it.name })
            .take(COVER_SOURCE_CHAPTERS)
            .any { cbz -> runCatching { fs.openInput(cbz.docId).use { CbzPacker.leadingPagesContain(it, cover) } }.getOrDefault(false) }

    /**
     * Ecrit [bytes] sous [name] : fichier cache d'abord, anciennes couvertures supprimees, puis renommage.
     * Jamais de createFile sur un nom pris (SAF rendrait « cover (1).jpg »).
     */
    private fun writeCover(folderDocId: String, existing: List<MihonDocEntry>, name: String, bytes: ByteArray) {
        val tmpName = ".$name.tmm.tmp"
        fs.findChild(folderDocId, tmpName)?.let { fs.delete(it.docId) }
        val tmpDocId = fs.createFile(folderDocId, tmpName, "application/octet-stream")
        val renamed = try {
            fs.openOutput(tmpDocId).use { it.write(bytes) }
            existing.forEach { fs.delete(it.docId) }
            fs.rename(tmpDocId, name)
        } catch (e: Exception) {
            runCatching { fs.delete(tmpDocId) }
            throw e
        }
        LocalCoverWrites.bump()
        // Le renommage SAF dedoublonne en silence si le nom est encore pris : pas de « cover (1) » laisse.
        if (fs.findChild(folderDocId, name)?.docId != renamed) {
            runCatching { fs.delete(renamed) }
            throw IOException("couverture renommee sous un autre nom que $name")
        }
    }

    private fun restore(backupDocId: String?, chapterFileName: String) {
        if (backupDocId != null) runCatching { fs.rename(backupDocId, chapterFileName) }
    }
}

/**
 * Compteur des couvertures ecrites dans local/ par TMM. Il entre dans la cle de cache Coil des couvertures
 * locales : une couverture reecrite sous le meme nom (meme URI) s'affiche sans redemarrer l'app.
 */
object LocalCoverWrites {
    private val counter = MutableStateFlow(0L)
    val generation: StateFlow<Long> = counter.asStateFlow()

    internal fun bump() = counter.update { it + 1 }
}
