package com.tmm.mobile.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.core.LocalReducedMotion
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.halftone

/**
 * Écran d'installation / intégration (Onboarding.dc.html, E4.10).
 * Assistant en 3 étapes : connexion Google Drive, sélection du dossier racine Mihon,
 * et indexation initiale de la bibliothèque locale.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onEvent: (OnboardingEvent) -> Unit,
    onFinishOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val progressCd = stringResource(R.string.onb_progress_cd)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper),
    ) {
        // Header : icône de l'app + barre de progression 3 étapes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(TmmColors.accent),
                contentAlignment = Alignment.Center,
            ) {
                TmmText(
                    text = "訳",
                    style = TmmType.display(20, weight = 800),
                    color = TmmColors.surface,
                )
            }

            // Barre de progression 3 segments
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = state.step.toFloat(),
                            range = 1f..3f,
                        )
                        contentDescription = progressCd
                    },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in 1..3) {
                    val fillFraction = when {
                        i < state.step -> 1f
                        i == state.step -> when (state.step) {
                            1 -> if (state.isGoogleConnected) 1f else 0.35f
                            2 -> if (state.isMihonSelected) 1f else 0.35f
                            3 -> state.readProgress
                            else -> 1f
                        }
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(TmmColors.lineStrong),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = fillFraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(TmmColors.ink),
                        )
                    }
                }
            }

            TmmText(
                text = "${state.step}/3",
                style = TmmType.mono(12),
                color = TmmColors.textMuted,
            )
        }

        // Corps défilant
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 28.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            when (state.step) {
                1 -> Step1Content(state = state)
                2 -> Step2Content(state = state)
                3 -> Step3Content(state = state)
            }
        }

        // Footer avec boutons d'actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.step < 3) {
                // PLAN_GEMINI.md E6.7 : "Continuer" actif seulement si downloads/ existe (les
                // autres sous-dossiers sont informatifs).
                val canContinueStep2 = state.step != 2 || !state.isMihonSelected ||
                    state.subfolders.firstOrNull { it.name == "downloads/" }?.found != false
                val btnLabel = when {
                    state.step == 1 && state.isWorking -> stringResource(R.string.onb_btn_connecting)
                    state.step == 1 && state.isGoogleConnected -> stringResource(R.string.onb_btn_continue)
                    state.step == 1 -> stringResource(R.string.onb_btn_connect_google)
                    state.step == 2 && state.isWorking -> stringResource(R.string.onb_btn_verifying)
                    state.step == 2 && !state.isMihonSelected -> stringResource(R.string.onb_btn_pick_folder)
                    else -> stringResource(R.string.onb_btn_continue)
                }

                val btnBg = if (state.step == 1 && !state.isGoogleConnected) TmmColors.C2B4C7E else TmmColors.ink

                // Transition creee seulement pendant le travail : sinon elle demandait une frame a
                // chaque vsync tant que l'ecran restait affiche (batterie, et boucle infinie du
                // looper Robolectric qui faisait durer SmokeTest 30 a 60 min).
                val spinAngle = if (state.isWorking && !LocalReducedMotion.current) {
                    val infiniteTransition = rememberInfiniteTransition(label = "spin")
                    val angle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900, easing = LinearEasing),
                        ),
                        label = "spinAngle",
                    )
                    angle
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (canContinueStep2) btnBg else TmmColors.textMuted)
                        .clickable(
                            role = Role.Button,
                            enabled = !state.isWorking && canContinueStep2,
                            onClickLabel = btnLabel,
                            onClick = { onEvent(OnboardingEvent.PrimaryAction) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isWorking) {
                            TmmIcon(
                                icon = TmmIconId.SpinnerArc,
                                size = 18.dp,
                                tint = TmmColors.surface,
                                modifier = Modifier.rotate(spinAngle),
                            )
                        }
                        TmmText(
                            text = btnLabel,
                            style = TmmType.sans(16, weight = 600),
                            color = TmmColors.surface,
                        )
                    }
                }
            } else if (state.step == 3 && state.isReadDone) {
                // Bouton de fin d'onboarding
                val finishLabel = stringResource(R.string.onb_btn_finish)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TmmColors.accent)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = finishLabel,
                            onClick = onFinishOnboarding,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TmmText(
                            text = finishLabel,
                            style = TmmType.sans(16, weight = 600),
                            color = TmmColors.surface,
                        )
                        TmmIcon(
                            icon = TmmIconId.ArrowRight,
                            size = 18.dp,
                            tint = TmmColors.surface,
                        )
                    }
                }
            }

            // Bouton retour étape précédente
            if (state.step > 1 && !state.isWorking) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(
                            role = Role.Button,
                            onClick = { onEvent(OnboardingEvent.PreviousStep) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(
                        text = stringResource(R.string.onb_btn_previous_step),
                        style = TmmType.sans(14, weight = 500),
                        color = TmmColors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Step1Content(state: OnboardingState) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        // Encart d'illustration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TmmColors.CE3E9F2)
                .halftone(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Téléphone
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, TmmColors.ink, RoundedCornerShape(12.dp))
                        .background(TmmColors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmIcon(
                        icon = TmmIconId.Phone,
                        size = 26.dp,
                        tint = TmmColors.ink,
                    )
                }

                // Cloud Drive
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(TmmColors.C2B4C7E),
                        contentAlignment = Alignment.Center,
                    ) {
                        TmmIcon(
                            icon = TmmIconId.Cloud,
                            size = 28.dp,
                            tint = TmmColors.surface,
                        )
                    }
                    TmmText(
                        text = stringResource(R.string.onb_drive_label),
                        style = TmmType.mono(10),
                        color = TmmColors.C2B4C7E,
                    )
                }

                // PC Bureau
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, TmmColors.ink, RoundedCornerShape(10.dp))
                        .background(TmmColors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmIcon(
                        icon = TmmIconId.Monitor,
                        size = 26.dp,
                        tint = TmmColors.ink,
                    )
                }
            }
        }

        // Textes
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TmmText(
                text = stringResource(R.string.onb_step1_title),
                style = TmmType.display(30, weight = 800),
                color = TmmColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            TmmText(
                text = stringResource(R.string.onb_step1_desc),
                style = TmmType.sans(15, lineHeight = 22.5f),
                color = TmmColors.textMuted,
            )
        }

        // Confirmation de compte Google connecté
        if (state.isGoogleConnected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(14.dp))
                    .background(TmmColors.surface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TmmIcon(
                        icon = TmmIconId.CheckSimple,
                        size = 16.dp,
                        tint = TmmColors.C2F7D4F,
                    )
                    TmmText(
                        text = state.googleAccount.ifBlank { stringResource(R.string.onb_step1_connected) },
                        style = TmmType.sans(14, weight = 600),
                        color = TmmColors.C2F7D4F,
                    )
                }

                if (state.sharedFolderName.isNotBlank() && state.pcLastSeen.isNotBlank()) TmmText(
                    text = stringResource(
                        R.string.onb_step1_folder_found_fmt,
                        state.sharedFolderName,
                        state.pcLastSeen,
                    ),
                    style = TmmType.sans(13),
                    color = TmmColors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun Step2Content(state: OnboardingState) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TmmText(
                text = stringResource(R.string.onb_step2_title),
                style = TmmType.display(30, weight = 800),
                color = TmmColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            TmmText(
                text = stringResource(R.string.onb_step2_desc),
                style = TmmType.sans(15, lineHeight = 22.5f),
                color = TmmColors.textMuted,
            )
        }

        // Boîte dossier racine Mihon
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
                .background(TmmColors.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TmmIcon(
                    icon = TmmIconId.Folder,
                    size = 20.dp,
                    tint = TmmColors.ink,
                )
                TmmText(
                    text = if (state.isMihonSelected) state.mihonFolderPath else stringResource(R.string.onb_step2_no_folder),
                    style = TmmType.mono(14),
                    color = TmmColors.ink,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.isMihonSelected) {
                state.subfolders.forEachIndexed { index, sub ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TmmColors.line),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (sub.found) TmmColors.C2F7D4F else TmmColors.textMuted),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sub.found) {
                                TmmIcon(
                                    icon = TmmIconId.CheckSimple,
                                    size = 12.dp,
                                    tint = TmmColors.surface,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            TmmText(
                                text = sub.name,
                                style = TmmType.mono(13),
                                color = TmmColors.ink,
                            )
                            TmmText(
                                text = if (sub.found) sub.role else stringResource(R.string.onboarding_folder_missing),
                                style = TmmType.sans(12),
                                color = TmmColors.textMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Step3Content(state: OnboardingState) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TmmText(
                text = stringResource(R.string.onb_step3_title),
                style = TmmType.display(30, weight = 800),
                color = TmmColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            TmmText(
                text = stringResource(R.string.onb_step3_desc),
                style = TmmType.sans(15, lineHeight = 22.5f),
                color = TmmColors.textMuted,
            )
        }

        // Carte d'indexation de la sauvegarde
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(TmmColors.ink)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TmmText(
                text = if (state.hasBackup) state.backupFileName else stringResource(R.string.empty_no_backup_title),
                style = TmmType.mono(12),
                color = TmmColors.CCFC8B8,
            )

            // Jauge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TmmColors.C2E2D29),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = state.readProgress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TmmColors.accent),
                )
            }

            // Grille 3 métriques
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TmmText(
                        text = "${state.seriesCount}",
                        style = TmmType.display(30, weight = 800),
                        color = TmmColors.surface,
                    )
                    TmmText(
                        text = stringResource(R.string.onb_step3_series_label),
                        style = TmmType.sans(12),
                        color = TmmColors.CCFC8B8,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TmmText(
                        text = "${state.categoriesCount}",
                        style = TmmType.display(30, weight = 800),
                        color = TmmColors.surface,
                    )
                    TmmText(
                        text = stringResource(R.string.onb_step3_categories_label),
                        style = TmmType.sans(12),
                        color = TmmColors.CCFC8B8,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TmmText(
                        text = "${state.downloadedCount}",
                        style = TmmType.display(30, weight = 800),
                        color = TmmColors.surface,
                    )
                    TmmText(
                        text = stringResource(R.string.onb_step3_downloaded_label),
                        style = TmmType.sans(12),
                        color = TmmColors.CCFC8B8,
                    )
                }
            }
        }

        // Alerte chapitres à synchroniser (PLAN_GEMINI.md 7.10 : masquee si toSendCount == 0)
        if (state.isReadDone && state.toSendCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(12.dp))
                    .background(TmmColors.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TmmIcon(
                    icon = TmmIconId.Up,
                    size = 18.dp,
                    tint = TmmColors.accent,
                )
                TmmText(
                    text = pluralStringResource(R.plurals.onb_step3_todo_notice, state.toSendCount, state.toSendCount),
                    style = TmmType.sans(13, lineHeight = 19f),
                    color = TmmColors.ink,
                )
            }
        }
    }
}

@Preview(name = "Onboarding - Step 1 Default", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStep1Preview() {
    TmmTheme {
        OnboardingScreen(
            state = MockupData.onboarding,
            onEvent = {},
        )
    }
}

@Preview(name = "Onboarding - Step 2 Configured", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStep2Preview() {
    TmmTheme {
        OnboardingScreen(
            state = MockupData.onboarding.copy(step = 2, isMihonSelected = true),
            onEvent = {},
        )
    }
}

@Preview(name = "Onboarding - Step 3 Done", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStep3Preview() {
    TmmTheme {
        OnboardingScreen(
            state = MockupData.onboarding.copy(
                step = 3,
                readProgress = 1f,
                seriesCount = 142,
                categoriesCount = 7,
                downloadedCount = 104,
                isReadDone = true,
            ),
            onEvent = {},
        )
    }
}
