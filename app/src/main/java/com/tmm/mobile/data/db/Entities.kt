package com.tmm.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tmm.mobile.protocol.QualityAlert

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val seriesKey: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val author: String?,
    val status: Int,
    val thumbnailUrl: String?,
    val sourceName: String,
    val dateAdded: Long,
    val mangaType: String?,
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val order: Long,
    val name: String,
)

@Entity(
    tableName = "series_category",
    primaryKeys = ["seriesKey", "categoryOrder"],
    indices = [Index("categoryOrder")],
)
data class SeriesCategoryEntity(
    val seriesKey: String,
    val categoryOrder: Long,
)

@Entity(tableName = "chapter", indices = [Index("seriesKey")])
data class ChapterEntity(
    @PrimaryKey val chapterKey: String,
    val seriesKey: String,
    val url: String,
    val name: String,
    val number: Float,
    val scanlator: String?,
    val read: Boolean,
    val dateUpload: Long,
)

@Entity(tableName = "downloaded_chapter", indices = [Index("seriesKey"), Index("chapterKey")])
data class DownloadedChapterEntity(
    @PrimaryKey val mihonPath: String,
    val sourceDir: String,
    val seriesDir: String,
    val chapterDir: String,
    val documentUri: String,
    val isCbz: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val pageCount: Int,
    val seriesKey: String?,
    val chapterKey: String?,
    val number: Float?,
)

@Entity(tableName = "scanned_series_dir", primaryKeys = ["sourceDir", "seriesDir"])
data class ScannedSeriesDirEntity(
    val sourceDir: String,
    val seriesDir: String,
    val lastModified: Long,
)

@Entity(tableName = "local_fr_chapter", indices = [Index("mihonPath"), Index("jobId")])
data class LocalFrChapterEntity(
    @PrimaryKey val localPath: String,
    val folderName: String,
    val fileName: String,
    val documentUri: String,
    val lastModified: Long,
    val jobId: String?,
    val mihonPath: String?,
)

@Entity(tableName = "job", indices = [Index("mihonPath"), Index("phoneState")])
data class JobEntity(
    @PrimaryKey val jobId: String,
    val mode: String,
    val pages: List<Int>?,
    val translate: Boolean,
    val mihonPath: String,
    val sourceDir: String,
    val seriesDir: String,
    val chapterDir: String,
    val seriesKey: String?,
    val chapterKey: String?,
    val phoneState: String,
    val failedStep: String?,
    val errorMessage: String?,
    val driveFolderId: String?,
    val uploadSessionUrl: String?,
    val cachePath: String?,
    val sha256: String?,
    val sizeBytes: Long?,
    val transferredBytes: Long,
    val transferTotal: Long,
    val pcState: String?,
    val pcStage: String?,
    val pcPage: Int?,
    val pcPages: Int?,
    val pcEngine: String?,
    val pcQueuePosition: Int?,
    val pcErrorCode: String?,
    val pcErrorMessage: String?,
    val pcErrorPage: Int?,
    val statusUpdatedAt: String?,
    val resultJson: String?,
    val resultFileId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val finishedAt: String?,
    val installedAt: Long?,
    val installedPath: String?,
    val receivedWrittenAt: Long?,
    val cancelWrittenAt: Long?,
    val driveDeletedAt: Long?,
    // Canal du job, fige a l'envoi : "drive" ou "direct" (Tailscale). Il ne change plus jusqu'au resultat.
    @ColumnInfo(defaultValue = CHANNEL_DRIVE) val channel: String = CHANNEL_DRIVE,
    // Code d'erreur local au telephone (docs/ERREURS_PROTOCOLE.md 1.2), jamais ecrit sur Drive ; null si
    // l'echec vient du PC (pcErrorCode) ou si le job n'a pas echoue.
    val errorCode: String? = null,
    // Mode translate sans fichier, options.redeliver (docs/ERREURS_PROTOCOLE.md 2) : redemande au PC la
    // traduction qu'il a deja (resultat perdu ou illisible).
    @ColumnInfo(defaultValue = "0") val redeliver: Boolean = false,
)

const val CHANNEL_DRIVE = "drive"
const val CHANNEL_DIRECT = "direct"

@Entity(tableName = "chapter_quality")
data class ChapterQualityEntity(
    @PrimaryKey val mihonPath: String,
    val totalPages: Int,
    val translatedBubbles: Int,
    val untranslatedBubbles: Int,
    val sfxKept: Int,
    val verifiedAt: String?,
    val alerts: List<QualityAlert>,
    val jobId: String,
)

@Entity(tableName = "series_settings")
data class SeriesSettingsEntity(
    @PrimaryKey val seriesKey: String,
    val autoTranslate: Boolean,
    val targetLang: String = "FR",
)

@Entity(tableName = "ignored_gap", primaryKeys = ["seriesKey", "number"])
data class IgnoredGapEntity(
    val seriesKey: String,
    val number: Int,
)

@Entity(tableName = "backup_import")
data class BackupImportEntity(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val importedAt: Long,
    val source: String,
)

@Entity(tableName = "pc_index_chapter")
data class PcIndexChapterEntity(
    @PrimaryKey val mihonPath: String,
    val original: Boolean,
    val fr: Boolean,
)
