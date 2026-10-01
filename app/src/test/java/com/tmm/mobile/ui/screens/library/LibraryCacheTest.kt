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
