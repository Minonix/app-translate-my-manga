package com.tmm.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SeriesEntity::class,
        CategoryEntity::class,
        SeriesCategoryEntity::class,
        ChapterEntity::class,
        DownloadedChapterEntity::class,
        ScannedSeriesDirEntity::class,
        LocalFrChapterEntity::class,
        JobEntity::class,
        ChapterQualityEntity::class,
        SeriesSettingsEntity::class,
        IgnoredGapEntity::class,
        BackupImportEntity::class,
        PcIndexChapterEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TmmDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun categoryDao(): CategoryDao
    abstract fun seriesCategoryDao(): SeriesCategoryDao
    abstract fun chapterDao(): ChapterDao
    abstract fun downloadedChapterDao(): DownloadedChapterDao
    abstract fun scannedSeriesDirDao(): ScannedSeriesDirDao
    abstract fun localFrChapterDao(): LocalFrChapterDao
    abstract fun jobDao(): JobDao
    abstract fun chapterQualityDao(): ChapterQualityDao
    abstract fun seriesSettingsDao(): SeriesSettingsDao
    abstract fun ignoredGapDao(): IgnoredGapDao
    abstract fun backupImportDao(): BackupImportDao
    abstract fun pcIndexChapterDao(): PcIndexChapterDao

    companion object {
        fun build(context: Context): TmmDatabase =
            Room.databaseBuilder(context, TmmDatabase::class.java, "tmm.db").build()
    }
}
