package com.tmm.mobile.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.Motion.TmmEase
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val ThumbSize = 24.dp
private val ThumbTravel = 20.dp
private val ThumbInset = 4.dp

/** Interrupteur maison (Settings.dc.html) : piste 52x32, pastille 24, coche quand actif. */
@Composable
fun TmmSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsStateEase(if (checked) TmmColors.ink else TmmColors.track)
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) ThumbTravel else 0.dp,
        animationSpec = tween(250, easing = TmmEase),
        label = "thumbOffset",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(TrackWidth, TrackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = ThumbInset + thumbOffset, y = ThumbInset)
                .size(ThumbSize)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                TmmIcon(TmmIconId.CheckSimple, tint = TmmColors.ink, size = 12.dp)
            }
        }
    }
}

@Composable
private fun animateColorAsStateEase(target: Color) =
    androidx.compose.animation.animateColorAsState(target, tween(250), label = "trackColor")

@Preview(showBackground = true)
@Composable
private fun TmmSwitchPreview() {
    TmmTheme {
        androidx.compose.foundation.layout.Row {
            TmmSwitch(checked = true, onCheckedChange = {})
            TmmSwitch(checked = false, onCheckedChange = {})
        }
    }
}
