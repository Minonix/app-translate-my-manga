package com.tmm.mobile.ui.screens.catalog

import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.db.BackupImportEntity
import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.DownloadedChapterEntity
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.data.db.LocalFrChapterEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.mihon.BackupImporter
import com.tmm.mobile.mihon.BackupReader
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonTree
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * ViewModel reel de l'ecran Catalogue (E10, Catalog.dc.html, plan 7.6).
 * L'etat derive en continu de Room (backup_import, series, category, series_category,
 * downloaded_chapter, local_fr_chapter, job) ; "Relire la dernière sauvegarde" rejoue le meme
 * import que Startup.importBackupIfNeeded (sans le court-circuit "inchange", puisque c'est ici
 * un geste explicite de l'utilisateur), avec la progression a 5 etapes de la maquette.
 *
 * PLAN_GEMINI.md 5.21 : aucun bouton "Importer" manuel n'existe en V1 - seul cet evenement
 * Reimport (rejeu de la derniere sauvegarde autobackup) est expose.
 */
class CatalogViewModel(private val container: AppContainer) : ViewModel() {
    private val db: TmmDatabase get() = container.db
    private val importStep = MutableStateFlow<CatalogImportStep>(CatalogImportStep.Idle)
    private val reimportMutex = Mutex()

    val state = combine(
        combine(
            db.backupImportDao().observe(),
            db.seriesDao().observeAll(),
            db.categoryDao().observeAll(),
            db.seriesCategoryDao().observeAll(),
        ) { backupImport, series, categories, links -> CatalogBaseData(backupImport, series, categories, links) },
        combine(
            db.downloadedChapterDao().observeAll(),
            db.localFrChapterDao().observeAll(),
            db.jobDao().observeAll(),
        ) { downloaded, localFr, jobs -> CatalogPresenceData(downloaded, localFr, jobs) },
        importStep,
    ) { base, presence, step ->
        buildCatalogState(base, presence, step, container.clock.nowMillis())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogState())

    fun onEvent(event: CatalogEvent) {
        when (event) {
            CatalogEvent.Reimport -> reimport()
            is CatalogEvent.SetImportStep -> importStep.value = event.step
        }
    }

    private fun reimport() {
        if (importStep.value != CatalogImportStep.Idle) return
        viewModelScope.launch {
            if (!reimportMutex.tryLock()) return@launch
            try {
                runReimport()
            } catch (e: Exception) {
                // ponytail: pas d'etat d'erreur dedie dans la maquette pour ce bouton (ni permission
                // perdue, ni fichier absent) - on abandonne silencieusement et on revient a Idle ;
                // un vrai diagnostic passe par l'ecran Permission (Startup, deja gere ailleurs).
                importStep.value = CatalogImportStep.Idle
            } finally {
                reimportMutex.unlock()
            }
        }
    }

    private suspend fun runReimport() {
        val treeUriString = container.prefs.mihonTreeUri.first() ?: return
        val treeUri = Uri.parse(treeUriString)
        val fs: MihonFs = MihonTree(container.context.contentResolver, treeUri)
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        importReimportSequence(fs, rootDocId, db, container.clock) { step ->
            importStep.value = step
            delay(STEP_DELAY_MS)
        }
        importStep.value = CatalogImportStep.Idle
    }

    private companion object {
        const val STEP_DELAY_MS = 450L
    }
}

private data class CatalogBaseData(
    val backupImport: BackupImportEntity?,
    val series: List<SeriesEntity>,
    val categories: List<CategoryEntity>,
    val links: List<SeriesCategoryEntity>,
)

private data class CatalogPresenceData(
    val downloaded: List<DownloadedChapterEntity>,
    val localFr: List<LocalFrChapterEntity>,
    val jobs: List<JobEntity>,
)

private fun buildCatalogState(
    base: CatalogBaseData,
    presence: CatalogPresenceData,
    step: CatalogImportStep,
    now: Long,
): CatalogState {
    val downloadedByPath = presence.downloaded.associateBy { it.mihonPath }
    val seriesKeyByMihonPath = presence.jobs.associate { it.mihonPath to it.seriesKey }
    // ponytail: le seriesKey d'un chapitre FR est retrouve via le job d'origine, ou a defaut via le
    // chapitre telecharge de meme mihonPath (cas d'une installation hors flux de job normal).
    val localFrSeriesKeys = presence.localFr.mapNotNull { lf ->
        lf.mihonPath?.let { path -> seriesKeyByMihonPath[path] ?: downloadedByPath[path]?.seriesKey }
    }.toSet()
    val hasContentSeriesKeys = presence.downloaded.mapNotNull { it.seriesKey }.toSet() + localFrSeriesKeys
    val absentSeries = base.series.count { it.seriesKey !in hasContentSeriesKeys }

    val linksBySeries = base.links.groupBy { it.seriesKey }
    val countByCategoryOrder = base.links.groupingBy { it.categoryOrder }.eachCount()
    val categories = base.categories.map { cat ->
        CatalogCategory(name = cat.name, count = countByCategoryOrder[cat.order] ?: 0, order = cat.order)
    }
    val uncategorizedCount = base.series.count { (linksBySeries[it.seriesKey] ?: emptyList()).isEmpty() }
    val allCategories = if (uncategorizedCount > 0) {
        categories + CatalogCategory(name = "Sans catégorie", count = uncategorizedCount, order = UNCATEGORIZED_ORDER)
    } else {
        categories
    }
    val maxCount = (allCategories.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

    return CatalogState(
        backupFileName = base.backupImport?.name.orEmpty(),
        importedWhen = base.backupImport?.let { formatImportedWhen(it.importedAt, now) }.orEmpty(),
        totalSeries = base.series.size,
        absentSeries = absentSeries,
        totalCategories = base.categories.size,
        categories = allCategories.map { it.copy(maxCount = maxCount) },
        importStep = step,
    )
}

// PLAN_GEMINI.md 7.6 : "aujourd'hui HH:mm", "hier HH:mm", sinon "le jj/mm".
private fun formatImportedWhen(importedAt: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val imported = Instant.ofEpochMilli(importedAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone)
    val time = "%02d:%02d".format(imported.hour, imported.minute)
    return when (ChronoUnit.DAYS.between(imported.toLocalDate(), today.toLocalDate())) {
        0L -> "aujourd'hui $time"
        1L -> "hier $time"
        else -> "le %02d/%02d".format(imported.dayOfMonth, imported.monthValue)
    }
}

/**
 * Rejeu pur/testable de Startup.importBackupIfNeeded (sans le court-circuit "inchange") : lit la
 * derniere sauvegarde .tachibk de Mihon/autobackup, l'importe dans Room et met a jour backup_import.
 * `onStep` est appele dans l'ordre des 5 etapes de la maquette (Catalog.dc.html) ; ne fait rien si
 * le dossier autobackup ou un fichier .tachibk est introuvable.
 */
internal suspend fun importReimportSequence(
    fs: MihonFs,
    rootDocId: String,
    db: TmmDatabase,
    clock: Clock,
    onStep: suspend (CatalogImportStep) -> Unit,
) {
    val autobackupId = fs.findChild(rootDocId, "autobackup")?.docId ?: return
    val newest = fs.children(autobackupId)
        .filter { !it.isDirectory && it.name.endsWith(".tachibk") }
        .maxByOrNull { it.lastModified }
        ?: return

    onStep(CatalogImportStep.ReadingFile)
    val backup = fs.openInput(newest.docId).use { BackupReader.read(it) }

    onStep(CatalogImportStep.Decompressing)

    onStep(CatalogImportStep.ReadingSeries)
    BackupImporter(db).import(backup)

    onStep(CatalogImportStep.ReadingCategories)

    onStep(CatalogImportStep.Comparing)
    db.backupImportDao().upsert(
        BackupImportEntity(
            name = newest.name,
            size = newest.size,
            lastModified = newest.lastModified,
            importedAt = clock.nowMillis(),
            source = "autobackup",
        ),
    )
}
