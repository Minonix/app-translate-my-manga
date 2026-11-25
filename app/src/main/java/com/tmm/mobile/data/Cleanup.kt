package com.tmm.mobile.data

import com.tmm.mobile.core.AppLog
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.mihon.MihonDocEntry
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonNaming
import com.tmm.mobile.mihon.MihonRoot
import com.tmm.mobile.protocol.CommandResult
import com.tmm.mobile.transport.AuthExpiredException
import com.tmm.mobile.transport.JobFolderListing
import com.tmm.mobile.transport.JobTransport
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Un fichier temporaire de Mihon plus recent est peut-etre un telechargement en cours.
const val CLEANUP_TMP_MIN_AGE_MS = 24L * 3600 * 1000

private val DUPLICATE_FILE = Regex("""^(.*) \((\d+)\)\.(cbz|jpe?g|png|webp)$""")
// Copie SAF d'un telechargement : le suffixe suit le hachage d'URL de Mihon (MihonNaming.isSafCopy).
private val DOWNLOAD_BASE = Regex("""_[0-9a-f]{6}$""")
// Chapitre installe par TMM (cNNNN - ...) ou couverture de la serie.
private val LOCAL_CHAPTER_BASE = Regex("""^c\d+""")
private const val COVER_BASE = "cover"
private const val TERMINAL_UNKNOWN_MIN_AGE_MS = CLEANUP_TMP_MIN_AGE_MS

/** Telephone : null = arbre Mihon absent ou inaccessible. */
data class PhoneCleanup(
    val tmpRemoved: Int = 0,
    val duplicatesRenamed: Int = 0,
    val duplicatesRemoved: Int = 0,
    val duplicatesToCheck: Int = 0,
    val errors: Int = 0,
) {
    val changed: Boolean get() = tmpRemoved + duplicatesRenamed + duplicatesRemoved > 0
}

/** Drive : `unavailable` = boite aux lettres pas etablie ou Drive injoignable (rien supprime). */
data class DriveCleanup(val folders: Int = 0, val failed: Int = 0, val unavailable: Boolean = false)

enum class PcCleanupState {
    // Apercu : la commande partira a la confirmation.
    WILL_ASK,
    // Aucun settings.json recu : le PC n'a jamais ete vu, rien n'est envoye.
    NEVER_SEEN,
    // Commande en file, accuse pas encore recu (PC eteint ou cycle en cours).
    WAITING,
    DONE,
    // « Type de commande inconnu » : PC plus ancien que la commande, sans gravite.
    OUTDATED,
    REJECTED,
}

data class PcCleanup(val state: PcCleanupState, val cmdId: String? = null, val forgotten: Int? = null)

data class CleanupReport(val phone: PhoneCleanup?, val drive: DriveCleanup, val pc: PcCleanup)

/**
 * Bouton « Nettoyer » des Reglages : telephone (SAF), Drive (dossiers jobs/) et PC (commande `cleanup`).
 * preview() et execute() passent par les memes regles ; execute() recalcule tout au lieu de reprendre
 * l'apercu. Chaque partie echoue seule : une session Drive expiree n'annule pas le menage Mihon.
 *
 * Pas de purge de la base locale : l'Activite et le statut d'un chapitre ne lisent que le dernier job du
 * chapitre (deriveStatus), les jobs plus anciens ne produisent aucune ligne fantome. Supprimer le dernier
 * job (installe, annule, en echec) ferait au contraire reapparaitre le precedent ou « a envoyer » (R3).
 */
class Cleanup(
    private val db: TmmDatabase,
    private val transport: JobTransport,
    private val settingsSync: SettingsSync,
    private val clock: Clock,
    private val mihonRoot: suspend () -> MihonRoot?,
) {
    suspend fun preview(): CleanupReport = run(dryRun = true)

    suspend fun execute(): CleanupReport = run(dryRun = false)

    private suspend fun run(dryRun: Boolean): CleanupReport {
        val phone = try {
            mihonRoot()?.let { root -> withContext(Dispatchers.IO) { cleanPhone(root, dryRun) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("Cleanup", "arbre Mihon inaccessible : ${e.message}")
            null
        }
        val drive = cleanDrive(dryRun)
        val pc = when {
            settingsSync.readOnly() -> PcCleanup(PcCleanupState.NEVER_SEEN)
            dryRun -> PcCleanup(PcCleanupState.WILL_ASK)
            else -> settingsSync.enqueueCleanup()?.let { PcCleanup(PcCleanupState.WAITING, it) }
                ?: PcCleanup(PcCleanupState.NEVER_SEEN)
        }
        return CleanupReport(phone, drive, pc)
    }

    // --- telephone ---

    private fun cleanPhone(root: MihonRoot, dryRun: Boolean): PhoneCleanup {
        val fs = root.fs
        val cutoff = clock.nowMillis() - CLEANUP_TMP_MIN_AGE_MS
        val tally = PhoneTally()
        fs.findChild(root.rootDocId, "downloads")?.takeIf { it.isDirectory }?.let { downloads ->
            for (source in fs.children(downloads.docId).filter { it.isDirectory }) {
                for (series in fs.children(source.docId).filter { it.isDirectory }) {
                    val entries = fs.children(series.docId)
                    val removed = HashSet<String>()
                    for (entry in entries.filter { isMihonTemp(it.name) }) {
                        if (!isStale(fs, entry, cutoff)) continue
                        tally.attempt {
                            if (!dryRun) fs.delete(entry.docId)
                            removed += entry.name
                            tally.tmp++
                        }
                    }
                    dedupe(fs, series.docId, entries.filter { it.name !in removed }, dryRun, tally) { base, ext ->
                        ext == "cbz" && DOWNLOAD_BASE.containsMatchIn(base)
                    }
                }
            }
        }
        fs.findChild(root.rootDocId, "local")?.takeIf { it.isDirectory }?.let { local ->
            for (series in fs.children(local.docId).filter { it.isDirectory }) {
                val entries = fs.children(series.docId)
                // LocalInstaller ecrit .<nom>.tmm-<jobId>.tmp puis garde un .old : installation en cours.
                if (entries.any { isInstallInFlight(it.name) }) continue
                dedupe(fs, series.docId, entries, dryRun, tally) { base, ext ->
                    if (ext == "cbz") LOCAL_CHAPTER_BASE.containsMatchIn(base) else base == COVER_BASE
                }
            }
        }
        return PhoneCleanup(tally.tmp, tally.renamed, tally.removed, tally.toCheck, tally.errors)
    }

    // « Chapitre (1).cbz » ou « cover (1).jpg » : renomme si le nom sans suffixe est libre, supprime si ce nom
    // existe avec la meme taille, garde sinon (« a verifier »). Traite dans l'ordre, (1) puis (2), sur l'etat
    // deja modifie. Seuls les noms que Mihon ou TMM produisent sont touches (isCopy) : un titre qui finit
    // vraiment par « (2) » n'a ni le hachage de Mihon ni le prefixe cNNNN, il n'est donc pas pris pour une copie.
    // Une couverture copiee n'est jamais renommee si une autre cover.* existe : deux couvertures resteraient.
    private fun dedupe(
        fs: MihonFs,
        dirDocId: String,
        entries: List<MihonDocEntry>,
        dryRun: Boolean,
        tally: PhoneTally,
        isCopy: (baseName: String, ext: String) -> Boolean,
    ) {
        val byName = entries.associateByTo(HashMap()) { it.name }
        val duplicates = entries
            .filter { !it.isDirectory }
            .mapNotNull { e ->
                DUPLICATE_FILE.matchEntire(e.name)
                    ?.takeIf { m -> isCopy(m.groupValues[1], m.groupValues[3].lowercase()) }
                    ?.let { m -> Dup(e, m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3]) }
            }
            .sortedWith(compareBy({ it.baseName }, { it.index }))
        for ((dup, baseName, _, ext) in duplicates) {
            val target = "$baseName.$ext"
            val otherCover = baseName == COVER_BASE &&
                byName.keys.any { it != target && it != dup.name && it.substringBeforeLast('.') == COVER_BASE }
            tally.attempt {
                val existing = if (dryRun) byName[target] else fs.findChild(dirDocId, target)
                val sameChapterDir = byName[baseName]?.isDirectory == true
                when {
                    sameChapterDir || existing?.isDirectory == true -> tally.toCheck++
                    existing == null && !otherCover -> {
                        if (!dryRun) {
                            fs.rename(dup.docId, target)
                            check(fs.findChild(dirDocId, target) != null) { "renommage de ${dup.name} non applique" }
                        }
                        byName.remove(dup.name)
                        byName[target] = dup.copy(name = target)
                        tally.renamed++
                    }
                    existing != null && dup.size > 0 && existing.size == dup.size -> {
                        if (!dryRun) fs.delete(dup.docId)
                        byName.remove(dup.name)
                        tally.removed++
                    }
                    else -> tally.toCheck++
                }
            }
        }
    }

    private data class Dup(val entry: MihonDocEntry, val baseName: String, val index: Int, val ext: String)

    private fun isStale(fs: MihonFs, entry: MihonDocEntry, cutoff: Long): Boolean {
        val stamps = buildList {
            add(entry.lastModified)
            if (entry.isDirectory) fs.children(entry.docId).forEach { add(it.lastModified) }
        }
        // 0 = date inconnue (colonne SAF absente) : garde, plutot que la lire comme 1970.
        if (stamps.any { it <= 0L }) return false
        return stamps.max() < cutoff
    }

    private class PhoneTally {
        var tmp = 0
        var renamed = 0
        var removed = 0
        var toCheck = 0
        var errors = 0

        // Une entree en echec (SAF revoque, fichier disparu) n'arrete pas les autres.
        inline fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("Cleanup", "entree Mihon non nettoyee : ${e.message}")
                errors++
            }
        }
    }

    // --- Drive ---

    private suspend fun cleanDrive(dryRun: Boolean): DriveCleanup {
        val folders = try {
            transport.listJobFolders()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("Cleanup", "dossiers Drive non listes : ${e.message}")
            null
        } ?: return DriveCleanup(unavailable = true)
        val jobs = db.jobDao().getAll(folders.map { it.name }.distinct()).associateBy { it.jobId }
        val now = clock.nowMillis()
        var deleted = 0
        var failed = 0
        for (folder in folders) {
            try {
                val job = jobs[folder.name]
                val delete = if (job != null) driveFolderDeletable(job) else unknownFolderDeletable(folder, now)
                if (!delete) continue
                if (!dryRun) {
                    transport.deleteFolder(folder.id)
                    if (job != null && job.driveDeletedAt == null) db.jobDao().upsert(job.copy(driveDeletedAt = now))
                }
                deleted++
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthExpiredException) {
                AppLog.d("Cleanup", "session Drive expiree pendant le nettoyage")
                return DriveCleanup(deleted, failed + 1, unavailable = deleted == 0)
            } catch (e: Exception) {
                AppLog.d("Cleanup", "dossier Drive ${folder.name} non supprime : ${e.message}")
                failed++
            }
        }
        return DriveCleanup(deleted, failed)
    }

    // Dossier d'un job inconnu de la base (autre telephone, base effacee) : supprime seulement si le
    // telephone l'a installe (received.json) ou si le PC l'a clos en echec/annulation depuis plus de 24 h.
    // Un result.json sans received.json reste : la reprise R14 peut encore l'installer.
    private suspend fun unknownFolderDeletable(folder: JobFolderListing, now: Long): Boolean {
        if ("received.json" in folder.files) return true
        if ("result.json" in folder.files) return false
        val status = folder.files["status.json"] ?: return false
        val modified = status.modifiedTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return false
        if (now - modified < TERMINAL_UNKNOWN_MIN_AGE_MS) return false
        return transport.readJobFolderState(status.id) in setOf("failed", "cancelled")
    }

    companion object {
        /**
         * Dossier Drive d'un job connu : supprime seulement si le job est fini cote telephone et que plus
         * rien ne le relira. Jamais un job en file, en cours, ou dont le resultat attend son installation.
         */
        fun driveFolderDeletable(job: JobEntity): Boolean = when {
            job.phoneState == "INSTALLED" -> job.receivedWrittenAt != null
            job.phoneState in setOf("PREPARING", "UPLOADING") -> false
            job.pcState == "cancelled" -> true
            job.phoneState == "FAILED" ->
                job.errorCode != LocalErrorCode.PC_STALLED && !job.reusesDeliveredResult() &&
                    job.pcState in setOf(null, "failed", "cancelled", "done")
            else -> false
        }

        fun isMihonTemp(name: String): Boolean =
            MihonNaming.isIncompleteChapterDirName(name.removeSuffix(".cbz")) || name.endsWith(".tmp")

        private fun isInstallInFlight(name: String): Boolean =
            (name.contains(".tmm-") && name.endsWith(".tmp")) || name.endsWith(".old")

        // Accuse du PC pour la commande `cleanup`. Un refus « Type de commande inconnu » = PC pas a jour.
        fun pcOutcome(banner: SyncBanner, result: CommandResult?): PcCleanupState = when (banner) {
            SyncBanner.APPLIED -> PcCleanupState.DONE
            SyncBanner.REJECTED ->
                if (result?.code == "bad_request" && result.message == UNKNOWN_COMMAND_MESSAGE) PcCleanupState.OUTDATED
                else PcCleanupState.REJECTED
            else -> PcCleanupState.WAITING
        }

        const val UNKNOWN_COMMAND_MESSAGE = "Type de commande inconnu"
    }
}
