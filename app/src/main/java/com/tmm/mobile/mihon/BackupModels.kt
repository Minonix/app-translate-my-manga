package com.tmm.mobile.mihon

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// Champs reproduits depuis mihonapp/mihon (Apache 2.0), commit 532575e29e196ed59a5457fd4b4c6af5e0740287 :
// data/backup/models/Backup.kt, BackupManga.kt, BackupChapter.kt, BackupCategory.kt, BackupSource.kt.
// ponytail: seuls les champs utiles a TMM Mobile sont repris (pas de tracking/history/preferences).

@Serializable
data class MihonBackup(
    @ProtoNumber(1) val backupManga: List<MihonBackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<MihonBackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<MihonBackupSource> = emptyList(),
)

@Serializable
data class MihonBackupManga(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val dateAdded: Long = 0,
    @ProtoNumber(16) val chapters: List<MihonBackupChapter> = emptyList(),
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
)

@Serializable
data class MihonBackupChapter(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(7) val dateFetch: Long = 0,
    @ProtoNumber(8) val dateUpload: Long = 0,
    @ProtoNumber(9) val chapterNumber: Float = 0f,
)

@Serializable
data class MihonBackupCategory(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val order: Long = 0,
)

@Serializable
data class MihonBackupSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long,
)
