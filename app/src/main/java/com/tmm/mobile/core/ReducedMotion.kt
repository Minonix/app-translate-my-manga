package com.tmm.mobile.core

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** true si l'utilisateur a reduit ou desactive les animations systeme. */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    // ponytail: lu une fois par composition, pas d'ecoute live du Settings.Global ;
    // ajouter un ContentObserver si un ecran doit reagir a un changement en direct.
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
