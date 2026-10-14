package com.tmm.mobile.mihon

import androidx.room.withTransaction
import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.ChapterEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.TmmDatabase

// CAHIER_DES_CHARGES.md 8.3 : le cache Room est remplace en une seule transaction (pas d'etat a
// moitie importe). BackupManga.categories contient les `order` de BackupCategory (pas des ids,
// verifie dans MangaRestorer.kt de Mihon) ; comme CategoryEntity.order EST deja la cle primaire
// locale, aucune indirection par nom n'est necessaire (contrairement a Mihon lui-meme).
class BackupImporter(private val db: TmmDatabase) {

    companion object {
        // Id fixe de la « Source locale » de Mihon (LocalSource.ID). Ses series sont les dossiers de
        // local/, ou TMM installe les traductions : mises en bibliotheque, elles reapparaissaient dans
        // TMM comme une serie a part, doublon de l'originale. Elles ne sont jamais importees.
        const val MIHON_LOCAL_SOURCE_ID = 0L
    }

    suspend fun import(backup: MihonBackup) {
        val sourceNameById = backup.backupSources.associate { it.sourceId to it.name }

        db.withTransaction {
            db.seriesCategoryDao().clear()
            db.chapterDao().clear()
            db.categoryDao().clear()
            db.seriesDao().clear()

            val categories = backup.backupCategories.map { CategoryEntity(order = it.order, name = it.name) }
            db.categoryDao().upsertAll(categories)

            val series = mutableListOf<SeriesEntity>()
            val links = mutableListOf<SeriesCategoryEntity>()
            val chapters = mutableListOf<ChapterEntity>()

            for (manga in backup.backupManga) {
                if (manga.source == MIHON_LOCAL_SOURCE_ID) continue
                val seriesKey = "${manga.source}|${manga.url}"
                series.add(
                    SeriesEntity(
                        seriesKey = seriesKey,
                        sourceId = manga.source.toString(),
                        url = manga.url,
                        title = manga.title,
                        author = manga.author,
                        status = manga.status,
                        thumbnailUrl = manga.thumbnailUrl,
                        sourceName = sourceNameById[manga.source] ?: "",
                        dateAdded = manga.dateAdded,
                        mangaType = null,
                    ),
                )
                for (order in manga.categories) {
                    links.add(SeriesCategoryEntity(seriesKey = seriesKey, categoryOrder = order))
                }
                for (chapter in manga.chapters) {
                    val chapterKey = "$seriesKey|${chapter.url}"
                    chapters.add(
                        ChapterEntity(
                            chapterKey = chapterKey,
                            seriesKey = seriesKey,
                            url = chapter.url,
                            name = chapter.name,
                            number = chapter.chapterNumber,
                            scanlator = chapter.scanlator,
                            read = chapter.read,
                            dateUpload = chapter.dateUpload,
                        ),
                    )
                }
            }

            db.seriesDao().upsertAll(series)
            db.seriesCategoryDao().insertAll(links)
            db.chapterDao().upsertAll(chapters)
        }
    }
}
