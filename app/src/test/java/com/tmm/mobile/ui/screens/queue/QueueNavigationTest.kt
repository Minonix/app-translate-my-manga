package com.tmm.mobile.ui.screens.queue

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tmm.mobile.ui.nav.HomeRoute
import com.tmm.mobile.ui.nav.LibraryRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.tmm.mobile.ui.nav.QueueRoute as QueueDestination

// Bouton "+" de la File : la Bibliotheque s'ouvre en mode selection meme si elle etait deja dans
// la pile ou sauvegardee par un changement d'onglet (memes NavOptions que la barre basse de TmmRoot).
@RunWith(RobolectricTestRunner::class)
class QueueNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    private fun setUpNavHost() {
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = HomeRoute) {
                composable<HomeRoute> { BasicText("home") }
                composable<LibraryRoute> { BasicText("library") }
                composable<QueueDestination> { BasicText("queue") }
            }
        }
    }

    private fun selectTab(route: Any) = nav.navigate(route) {
        popUpTo(HomeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    private fun assertLibraryPickingAboveHome() {
        val entry = nav.currentBackStackEntry!!
        // hasRoute : c'est ce test qui allume l'onglet Bibliotheque dans TmmRoot.
        assertTrue(entry.destination.hasRoute(LibraryRoute::class))
        assertEquals(LibraryRoute(picking = true), entry.toRoute<LibraryRoute>())
        assertTrue(nav.previousBackStackEntry!!.destination.hasRoute(HomeRoute::class))
    }

    @Test
    fun plus_afterLibraryTabWasSaved_opensPicking() {
        setUpNavHost()
        composeRule.runOnIdle { selectTab(LibraryRoute()) }
        composeRule.runOnIdle { selectTab(QueueDestination) }
        composeRule.runOnIdle { nav.openLibraryPicking() }
        composeRule.runOnIdle { assertLibraryPickingAboveHome() }
    }

    @Test
    fun plus_withLibraryStillInStack_opensPicking() {
        setUpNavHost()
        composeRule.runOnIdle { selectTab(LibraryRoute()) }
        // Toast "Voir la file" de la Bibliotheque : navigate simple, la Bibliotheque reste dessous.
        composeRule.runOnIdle { nav.navigate(QueueDestination) }
        composeRule.runOnIdle { nav.openLibraryPicking() }
        composeRule.runOnIdle { assertLibraryPickingAboveHome() }
    }
}
