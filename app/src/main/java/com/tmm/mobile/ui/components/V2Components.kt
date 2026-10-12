package com.tmm.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.pressScale
import kotlinx.coroutines.delay

/*
 * Composants de la maquette v2 (maquettes/v2-simplifiee/index.html). Les composants v1 (TmmSheet,
 * TmmToast, TmmButton...) restent pour les ecrans hors refonte (Onboarding, Permission, Empty, Offline).
 */

private val C = TmmColors.V2

/** Taille CSS fractionnaire (13.5px...) que TmmType, en entiers, ne sait pas exprimer. */
fun TextStyle.px(size: Float, lineHeight: Float? = null): TextStyle =
    copy(fontSize = size.sp, lineHeight = lineHeight?.let { (it * size).sp } ?: TextUnit.Unspecified)

/** Classes `.s-*` de la maquette v2 : un ton par statut de chapitre. */
enum class PillTone(val background: Color, val foreground: Color, val outlined: Boolean = false) {
    Todo(C.redSoft, C.redDark),
    Busy(C.ink, C.white),
    Get(C.blueSoft, C.blue),
    Ok(C.greenSoft, C.green),
    Fail(C.red, C.white),
    Wait(C.chip, C.ink3),
    Absent(C.white, C.muted, outlined = true),
    Gap(C.amberSoft, C.amber),
}

/**
 * `.st-pill` : pastille de statut. `plain` retire le point, `small` est la variante sur couverture,
 * `icon` s'ajoute après le point (flèches Envoi et Retour de l'Activité).
 */
@Composable
fun StatusPill(
    tone: PillTone,
    label: String,
    modifier: Modifier = Modifier,
    plain: Boolean = false,
    small: Boolean = false,
    icon: TmmIconId? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .height(if (small) 24.dp else 26.dp)
            .clip(shape)
            .background(tone.background)
            .then(if (tone.outlined) Modifier.border(1.5.dp, C.line, shape) else Modifier)
            .padding(horizontal = if (small) 8.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!plain) Box(Modifier.size(7.dp).clip(CircleShape).background(tone.foreground))
        if (icon != null) TmmIcon(icon, size = 14.dp, tint = tone.foreground)
        TmmText(
            text = label,
            style = TmmType.sans(if (small) 12 else 13, weight = 600),
            color = tone.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `.auto-tag` : etiquette noire « Suivie » des series au suivi automatique. */
@Composable
fun AutoTag(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.ink)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TmmIcon(TmmIconId.V2Bolt, size = 13.dp, tint = C.white, strokeWidth = 2.2f)
        TmmText(text = label, style = TmmType.sans(12, weight = 600), color = C.white)
    }
}

enum class V2ButtonStyle { Red, Ink, Ghost, Plain }
enum class V2ButtonSize { Normal, Block, Small }

/** `.btn` et ses variantes `.red`, `.ink`, `.ghost`, `.block`, `.sm`. */
@Composable
fun V2Button(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: V2ButtonStyle = V2ButtonStyle.Red,
    size: V2ButtonSize = V2ButtonSize.Normal,
    icon: TmmIconId? = null,
    enabled: Boolean = true,
    contentColor: Color? = null,
) {
    val (height, radius, fontSize) = when (size) {
        V2ButtonSize.Normal -> Triple(44.dp, 14.dp, 15)
        V2ButtonSize.Block -> Triple(54.dp, 16.dp, 16)
        V2ButtonSize.Small -> Triple(36.dp, 11.dp, 14)
    }
    val shape = RoundedCornerShape(radius)
    val (bg, fg) = when (style) {
        V2ButtonStyle.Red -> C.red to C.white
        V2ButtonStyle.Ink -> C.ink to C.white
        V2ButtonStyle.Ghost -> C.white to C.ink
        V2ButtonStyle.Plain -> Color.Transparent to C.ink
    }
    val textColor = contentColor ?: fg
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(height)
            .then(if (size == V2ButtonSize.Block) Modifier.fillMaxWidth() else Modifier)
            .pressScale(interactionSource)
            .clip(shape)
            .background(bg)
            .then(if (style == V2ButtonStyle.Ghost) Modifier.border(1.5.dp, C.line, shape) else Modifier)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (size == V2ButtonSize.Small) 12.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) TmmIcon(icon, size = 20.dp, tint = textColor)
        TmmText(text = label, style = TmmType.sans(fontSize, weight = 600), color = textColor, maxLines = 1)
    }
}

/** `.iconbtn` : bouton rond de 44 a bordure. */
@Composable
fun V2IconButton(icon: TmmIconId, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(C.white)
            .border(1.5.dp, C.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        TmmIcon(icon, size = 22.dp, tint = C.ink)
    }
}

/** `.switch` : 52 x 32, sable eteint, encre (ou `onColor`) allume. */
@Composable
fun V2Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onColor: Color = C.ink,
    offColor: Color = C.sand,
    contentDescription: String? = null,
) {
    val track by animateColorAsState(if (checked) onColor else offColor, tween(200), label = "v2SwitchTrack")
    val thumb by animateDpAsState(if (checked) 20.dp else 0.dp, tween(200), label = "v2SwitchThumb")
    Box(
        modifier = modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(track)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        Box(
            Modifier
                // Lecture de `thumb` en phase de placement : pas de recomposition a chaque frame.
                .offset { IntOffset((4.dp + thumb).roundToPx(), 4.dp.roundToPx()) }
                .shadow(1.5.dp, CircleShape, ambientColor = TmmColors.Black15, spotColor = TmmColors.Black15)
                .size(24.dp)
                .clip(CircleShape)
                .background(C.white),
        )
    }
}

/** `.check` : case carree de 24 (rayon 7), encre une fois cochee. */
@Composable
fun V2Check(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(shape)
            .background(if (checked) C.ink else C.white)
            .border(1.8.dp, if (checked) C.ink else C.sand, shape)
            .semantics { role = Role.Checkbox },
        contentAlignment = Alignment.Center,
    ) {
        if (checked && enabled) TmmIcon(TmmIconId.V2Check, size = 15.dp, tint = C.white, strokeWidth = 2.6f)
    }
}

/** `.act` : ligne d'action de feuille (icone, titre, description, compte a droite). */
@Composable
fun V2ActionRow(
    icon: TmmIconId,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    danger: Boolean = false,
    iconBackground: Color? = null,
    iconTint: Color? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(C.white)
            .border(1.dp, C.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground ?: if (danger) C.redSoft else C.chip),
            contentAlignment = Alignment.Center,
        ) {
            TmmIcon(icon, size = 22.dp, tint = iconTint ?: if (danger) C.red else C.ink)
        }
        Column(Modifier.weight(1f)) {
            TmmText(text = title, style = TmmType.sans(16, weight = 600), color = if (danger) C.redDark else C.ink)
            TmmText(
                text = description,
                style = TmmType.sans(13).px(13.5f, lineHeight = 1.35f),
                color = C.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (count != null) TmmText(text = count.toString(), style = TmmType.mono(14), color = C.muted)
    }
}

/** En-tete de feuille : `.kick` (petites capitales) puis `.ttl`. */
@Composable
fun V2SheetHeader(kick: String?, title: String) {
    if (kick != null) {
        TmmText(
            text = kick.uppercase(),
            style = TmmType.sans(12, weight = 600, letterSpacingEm = 1.3f / 12f),
            color = C.muted,
        )
    }
    TmmText(
        text = title,
        style = TmmType.display(26, weight = 800, lineHeight = 28.6f, letterSpacingEm = -0.4f / 26f),
        color = C.ink,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** `.sheet` : feuille du bas sur fond `bg`, rayon 28, poignee sable, voile `.scrim`. */
@Composable
fun V2Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    paneTitle: String = "Feuille",
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    V2SheetFrame(visible, onDismiss, modifier, paneTitle, footer) {
        // Le pied (`position:sticky;bottom:0` de la maquette) reste visible sous la partie qui defile.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            SheetHandle(Modifier.align(Alignment.CenterHorizontally))
            content()
        }
    }
}

/**
 * [V2Sheet] dont le corps est une liste paresseuse : pour les feuilles qui listent toute la
 * bibliotheque (2000 series), ou une Column composerait chaque ligne a l'ouverture.
 */
@Composable
fun V2LazySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    paneTitle: String = "Feuille",
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    V2SheetFrame(visible, onDismiss, modifier, paneTitle, footer) {
        LazyColumn(Modifier.weight(1f, fill = false)) {
            item(key = "handle") {
                Box(Modifier.fillMaxWidth()) { SheetHandle(Modifier.align(Alignment.Center)) }
            }
            content()
        }
    }
}

@Composable
private fun SheetHandle(modifier: Modifier) {
    Box(
        modifier
            .padding(bottom = 16.dp)
            .size(40.dp, 5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(C.sand),
    )
}

@Composable
private fun V2SheetFrame(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier,
    paneTitle: String,
    footer: (@Composable ColumnScope.() -> Unit)?,
    body: @Composable ColumnScope.() -> Unit,
) {
    if (visible) BackHandler(onBack = onDismiss)
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(C.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(280)) { it },
                exit = slideOutVertically(tween(220)) { it },
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(C.bg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                        .semantics { this.paneTitle = paneTitle }
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = if (footer == null) 26.dp else 0.dp),
                ) {
                    body()
                    footer?.invoke(this)
                }
            }
        }
    }
}

/**
 * `.toast` : bandeau noir en bas d'ecran. `durationMs` est allonge par l'appelant quand le toast porte
 * une action a toucher (« Annuler ») : 3,6 s ne laissent pas le temps de lire puis de reagir.
 */
@Composable
fun V2Toast(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: TmmIconId? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    durationMs: Long = 3600L,
) {
    LaunchedEffect(visible, message) {
        if (visible) {
            delay(durationMs)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = TmmColors.Black40, spotColor = TmmColors.Black40)
                .clip(RoundedCornerShape(16.dp))
                .background(C.ink)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) TmmIcon(icon, size = 22.dp, tint = C.white)
            TmmText(text = message, style = TmmType.sans(14), color = C.white, modifier = Modifier.weight(1f))
            if (actionLabel != null) {
                TmmText(
                    text = actionLabel,
                    style = TmmType.sans(14, weight = 600),
                    color = C.toastLink,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onAction),
                )
            }
        }
    }
}

/**
 * Une ligne d'une [v2Card] decoupee en elements de LazyColumn : seuls le premier et le dernier
 * portent les coins arrondis et le bord haut/bas. Le contour est trace comme celui de la carte
 * entiere, prolonge au-dela de l'element quand il n'est pas en bout, si bien que seuls les bords
 * lateraux restent visibles entre deux lignes.
 */
fun Modifier.v2CardItem(
    isFirst: Boolean,
    isLast: Boolean,
    background: Color = C.card,
    borderColor: Color = C.line,
    radius: Dp = 22.dp,
): Modifier {
    val shape = RoundedCornerShape(
        topStart = if (isFirst) radius else 0.dp,
        topEnd = if (isFirst) radius else 0.dp,
        bottomStart = if (isLast) radius else 0.dp,
        bottomEnd = if (isLast) radius else 0.dp,
    )
    return clip(shape).background(background).drawWithContent {
        drawContent()
        val stroke = 1.dp.toPx()
        val r = radius.toPx()
        val top = if (isFirst) 0f else -2 * r
        val bottom = if (isLast) size.height else size.height + 2 * r
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(stroke / 2, top + stroke / 2),
            size = Size(size.width - stroke, bottom - top - stroke),
            cornerRadius = CornerRadius(r - stroke / 2),
            style = Stroke(stroke),
        )
    }
}

/** `.card` : fond card, bordure line, rayon 22. */
fun Modifier.v2Card(background: Color = C.card, borderColor: Color = C.line, radius: Dp = 22.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(background).border(1.dp, borderColor, shape)
}
