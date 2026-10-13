package com.tmm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.halftone

/**
 * Vignette de couverture de la tuile bibliotheque (Library.dc.html, tuile 150dp) :
 * texture demi-teinte + couleur placeholder derriere, image chargee par-dessus si `coverUrl` non nul.
 * `color` permet de reproduire la couleur exacte de la maquette (ex. Library) au lieu du hash de
 * `seriesKey` (utilise seulement en repli, meme principe que `ProgressBar.fillColor` en E4.1).
 */
@Composable
fun Cover(
    seriesKey: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val placeholder = color ?: TmmColors.placeholderFor(seriesKey)
    val context = LocalContext.current
    // URL reseau, sinon couverture de la source locale Mihon (cover.* dans local/<serie>/).
    val url = remember(coverUrl) { normalizeCoverUrl(coverUrl) }
    val model: Any? = remember(coverUrl) { url ?: localCoverUri(context, coverUrl) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(placeholder)
            .halftone(),
    ) {
        if (model != null) {
            // En cas d'echec, rien n'est dessine : le placeholder demi-teinte reste visible.
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { error ->
                    val t = error.result.throwable
                    AppLog.d("Cover", if (url != null) coverErrorLogLine(url, t) else "couverture locale non chargee erreur=${t::class.java.simpleName}")
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CoverPreview() {
    TmmTheme {
        Cover(
            seriesKey = "blue-lock",
            coverUrl = null,
            modifier = Modifier
                .height(150.dp)
                .width(110.dp),
        )
    }
}
