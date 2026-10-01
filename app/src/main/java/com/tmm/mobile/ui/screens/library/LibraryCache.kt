package com.tmm.mobile.ui.screens.library

import com.tmm.mobile.core.AppLog
import com.tmm.mobile.data.db.CategoryEntity
import com.tmm.mobile.data.db.SeriesCategoryEntity
import com.tmm.mobile.data.db.SeriesEntity
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.status.ChapterJoinIndex
import com.tmm.mobile.data.status.ChapterJoinInputs
import com.tmm.mobile.data.status.SeriesBadge
import com.tmm.mobile.data.status.SeriesSummary
import com.tmm.mobile.data.status.badge
import com.tmm.mobile.data.status.buildChapterJoinRows
import com.tmm.mobile.data.status.deriveSeriesSummary
import com.tmm.mobile.data.status.joinInputsFlow
import com.tmm.mobile.data.status.toAggregateInputs
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.mock.MockSeries
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

/** Ce que la Bibliotheque affiche : puces de categories (avec compte) et liste des series. */
@Serializable
data class LibrarySnapshot(
    val categories: List<Pair<String, Int>> = listOf("Tous" to 0),
    val series: List<MockSeries> = emptyList(),
)

/**
 * Cache de la Bibliotheque, partage par tous les LibraryViewModel (AppContainer). Avec ~2000 series,
 * recharger et rejoindre tous les chapitres Room prend plusieurs secondes ; la liste change peu d'un
 * jour a l'autre, donc :
 * - la derniere liste calculee reste en memoire : revenir sur l'onglet est instantane ;
 * - elle est ecrite sur disque et relue au premier abonnement : au lancement, la liste s'affiche tout
 *   de suite, puis est remplacee par le calcul frais depuis Room (les suppressions/ajouts y arrivent
 *   via le scan Mihon, rien n'est fige).
 * Le calcul ne tourne que tant qu'un ecran observe (+5 s) : pas de recalcul a chaque progression de
 * job quand la Bibliotheque n'est pas affichee, et les ~100k chapitres ne restent pas en memoire.
 */
class LibraryCache(private val db: TmmDatabase, scope: CoroutineScope, private val file: File) {
    private var diskRead = false
    private var lastWritten: LibrarySnapshot? = null

    private val live = combine(
        db.seriesDao().observeAll(),
        db.categoryDao().observeAll(),
        db.seriesCategoryDao().observeAll(),
        joinInputsFlow(db),
    ) { series, categories, links, joinInputs -> buildLibrarySnapshot(series, categories, links, joinInputs) }
        .flowOn(Dispatchers.Default)

    val data: StateFlow<LibrarySnapshot> = flow {
        if (!diskRead) {
            diskRead = true
            read()?.let { lastWritten = it; emit(it) }
        }
        emitAll(live.onEach(::write))
    }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LibrarySnapshot())

    private fun read(): LibrarySnapshot? = try {
        if (file.exists()) ProtocolJson.decodeFromString(LibrarySnapshot.serializer(), file.readText()) else null
    } catch (e: Exception) {
        // Format d'une ancienne version ou fichier tronque : on repart du calcul Room.
        AppLog.d("LibraryCache", "instantane illisible : ${e.javaClass.simpleName}")
        null
    }

    private fun write(snapshot: LibrarySnapshot) {
        if (snapshot == lastWritten) return
        lastWritten = snapshot
        try {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(ProtocolJson.encodeToString(LibrarySnapshot.serializer(), snapshot))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            AppLog.d("LibraryCache", "ecriture instantane impossible : ${e.javaClass.simpleName}")
        }
    }
}

internal fun buildLibrarySnapshot(
    seriesList: List<SeriesEntity>,
    categories: List<CategoryEntity>,
    links: List<SeriesCategoryEntity>,
    joinInputs: ChapterJoinInputs,
): LibrarySnapshot {
    val linksBySeries = links.groupBy { it.seriesKey }
    val categoryNameByOrder = categories.associate { it.order to it.name }
    val countByCategory = links.groupingBy { it.categoryOrder }.eachCount()
    val chips = listOf("Tous" to seriesList.size) + categories.map { cat -> cat.name to (countByCategory[cat.order] ?: 0) }
    val index = ChapterJoinIndex(joinInputs)
    val mockSeries = seriesList.map { series ->
        val rows = buildChapterJoinRows(series.seriesKey, series.sourceName, series.title, index)
        val summary = deriveSeriesSummary(series.seriesKey, rows.toAggregateInputs(), emptySet())
        val badge = summary.badge()
        val primaryOrder = linksBySeries[series.seriesKey]?.minOfOrNull { it.categoryOrder }
        val categoryName = primaryOrder?.let { categoryNameByOrder[it] } ?: ""
        val totalChapters = index.chaptersBySeries[series.seriesKey]?.size ?: 0
        MockSeries(
            title = series.title,
            colorHex = "",
            category = categoryName,
            frChapters = summary.frCount,
            totalChapters = totalChapters,
            toSend = summary.toSend,
            toGet = summary.toFetch,
            badge = libraryBadgeLabel(badge, summary),
            tone = badge.toStatusTone(),
            seriesKey = series.seriesKey,
            thumbnailUrl = series.thumbnailUrl,
        )
    }
    return LibrarySnapshot(categories = chips, series = mockSeries)
}

private fun libraryBadgeLabel(badge: SeriesBadge, s: SeriesSummary): String = when (badge) {
    SeriesBadge.BUSY -> "Traduction en cours"
    SeriesBadge.SEND -> "${s.toSend} à envoyer"
    SeriesBadge.GET -> "${s.toFetch} à récupérer"
    SeriesBadge.ABSENT -> "Non téléchargée"
    SeriesBadge.OK -> "À jour"
}

private fun SeriesBadge.toStatusTone(): StatusTone = when (this) {
    SeriesBadge.BUSY -> StatusTone.Busy
    SeriesBadge.SEND -> StatusTone.Send
    SeriesBadge.GET -> StatusTone.Get
    SeriesBadge.ABSENT -> StatusTone.Absent
    SeriesBadge.OK -> StatusTone.Ok
}
