package com.tmm.mobile

import com.tmm.mobile.workers.WorkerTestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Actualiser (Accueil) relance Startup.run() sans lire son retour : le resultat doit etre publie sur
// Startup.results pour que TmmRoot renvoie vers l'ecran Permission quand l'acces Mihon a ete retire.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StartupResultsTest {
    private val env = WorkerTestEnvironment()

    @Before
    fun setUp() = env.setUp()

    @After
    fun tearDown() = env.tearDown()

    @Test
    fun run_withoutPersistedTreePermission_publishesPermissionNeeded() = runTest {
        env.prefs.setOnboardingDone(true)
        env.prefs.setMihonTreeUri("content://com.android.externalstorage.documents/tree/primary%3AMihon")
        val startup = env.container.startup
        val published = async(UnconfinedTestDispatcher(testScheduler)) { startup.results.first() }

        val returned = startup.run()

        assertEquals(StartupResult.PERMISSION_NEEDED, returned)
        assertEquals(StartupResult.PERMISSION_NEEDED, published.await())
    }
}
