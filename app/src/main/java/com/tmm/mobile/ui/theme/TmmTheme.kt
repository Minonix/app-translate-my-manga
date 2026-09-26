package com.tmm.mobile.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import com.tmm.mobile.core.LocalReducedMotion
import com.tmm.mobile.core.rememberReducedMotion

val LocalTextStyle = compositionLocalOf { TmmType.sans(15) }

/** Indication vide : les maquettes n'utilisent jamais de ripple Material. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return object : Modifier.Node() {}
    }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0
}

@Composable
fun TmmTheme(content: @Composable () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalReducedMotion provides reducedMotion,
        LocalTextStyle provides TmmType.sans(15),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(TmmColors.paper)) {
            content()
        }
    }
}
