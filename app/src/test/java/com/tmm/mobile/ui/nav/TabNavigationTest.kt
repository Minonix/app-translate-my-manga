package com.tmm.mobile.ui.nav

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Fiche serie ouverte depuis l'Accueil : l'onglet Accueil restait sans effet et la fiche se rouvrait
// en revenant d'Activite ou Reglages (sous-pile rattachee a HomeRoute puis restauree).
@RunWith(RobolectricTestRunner::class)
class TabNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun setUpHost() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = HomeRoute) {
                composable<HomeRoute> { BasicText("home") }
                composable<LibraryRoute> { BasicText("library") }
                composable<QueueRoute> { BasicText("queue") }
                composable<SettingsRoute> { BasicText("settings") }
                composable<SeriesRoute> { BasicText("series") }
            }
        }
    }

    private fun act(block: NavHostController.() -> Unit) = compose.runOnIdle { nav.block() }

    private inline fun <reified T : Any> assertOn() = compose.runOnIdle {
        assertTrue("attendu ${T::class.simpleName}, obtenu ${nav.currentDestination?.route}", nav.currentDestination?.hasRoute<T>() == true)
    }

    @Test
    fun homeTab_fromSeriesOpenedOnHome_returnsHome() {
        setUpHost()
        act { navigate(SeriesRoute("s1")) }
        act { selectTab(0) }
        assertOn<HomeRoute>()
    }

    @Test
    fun homeTab_afterActivity_doesNotReopenSeries() {
        setUpHost()
        act { navigate(SeriesRoute("s1")) }
        act { selectTab(2) }
        assertOn<QueueRoute>()
        act { selectTab(0) }
        assertOn<HomeRoute>()
        act { selectTab(3) }
        act { selectTab(0) }
        assertOn<HomeRoute>()
    }

    @Test
    fun libraryTab_keepsItsSeriesAcrossTabs() {
        setUpHost()
        act { selectTab(1) }
        act { navigate(SeriesRoute("s1")) }
        act { selectTab(0) }
        assertOn<HomeRoute>()
        act { selectTab(1) }
        assertOn<SeriesRoute>()
    }
}
