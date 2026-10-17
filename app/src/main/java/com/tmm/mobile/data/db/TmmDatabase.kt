package com.tmm.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        ManualChapterLinkEntity::class,
    ],
    version = 4,
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
    abstract fun manualChapterLinkDao(): ManualChapterLinkDao

    companion object {
        // v1 -> v2 : canal du job (connexion directe). Les jobs existants restent sur Drive.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `job` ADD COLUMN `channel` TEXT NOT NULL DEFAULT 'drive'")
            }
        }

        // v2 -> v3 : code d'erreur local du job (docs/ERREURS_PROTOCOLE.md 1.2) et redemande (redeliver, 2).
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `job` ADD COLUMN `errorCode` TEXT")
                db.execSQL("ALTER TABLE `job` ADD COLUMN `redeliver` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v3 -> v4 : liaisons manuelles fichier telecharge -> chapitre (Fiche serie).
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `manual_chapter_link` (`mihonPath` TEXT NOT NULL, `seriesKey` TEXT NOT NULL, " +
                        "`chapterKey` TEXT, `number` REAL NOT NULL, PRIMARY KEY(`mihonPath`))",
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        // R14 : la base ne doit jamais empecher l'app de demarrer. Un retour a une version anterieure de
        // l'app (downgrade) repart d'une base vide ; une migration montante absente reste un bug (teste).
        fun build(context: Context): TmmDatabase =
            Room.databaseBuilder(context, TmmDatabase::class.java, "tmm.db")
                .addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
