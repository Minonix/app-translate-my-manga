package com.tmm.mobile.ui.screens.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.mock.MockSeries
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// L'instantane disque est affiche avant le calcul Room, puis remplace par lui (et reecrit).
@RunWith(RobolectricTestRunner::class)
class LibraryCacheTest {
    // Frontier : le chapitre 0 est le volume 13 J-Novel, sa premiere page n'est pas la couverture de la serie.
    @Test fun coverFallback_prefersSmallestPositiveNumber() {
        fun row(number: Float?) = com.tmm.mobile.data.db.DownloadedChapterEntity(
            mihonPath = "S/F/c$number", sourceDir = "S", seriesDir = "F", chapterDir = "c$number", documentUri = "doc-$number",
            isCbz = true, sizeBytes = 1, lastModified = 0, pageCount = 1, seriesKey = "k", chapterKey = null, number = number,
        )

        assertEquals(1f, coverFallbackChapter(listOf(row(0f), row(-1f), row(2f), row(1f), row(null)))?.number)
        assertEquals(null, coverFallbackChapter(listOf(row(0f), row(null)))?.number)
        assertEquals(0f, coverFallbackChapter(listOf(row(0f)))?.number)
    }

    @Test fun snapshotShownFirst_thenReplacedByRoomAndRewritten() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TmmDatabase::class.java).build()
        val file = File(kotlin.io.path.createTempDirectory().toFile(), "library_snapshot.json")
        val cached = LibrarySnapshot(
            categories = listOf("Tous" to 1),
            series = listOf(MockSeries("Blue Lock", "", "", 1, 2, 0, 0, "À jour", StatusTone.Ok, seriesKey = "bl")),
        )
        file.writeText(ProtocolJson.encodeToString(LibrarySnapshot.serializer(), cached))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = LibraryCache(db, scope, file)

        val seen = mutableListOf<LibrarySnapshot>()
        // Room est vide : la valeur fraiche (0 serie) suit l'instantane (1 serie).
        cache.data.first { seen += it; it.series.isEmpty() && seen.any { s -> s == cached } }
        assertEquals(LibrarySnapshot(categories = listOf("Tous" to 0)), ProtocolJson.decodeFromString(LibrarySnapshot.serializer(), file.readText()))

        scope.cancel()
        db.close()
    }
}
