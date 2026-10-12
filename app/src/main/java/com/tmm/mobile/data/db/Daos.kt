package com.tmm.mobile.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series")
    fun observeAll(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE seriesKey = :seriesKey")
    fun observe(seriesKey: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE seriesKey = :seriesKey")
    suspend fun get(seriesKey: String): SeriesEntity?

    @Upsert
    suspend fun upsert(series: SeriesEntity)

    @Upsert
    suspend fun upsertAll(series: List<SeriesEntity>)

    @Delete
    suspend fun delete(series: SeriesEntity)

    @Query("DELETE FROM series")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY `order`")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM category")
    suspend fun clear()
}

@Dao
interface SeriesCategoryDao {
    @Query("SELECT * FROM series_category")
    fun observeAll(): Flow<List<SeriesCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<SeriesCategoryEntity>)

    @Query("DELETE FROM series_category WHERE seriesKey = :seriesKey")
    suspend fun deleteForSeries(seriesKey: String)

    @Query("DELETE FROM series_category")
    suspend fun clear()
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapter WHERE seriesKey = :seriesKey ORDER BY number")
    fun observeForSeries(seriesKey: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter")
    fun observeAll(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter WHERE chapterKey = :chapterKey")
    suspend fun get(chapterKey: String): ChapterEntity?

    @Query("SELECT * FROM chapter WHERE seriesKey = :seriesKey")
    suspend fun getForSeries(seriesKey: String): List<ChapterEntity>

    @Upsert
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Delete
    suspend fun delete(chapter: ChapterEntity)

    @Query("DELETE FROM chapter")
    suspend fun clear()
}

@Dao
interface DownloadedChapterDao {
    @Query("SELECT * FROM downloaded_chapter WHERE seriesKey = :seriesKey")
    fun observeForSeries(seriesKey: String): Flow<List<DownloadedChapterEntity>>

    @Query("SELECT * FROM downloaded_chapter")
    fun observeAll(): Flow<List<DownloadedChapterEntity>>

    @Query("SELECT * FROM downloaded_chapter WHERE mihonPath = :mihonPath")
    suspend fun get(mihonPath: String): DownloadedChapterEntity?

    @Upsert
    suspend fun upsert(chapter: DownloadedChapterEntity)

    @Upsert
    suspend fun upsertAll(chapters: List<DownloadedChapterEntity>)

    @Delete
    suspend fun delete(chapter: DownloadedChapterEntity)

    @Query("DELETE FROM downloaded_chapter WHERE sourceDir = :sourceDir AND seriesDir = :seriesDir")
    suspend fun deleteForSeriesDir(sourceDir: String, seriesDir: String)

    @Query("DELETE FROM downloaded_chapter WHERE pageCount = 0")
    suspend fun deleteWithoutPages()
}

@Dao
interface ScannedSeriesDirDao {
    @Query("SELECT * FROM scanned_series_dir")
    fun observeAll(): Flow<List<ScannedSeriesDirEntity>>

    @Query("SELECT * FROM scanned_series_dir WHERE sourceDir = :sourceDir AND seriesDir = :seriesDir")
    suspend fun get(sourceDir: String, seriesDir: String): ScannedSeriesDirEntity?

    @Upsert
    suspend fun upsert(dir: ScannedSeriesDirEntity)

    @Upsert
    suspend fun upsertAll(dirs: List<ScannedSeriesDirEntity>)

    @Query("SELECT * FROM scanned_series_dir WHERE sourceDir = :sourceDir")
    suspend fun getForSource(sourceDir: String): List<ScannedSeriesDirEntity>

    @Query("DELETE FROM scanned_series_dir WHERE sourceDir = :sourceDir AND seriesDir = :seriesDir")
    suspend fun delete(sourceDir: String, seriesDir: String)
}

@Dao
interface LocalFrChapterDao {
    @Query("SELECT * FROM local_fr_chapter")
    fun observeAll(): Flow<List<LocalFrChapterEntity>>

    @Query("SELECT * FROM local_fr_chapter WHERE localPath = :localPath")
    suspend fun get(localPath: String): LocalFrChapterEntity?

    @Query("SELECT * FROM local_fr_chapter WHERE folderName = :folderName")
    suspend fun getForFolder(folderName: String): List<LocalFrChapterEntity>

    @Upsert
    suspend fun upsertAll(chapters: List<LocalFrChapterEntity>)

    @Upsert
    suspend fun upsert(chapter: LocalFrChapterEntity)

    @Delete
    suspend fun delete(chapter: LocalFrChapterEntity)
}

@Dao
interface JobDao {
    // Lecture en transaction : au-dela de ~600 jobs la table depasse une fenetre de curseur (2 Mo) ;
    // sans transaction, une ecriture entre deux fenetres faisait planter la lecture
    // ("Couldn't read row N from CursorWindow").
    @Transaction
    @Query("SELECT * FROM job")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM job WHERE mihonPath = :mihonPath ORDER BY createdAt DESC")
    fun observeForMihonPath(mihonPath: String): Flow<List<JobEntity>>

    @Query("SELECT * FROM job WHERE mihonPath = :mihonPath ORDER BY createdAt DESC")
    suspend fun getAllForMihonPath(mihonPath: String): List<JobEntity>

    @Query("SELECT * FROM job WHERE jobId IN (:jobIds)")
    suspend fun getAll(jobIds: List<String>): List<JobEntity>

    @Query("SELECT * FROM job WHERE jobId = :jobId")
    suspend fun get(jobId: String): JobEntity?

    @Upsert
    suspend fun upsert(job: JobEntity)

    @Delete
    suspend fun delete(job: JobEntity)

    @Query("SELECT jobId FROM job WHERE phoneState NOT IN (:states)")
    suspend fun jobIdsNotIn(states: List<String>): List<String>

    @Query("DELETE FROM job WHERE phoneState NOT IN (:states)")
    suspend fun deleteNotIn(states: List<String>): Int
}

@Dao
interface ChapterQualityDao {
    @Query("SELECT * FROM chapter_quality WHERE mihonPath = :mihonPath")
    fun observe(mihonPath: String): Flow<ChapterQualityEntity?>

    // Ajoute pour la Fiche serie (E10) : statistiques par chapitre affichees en une fois pour
    // toute une serie, sur le meme modele que les autres observeAll() de ce fichier.
    @Query("SELECT * FROM chapter_quality")
    fun observeAll(): Flow<List<ChapterQualityEntity>>

    @Upsert
    suspend fun upsert(quality: ChapterQualityEntity)
}

@Dao
interface SeriesSettingsDao {
    @Query("SELECT * FROM series_settings WHERE seriesKey = :seriesKey")
    fun observe(seriesKey: String): Flow<SeriesSettingsEntity?>

    @Query("SELECT seriesKey FROM series_settings WHERE autoTranslate = 1")
    suspend fun autoTranslateSeriesKeys(): List<String>

    @Query("SELECT seriesKey FROM series_settings WHERE autoTranslate = 1")
    fun observeAutoTranslateSeriesKeys(): Flow<List<String>>

    @Query("UPDATE series_settings SET autoTranslate = 0")
    suspend fun clearAutoTranslate()

    @Upsert
    suspend fun upsert(settings: SeriesSettingsEntity)
}

@Dao
interface IgnoredGapDao {
    @Query("SELECT * FROM ignored_gap WHERE seriesKey = :seriesKey")
    fun observeForSeries(seriesKey: String): Flow<List<IgnoredGapEntity>>

    @Upsert
    suspend fun upsert(gap: IgnoredGapEntity)

    @Delete
    suspend fun delete(gap: IgnoredGapEntity)
}

@Dao
interface BackupImportDao {
    @Query("SELECT * FROM backup_import WHERE id = 0")
    fun observe(): Flow<BackupImportEntity?>

    @Upsert
    suspend fun upsert(backupImport: BackupImportEntity)
}

@Dao
interface PcIndexChapterDao {
    @Query("SELECT * FROM pc_index_chapter")
    fun observeAll(): Flow<List<PcIndexChapterEntity>>

    @Query("SELECT * FROM pc_index_chapter WHERE mihonPath = :mihonPath")
    suspend fun get(mihonPath: String): PcIndexChapterEntity?

    @Query("DELETE FROM pc_index_chapter")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<PcIndexChapterEntity>)

    @Transaction
    suspend fun replaceAll(chapters: List<PcIndexChapterEntity>) {
        clear()
        insertAll(chapters)
    }
}
