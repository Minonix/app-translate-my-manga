package com.tmm.mobile.data

import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.status.ChapterJoinRow
import com.tmm.mobile.data.status.isActive
import com.tmm.mobile.data.status.loosePath
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.protocol.MihonPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Cases cochees dans la feuille "Supprimer" de la Fiche serie. */
data class DeleteOptions(
    val localFr: Boolean,
    val mihon: Boolean,
    val pc: Boolean,
) {
    val any: Boolean get() = localFr || mihon || pc
}

/**
 * Bilan d'une suppression. `failed` compte les fichiers SAF qui n'ont pas pu etre supprimes (ligne
 * Room conservee). `pcUnavailable` : la case PC etait cochee pour des chapitres presents sur le PC,
 * mais aucune commande n'a pu etre mise en file (settings.json du PC jamais recu).
 */
data class DeleteOutcome(
    val chapters: Int,
    val skippedActive: Int,
    val localFrDeleted: Int,
    val mihonDeleted: Int,
    val failed: Int,
    val pcChapters: Int,
    val pcUnavailable: Boolean,
)

/**
 * Suppression de chapitres depuis la Fiche serie : CBZ FR installe dans local/ (Mihon), chapitre
 * telecharge par Mihon dans downloads/ (irreversible) et/ou fichiers sur le PC via une commande
 * "delete" (SettingsSync). Les chapitres dont le dernier job est actif sont ignores.
 * `fs` renvoie l'arbre SAF Mihon (null s'il n'est pas configure). Les docId SAF viennent de Room
 * (champ documentUri, qui porte en realite un docId, cf. DownloadScanner/InstallJobWorker).
 */
class ChapterDeleter(
    private val db: TmmDatabase,
    private val settingsSync: SettingsSync,
    private val fs: suspend () -> MihonFs?,
) {

    suspend fun delete(rows: List<ChapterJoinRow>, options: DeleteOptions): DeleteOutcome {
        val (eligible, skipped) = rows.partition { !isSkipped(it) }
        if (!options.any || eligible.isEmpty()) {
            return DeleteOutcome(eligible.size, skipped.size, 0, 0, 0, 0, pcUnavailable = false)
        }

        // Chemins PC lus avant toute suppression locale : seuls les chapitres connus de l'index PC.
        val pcPaths = if (options.pc) eligible.mapNotNull { pcPathOf(it) }.distinctBy { it.flatten() } else emptyList()

        var localFrDeleted = 0
        var mihonDeleted = 0
        var failed = 0
        val mihonFs = if (options.localFr || options.mihon) fs() else null

        if (options.localFr) {
            val localFr = db.localFrChapterDao().observeAll().first()
            for (row in eligible) {
                val flat = row.flatPath
                val loose = if (row.downloaded == null) loosePath(flat) else null
                val matches = localFr.filter { fr ->
                    val path = fr.mihonPath ?: return@filter false
                    path == flat || (loose != null && loosePath(path) == loose)
                }
                for (fr in matches) {
                    if (deleteDocument(mihonFs, fr.documentUri)) {
                        db.localFrChapterDao().delete(fr)
                        localFrDeleted++
                    } else {
                        failed++
                    }
                }
            }
        }

        if (options.mihon) {
            for (row in eligible) {
                val downloaded = row.downloaded ?: continue
                if (deleteDocument(mihonFs, downloaded.documentUri)) {
                    db.downloadedChapterDao().delete(downloaded)
                    mihonDeleted++
                } else {
                    failed++
                }
            }
        }

        val cmdId = if (pcPaths.isNotEmpty()) settingsSync.enqueueDelete(pcPaths) else null
        return DeleteOutcome(
            chapters = eligible.size,
            skippedActive = skipped.size,
            localFrDeleted = localFrDeleted,
            mihonDeleted = mihonDeleted,
            failed = failed,
            pcChapters = if (cmdId != null) pcPaths.size else 0,
            pcUnavailable = pcPaths.isNotEmpty() && cmdId == null,
        )
    }

    // SAF : un echec (fichier deja absent, arbre revoque) n'arrete pas les autres suppressions.
    private suspend fun deleteDocument(mihonFs: MihonFs?, docId: String): Boolean {
        if (mihonFs == null) return false
        return try {
            withContext(Dispatchers.IO) { mihonFs.delete(docId) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("ChapterDeleter", "suppression SAF impossible : ${e.message}")
            false
        }
    }

    companion object {
        /** Dernier job encore actif : le chapitre n'est pas supprime (feuille : "N ignores"). */
        fun isSkipped(row: ChapterJoinRow): Boolean = row.facts.latestJob?.isActive() == true

        /**
         * Chemin du chapitre tel que le PC le connait (pc_index_chapter), null s'il n'y est pas.
         * Pour un chapitre non telecharge, row.mihonPath est reconstruit et peut differer du chemin PC
         * (suffixe de langue de la source) : on repart alors du chemin aplati de l'index.
         */
        internal fun pcPathOf(row: ChapterJoinRow): MihonPath? {
            val index = row.facts.index ?: return null
            if (!index.original && !index.fr) return null
            if (index.mihonPath == row.flatPath) return row.mihonPath
            val parts = index.mihonPath.split('/', limit = 3)
            return if (parts.size == 3) MihonPath(parts[0], parts[1], parts[2]) else null
        }
    }
}
