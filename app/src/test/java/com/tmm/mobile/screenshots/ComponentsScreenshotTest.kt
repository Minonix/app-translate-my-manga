package com.tmm.mobile.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tmm.mobile.ui.components.Cover
import com.tmm.mobile.ui.components.OfflineBanner
import com.tmm.mobile.ui.components.OfflineBannerState
import com.tmm.mobile.ui.components.PresenceIcons
import com.tmm.mobile.ui.components.ProgressBar
import com.tmm.mobile.ui.components.RefreshButton
import com.tmm.mobile.ui.components.SectionLabel
import com.tmm.mobile.ui.components.StatusBadge
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.components.TmmBottomBar
import com.tmm.mobile.ui.components.TmmBottomBarItem
import com.tmm.mobile.ui.components.TmmButton
import com.tmm.mobile.ui.components.TmmCheckbox
import com.tmm.mobile.ui.components.TmmChip
import com.tmm.mobile.ui.components.TmmDialog
import com.tmm.mobile.ui.components.TmmSearchField
import com.tmm.mobile.ui.components.TmmSegmented
import com.tmm.mobile.ui.components.TmmSheet
import com.tmm.mobile.ui.components.TmmSwitch
import com.tmm.mobile.ui.components.TmmToast
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Une capture Roborazzi par composant maison de E3 (docs/PLAN_GEMINI.md 4.9).
 * Pas de plugin Gradle Roborazzi installe : les images sont ecrites directement
 * sous build/outputs/roborazzi via captureRoboImage, sans tache verify/record dediee.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class ComponentsScreenshotTest {
    private val composeRule = createComposeRule()

    @get:org.junit.Rule
    val rule = composeRule

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { TmmTheme { content() } }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun tmmSwitch() = capture("TmmSwitch") {
        TmmSwitch(checked = true, onCheckedChange = {})
    }

    @Test
    fun tmmCheckbox() = capture("TmmCheckbox") {
        Row { TmmCheckbox(checked = true, onCheckedChange = {}); TmmCheckbox(checked = false, onCheckedChange = {}) }
    }

    @Test
    fun tmmSegmented() = capture("TmmSegmented") {
        TmmSegmented(options = listOf("grid" to "Grille", "list" to "Liste"), selected = "grid", onSelect = {})
    }

    @Test
    fun tmmChip() = capture("TmmChip") {
        Row { TmmChip(label = "En cours", selected = true, onClick = {}, count = 24) }
    }

    @Test
    fun tmmButton() = capture("TmmButton") {
        Column { TmmButton(label = "Confirmer", onClick = {}); TmmButton(label = "Annuler", onClick = {}, primary = false) }
    }

    @Test
    fun tmmSearchField() = capture("TmmSearchField") {
        TmmSearchField(value = "", onValueChange = {})
    }

    @Test
    fun tmmSheet() = capture("TmmSheet") {
        TmmSheet(visible = true, onDismiss = {}, title = "Retraduire ?") {
            TmmButton(label = "Confirmer", onClick = {})
        }
    }

    @Test
    fun tmmDialog() = capture("TmmDialog") {
        TmmDialog(visible = true, onDismiss = {}, title = "Supprimer ?") {
            TmmButton(label = "Supprimer", onClick = {})
        }
    }

    @Test
    fun tmmToast() = capture("TmmToast") {
        TmmToast(visible = true, message = "Chapitre envoyé.", onDismiss = {})
    }

    @Test
    fun offlineBanner() = capture("OfflineBanner") {
        Column {
            OfflineBanner(OfflineBannerState.NoNetwork)
            OfflineBanner(OfflineBannerState.TokenExpired(reconnecting = false, onReconnect = {}))
        }
    }

    @Test
    fun cover() = capture("Cover") {
        Cover(seriesKey = "blue-lock", coverUrl = null, modifier = Modifier.height(150.dp).width(110.dp))
    }

    @Test
    fun statusBadge() = capture("StatusBadge") {
        Row { StatusBadge(StatusTone.Send, "1 à envoyer") }
    }

    @Test
    fun presenceIcons() = capture("PresenceIcons") {
        PresenceIcons(phone = true, pc = false, fr = true)
    }

    @Test
    fun progressBar() = capture("ProgressBar") {
        ProgressBar(fraction = 0.6f, modifier = Modifier.width(150.dp))
    }

    @Test
    fun refreshButton() = capture("RefreshButton") {
        RefreshButton(onRefresh = {})
    }

    @Test
    fun sectionLabel() = capture("SectionLabel") {
        SectionLabel(text = "En cours")
    }

    @Test
    fun tmmBottomBar() = capture("TmmBottomBar") {
        TmmBottomBar(
            items = listOf(
                TmmBottomBarItem(TmmIconId.Home, "Accueil"),
                TmmBottomBarItem(TmmIconId.Grid, "Bibliothèque"),
            ),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}
