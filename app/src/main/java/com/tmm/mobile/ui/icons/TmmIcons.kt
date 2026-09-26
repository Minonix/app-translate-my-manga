package com.tmm.mobile.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmm.mobile.ui.theme.TmmColors

/**
 * Element geometrique d'une icone, dans le systeme de coordonnees SVG des
 * maquettes (viewBox 0 0 24 24). Reproduit path/rect/circle tels qu'extraits
 * des maquettes (voir 4.6 du plan).
 */
sealed class IconElement {
    data class Path(val d: String) : IconElement()
    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float, val rx: Float = 0f) : IconElement()
    data class Circle(val cx: Float, val cy: Float, val r: Float) : IconElement()
}

/**
 * Catalogue ferme des icones de l'application, extrait des 13 maquettes
 * (maquettes). Aucune icone Material n'est utilisee (voir NoMaterialIconsTest).
 * Les noms d'entrees suivent l'objet `I` des scripts de maquette quand il existe,
 * sinon une description anglaise camelCase de la forme.
 */
enum class TmmIconId(val defaultStrokeWidth: Float, val elements: List<IconElement>) {
    File(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M6 3h9l4 4v14H6zM14 3v5h5"))),
    CheckSimple(defaultStrokeWidth = 2.4f, elements = listOf(IconElement.Path("M5 12l5 5 9-10"))),
    Refresh(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Path("M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"))),
    ChevronRight(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Path("M9 5l7 7-7 7"))),
    Home(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z"))),
    Grid(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Rect(3f, 3f, 7f, 9f, 1f), IconElement.Rect(14f, 3f, 7f, 9f, 1f), IconElement.Rect(3f, 15f, 7f, 6f, 1f), IconElement.Rect(14f, 15f, 7f, 6f, 1f))),
    Sync(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M4 12a8 8 0 0 1 14-5.3L20 9M20 4v5h-5M20 12a8 8 0 0 1-14 5.3L4 15M4 20v-5h5"))),
    List(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M9 6h12M9 12h12M9 18h12M4 6h.01M4 12h.01M4 18h.01"))),
    Inbox(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M3 4h18v5H3zM5 9v11h14V9M10 13h4"))),
    Pages(defaultStrokeWidth = 1.6f, elements = listOf(IconElement.Path("M4 5h7v14H4zM13 5h7v14h-7z"))),
    Filter(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M4 6h16M7 12h10M10 18h4"))),
    CheckboxChecked(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Rect(3f, 3f, 18f, 18f, 4f), IconElement.Path("M8 12l3 3 5-6"))),
    Search(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Circle(11f, 11f, 7f), IconElement.Path("M20 20l-3.5-3.5"))),
    SearchEmpty(defaultStrokeWidth = 1.5f, elements = listOf(IconElement.Circle(11f, 11f, 7f), IconElement.Path("M20 20l-3.5-3.5M8 11h6"))),
    Info(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Circle(12f, 12f, 9f), IconElement.Path("M12 11v5M12 8h.01"))),
    Settings(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M4 7h9M17 7h3M4 17h3M11 17h9"), IconElement.Circle(15f, 7f, 2f), IconElement.Circle(9f, 17f, 2f))),
    Cloud(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9a4.5 4.5 0 0 1-.5 9z"))),
    PhoneOutline(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Rect(7f, 2f, 10f, 20f, 2f))),
    ArrowRight(defaultStrokeWidth = 2.2f, elements = listOf(IconElement.Path("M5 12h14M13 6l6 6-6 6"))),
    Monitor(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Rect(2f, 4f, 20f, 13f, 2f), IconElement.Path("M8 21h8M12 17v4"))),
    MonitorOutline(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Rect(2f, 4f, 20f, 13f, 2f))),
    SpinnerArc(defaultStrokeWidth = 2.2f, elements = listOf(IconElement.Path("M12 3a9 9 0 1 0 9 9"))),
    Translate(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M4 5h8M8 3v2M5 5c1 4 4 7 7 8M11 5c-1 4-4 7-7 8M13 21l4-10 4 10M14.5 17h5"))),
    Phone(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Rect(7f, 2f, 10f, 20f, 2f), IconElement.Path("M11 18h2"))),
    Download(defaultStrokeWidth = 2.2f, elements = listOf(IconElement.Path("M12 4v12M6 10l6 6 6-6M4 20h16"))),
    ChevronLeft(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Path("M15 5l-7 7 7 7"))),
    Close(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Path("M6 6l12 12M18 6L6 18"))),
    WifiOff(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M2 8.8a15 15 0 0 1 4.2-2.6M10.7 5.1A15 15 0 0 1 22 8.8M5 12.5a10 10 0 0 1 3.5-2M15 10.8a10 10 0 0 1 4 1.7M8.5 16a5 5 0 0 1 7 0M12 20h.01M3 3l18 18"))),
    Lock(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Rect(5f, 11f, 14f, 10f, 2f), IconElement.Path("M8 11V7a4 4 0 0 1 8 0v4"))),
    Clock(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Circle(12f, 12f, 9f), IconElement.Path("M12 7v5l3 2"))),
    Folder(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M3 7h6l2 2h10v10H3z"))),
    Up(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Path("M12 19V5M6 11l6-6 6 6"))),
    FolderDenied(defaultStrokeWidth = 1.6f, elements = listOf(IconElement.Path("M3 7h6l2 2h10v10H3z"), IconElement.Path("M10 13l4 4M14 13l-4 4"))),
    Plus(defaultStrokeWidth = 2.2f, elements = listOf(IconElement.Path("M12 5v14M5 12h14"))),
    BarChart(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Path("M4 20V10M10 20V4M16 20v-7M22 20H2"))),
    Warning(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Path("M12 3l10 18H2zM12 10v5M12 18h.01"))),
    CheckCircle(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Circle(12f, 12f, 9f), IconElement.Path("M8 12l3 3 5-6"))),
    MoreDots(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Circle(5f, 12f, 1f), IconElement.Circle(12f, 12f, 1f), IconElement.Circle(19f, 12f, 1f))),
    Down(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Path("M12 5v14M6 13l6 6 6-6"))),
    ChevronDown(defaultStrokeWidth = 2.2f, elements = listOf(IconElement.Path("M6 9l6 6 6-6"))),
    ShieldCheck(defaultStrokeWidth = 2.0f, elements = listOf(IconElement.Path("M9 12l2 2 4-4M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z"))),
    PhoneDevice(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM11 18h2"))),
    CloudDevice(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M7 18a4 4 0 0 1-.6-8A6 6 0 0 1 18 9a4.5 4.5 0 0 1-.5 9z"))),
    MonitorDevice(defaultStrokeWidth = 1.8f, elements = listOf(IconElement.Path("M4 4h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM8 21h8M12 17v4"))),
}

private val vectorCache = HashMap<Triple<TmmIconId, Int, Float>, ImageVector>()

private fun PathBuilder.rectOutline(rect: IconElement.Rect) {
    val (x, y, w, h, rx) = rect
    if (rx <= 0f) {
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); close()
    } else {
        moveTo(x + rx, y)
        lineTo(x + w - rx, y)
        arcTo(rx, rx, 0f, false, true, x + w, y + rx)
        lineTo(x + w, y + h - rx)
        arcTo(rx, rx, 0f, false, true, x + w - rx, y + h)
        lineTo(x + rx, y + h)
        arcTo(rx, rx, 0f, false, true, x, y + h - rx)
        lineTo(x, y + rx)
        arcTo(rx, rx, 0f, false, true, x + rx, y)
        close()
    }
}

private fun PathBuilder.circleOutline(circle: IconElement.Circle) {
    val (cx, cy, r) = circle
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    close()
}

private fun buildImageVector(icon: TmmIconId, sizeDp: Int, strokeWidth: Float): ImageVector =
    ImageVector.Builder(
        name = icon.name,
        defaultWidth = sizeDp.dp,
        defaultHeight = sizeDp.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        icon.elements.forEach { element ->
            when (element) {
                is IconElement.Path -> addPath(
                    pathData = addPathNodes(element.d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                is IconElement.Rect -> path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) { rectOutline(element) }
                is IconElement.Circle -> path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) { circleOutline(element) }
            }
        }
    }.build()

@Composable
fun TmmIcon(
    icon: TmmIconId,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = TmmColors.ink,
    strokeWidth: Float = icon.defaultStrokeWidth,
    contentDescription: String? = null,
) {
    val sizeDp = size.value.toInt()
    val vector: ImageVector = remember(icon, sizeDp, strokeWidth) {
        vectorCache.getOrPut(Triple(icon, sizeDp, strokeWidth)) { buildImageVector(icon, sizeDp, strokeWidth) }
    }
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}
