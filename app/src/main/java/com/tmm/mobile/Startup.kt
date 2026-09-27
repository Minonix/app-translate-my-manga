package com.tmm.mobile

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.data.JobRepository
import com.tmm.mobile.data.RefreshCoordinator
import com.tmm.mobile.data.db.BackupImportEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.runAutoTranslate
import com.tmm.mobile.mihon.BackupImporter
import com.tmm.mobile.mihon.BackupReader
import com.tmm.mobile.mihon.DownloadScanner
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonTree
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class StartupResult { ONBOARDING, PERMISSION_NEEDED, DONE }

// PLAN_GEMINI.md 5.16. TmmRoot (E10) appelle run() a chaque ON_START ; le Mutex garantit une seule
// execution a la fois si l'activite repasse ON_START pendant qu'une sequence tourne deja.
class Startup(
    private val context: Context,
    private val db: TmmDatabase,
    private val prefs: AppPrefs,
    private val jobRepository: JobRepository,
    private val refreshCoordinator: RefreshCoordinator,
) {
    private val mutex = Mutex()

    suspend fun run(): StartupResult = mutex.withLock {
        if (!prefs.onboardingDone.first()) return@withLock StartupResult.ONBOARDING

        val treeUriString = prefs.mihonTreeUri.first() ?: return@withLock StartupResult.PERMISSION_NEEDED
        val treeUri = Uri.parse(treeUriString)
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        if (!hasPermission) return@withLock StartupResult.PERMISSION_NEEDED

        val fs: MihonFs = MihonTree(context.contentResolver, treeUri)
        val rootDocId = try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            fs.children(docId)
            docId
        } catch (e: Exception) {
            return@withLock StartupResult.PERMISSION_NEEDED
        }

        importBackupIfNeeded(fs, rootDocId)
        scanDownloads(fs, rootDocId)
        runAutoTranslate(db, jobRepository)
        refreshCoordinator.refresh()
        StartupResult.DONE
    }

    private suspend fun importBackupIfNeeded(fs: MihonFs, rootDocId: String) {
        val autobackupId = fs.findChild(rootDocId, "autobackup")?.docId ?: return
        val newest = fs.children(autobackupId)
            .filter { !it.isDirectory && it.name.endsWith(".tachibk") }
            .maxByOrNull { it.lastModified }
            ?: return
        val known = db.backupImportDao().observe().first()
        val changed = known == null || known.name != newest.name || known.size != newest.size ||
            known.lastModified != newest.lastModified
        if (!changed) return

        val backup = fs.openInput(newest.docId).use { BackupReader.read(it) }
        BackupImporter(db).import(backup)
        db.backupImportDao().upsert(
            BackupImportEntity(
                name = newest.name,
                size = newest.size,
                lastModified = newest.lastModified,
                importedAt = System.currentTimeMillis(),
                source = "autobackup",
            ),
        )
    }

    private suspend fun scanDownloads(fs: MihonFs, rootDocId: String) {
        val scanner = DownloadScanner(fs, db)
        fs.findChild(rootDocId, "downloads")?.let { scanner.scanDownloads(it.docId) }
        fs.findChild(rootDocId, "local")?.let { scanner.scanLocal(it.docId) }
        prefs.setLastScanAt(System.currentTimeMillis())
    }
}
