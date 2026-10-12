package com.tmm.mobile.ui.screens.settings

import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.db.BackupImportEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.mihon.BackupImporter
import com.tmm.mobile.mihon.BackupReader
import com.tmm.mobile.mihon.MihonFs

/** Etapes affichees pendant « Relire la sauvegarde » (Reglages). */
sealed interface BackupImportStep {
    data object Idle : BackupImportStep
    data object ReadingFile : BackupImportStep
    data object Decompressing : BackupImportStep
    data object ReadingSeries : BackupImportStep
    data object ReadingCategories : BackupImportStep
    data object Comparing : BackupImportStep

    val progressFraction: Float
        get() = when (this) {
            Idle -> 0f
            ReadingFile -> 0.2f
            Decompressing -> 0.4f
            ReadingSeries -> 0.6f
            ReadingCategories -> 0.8f
            Comparing -> 1.0f
        }
}

/**
 * Rejeu pur/testable de Startup.importBackupIfNeeded (sans le court-circuit "inchange") : lit la
 * derniere sauvegarde .tachibk de Mihon/autobackup, l'importe dans Room et met a jour backup_import.
 * `onStep` est appele dans l'ordre des 5 etapes affichees par les Reglages ; ne fait rien si
 * le dossier autobackup ou un fichier .tachibk est introuvable.
 */
internal suspend fun importReimportSequence(
    fs: MihonFs,
    rootDocId: String,
    db: TmmDatabase,
    clock: Clock,
    onStep: suspend (BackupImportStep) -> Unit,
) {
    val autobackupId = fs.findChild(rootDocId, "autobackup")?.docId ?: return
    val newest = fs.children(autobackupId)
        .filter { !it.isDirectory && it.name.endsWith(".tachibk") }
        .maxByOrNull { it.lastModified }
        ?: return

    onStep(BackupImportStep.ReadingFile)
    val backup = fs.openInput(newest.docId).use { BackupReader.read(it) }

    onStep(BackupImportStep.Decompressing)

    onStep(BackupImportStep.ReadingSeries)
    BackupImporter(db).import(backup)

    onStep(BackupImportStep.ReadingCategories)

    onStep(BackupImportStep.Comparing)
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
