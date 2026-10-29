package com.tmm.mobile.ui.screens.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.BuildConfig
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.TmmSwitch
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.TmmToast
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType

/**
 * Écran Réglages (Settings.dc.html, E4.8).
 * Configuration du compte Google Drive, synchronisation Mihon, réglages de traduction,
 * politique de réseau et diagnostics.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateOnboarding: () -> Unit = {},
    onNavigateTranslationSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state.directOpen) {
        DirectConnectionPage(state = state, onEvent = onEvent, modifier = modifier)
        return
    }
    val pcStatusCd = stringResource(R.string.settings_pc_status_cd)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Corps défilant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Titre H1
                TmmText(
                    text = stringResource(R.string.settings_title),
                    style = TmmType.display(36, weight = 800),
                    color = TmmColors.ink,
                    modifier = Modifier.semantics { heading() },
                )

                // Carte État du PC
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(TmmColors.ink)
                        .semantics { contentDescription = pcStatusCd }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TmmColors.C2E2D29),
                            contentAlignment = Alignment.Center,
                        ) {
                            TmmIcon(
                                icon = TmmIconId.Monitor,
                                size = 22.dp,
                                tint = TmmColors.paper,
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            TmmText(
                                text = state.pcName,
                                style = TmmType.sans(16, weight = 600),
                                color = TmmColors.paper,
                            )
                            TmmText(
                                text = state.pcLastSignalText,
                                style = TmmType.sans(13),
                                color = TmmColors.CCFC8B8,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isPcOnline) TmmColors.C2F7D4F else TmmColors.dangerText),
                            )
                            TmmText(
                                text = stringResource(
                                    if (state.directStatus == DirectStatus.Direct) R.string.settings_v2_direct else R.string.settings_v2_drive
                                ),
                                style = TmmType.sans(12, weight = 600),
                                color = if (state.isPcOnline) TmmColors.C9BD3AE else TmmColors.dangerText,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TmmColors.C2E2D29),
                    )

                    // Sans PC associe, ne pas annoncer la connexion directe : Drive est le seul canal.
                    val pcVersion = state.pcVersionProtocol.substringBefore(" ·")
                    TmmText(
                        text = if (state.directStatus == DirectStatus.NotPaired || state.directStatus == DirectStatus.Expired) {
                            stringResource(R.string.settings_v2_connection_drive_fmt, pcVersion)
                        } else {
                            stringResource(R.string.settings_v2_connection_fmt, pcVersion)
                        },
                        style = TmmType.sans(12),
                        color = TmmColors.C9C9486,
                    )
                }

                SettingsGroup(title = stringResource(R.string.settings_group_translation)) {
                    SettingsRow(label = stringResource(R.string.settings_v2_languages), value = state.languageSummary,
                        isLink = true, onRowClick = onNavigateTranslationSettings)
                    SettingsRow(label = stringResource(R.string.settings_v2_engine), value = state.engineSummary,
                        isLink = true, onRowClick = onNavigateTranslationSettings)
                    SettingsRow(label = stringResource(R.string.settings_v2_all_pc), value = stringResource(R.string.settings_v2_all_pc_hint),
                        isLink = true, onRowClick = onNavigateTranslationSettings, showBottomDivider = false)
                }
                SettingsGroup(title = stringResource(R.string.settings_v2_followed)) {
                    SettingsRow(label = pluralStringResource(R.plurals.settings_v2_series_count, state.followedSeries.size, state.followedSeries.size),
                        value = state.followedSeries.joinToString { it.title }.ifEmpty { stringResource(R.string.settings_v2_no_followed) },
                        isLink = true, onRowClick = { onEvent(SettingsEvent.ToggleFollowed) }, showBottomDivider = state.followedExpanded)
                    if (state.followedExpanded) state.followedSeries.forEach { series ->
                        SettingsRow(label = series.title, value = stringResource(R.string.settings_v2_unfollow_hint),
                            actionButtonText = stringResource(R.string.settings_v2_unfollow),
                            onActionClick = { onEvent(SettingsEvent.Unfollow(series.key)) })
                    }
                }
                // Groupe 4 : Réseau
                SettingsGroup(title = stringResource(R.string.settings_v2_sends)) {
                    SettingsRow(
                        label = stringResource(R.string.settings_row_wifi_only),
                        value = if (state.wifiOnly) {
                            stringResource(R.string.settings_wifi_only_on_desc)
                        } else {
                            stringResource(R.string.settings_wifi_only_off_desc)
                        },
                        isToggle = true,
                        toggleChecked = state.wifiOnly,
                        onToggleChange = { onEvent(SettingsEvent.SetWifiOnly(it)) },
                        showBottomDivider = false,
                    )
                }

                // Groupe 2 : Mihon
                SettingsGroup(title = stringResource(R.string.settings_group_mihon)) {
                    SettingsRow(
                        label = stringResource(R.string.settings_row_mihon_folder),
                        value = state.mihonFolder,
                        isMono = true,
                        actionButtonText = stringResource(R.string.settings_action_change),
                        onActionClick = { onEvent(SettingsEvent.ChangeMihonFolder) },
                    )
                    SettingsRow(
                        label = stringResource(R.string.settings_v2_backup),
                        value = listOf(state.lastBackupInfo, state.backupFileName).filter { it.isNotEmpty() }.joinToString(" · "),
                        actionButtonText = stringResource(R.string.settings_v2_reread),
                        actionEnabled = state.importStep == BackupImportStep.Idle,
                        onActionClick = { onEvent(SettingsEvent.Reimport) },
                    )
                    if (state.importStep != BackupImportStep.Idle || state.importError) {
                        TmmText(
                            text = stringResource(when (state.importStep) {
                                BackupImportStep.Idle -> R.string.settings_v2_import_error
                                BackupImportStep.ReadingFile -> R.string.settings_v2_step_reading
                                BackupImportStep.Decompressing -> R.string.settings_v2_step_decompressing
                                BackupImportStep.ReadingSeries -> R.string.settings_v2_step_series
                                BackupImportStep.ReadingCategories -> R.string.settings_v2_step_categories
                                BackupImportStep.Comparing -> R.string.settings_v2_step_comparing
                            }),
                            style = TmmType.sans(12), color = TmmColors.textMuted,
                            modifier = Modifier.padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                SettingsGroup(title = stringResource(R.string.settings_v2_account)) {
                    SettingsRow(label = stringResource(R.string.settings_group_drive), value = state.googleAccount,
                        actionButtonText = stringResource(R.string.settings_action_change),
                        onActionClick = { onEvent(SettingsEvent.ChangeGoogleAccount) }, showBottomDivider = false)
                }
                SettingsGroup(title = stringResource(R.string.settings_update_group)) {
                    UpdateRow(state.update, onEvent)
                }
                // Avancé : carte repliable (details de la maquette v2), une seule liste une fois dépliée.
                AdvancedCard(expanded = state.advancedExpanded, onToggle = { onEvent(SettingsEvent.ToggleAdvanced) })
                if (state.advancedExpanded) {
                    SettingsList {
                        // Connexion directe (Tailscale) : ligne a chevron vers son sous-ecran (maquette v2).
                        SettingsRow(
                            label = stringResource(R.string.settings_v2_direct_row),
                            value = directSummary(state),
                            isLink = true,
                            onRowClick = { onEvent(SettingsEvent.OpenDirect) },
                        )

                        // Conservation sur Drive : toute la ligne fait defiler les trois choix.
                        SettingsRow(
                            label = stringResource(R.string.settings_row_drive_retention),
                            value = when (state.driveRetention) {
                                DriveRetention.Never -> stringResource(R.string.settings_retention_never)
                                DriveRetention.Days30 -> stringResource(R.string.settings_retention_30_days)
                                DriveRetention.AfterDownload -> stringResource(R.string.settings_retention_after_download)
                            },
                            isLink = true,
                            onRowClick = { onEvent(SettingsEvent.CycleDriveRetention) },
                        )
                        SettingsRow(
                            label = stringResource(R.string.settings_v2_drive_folder),
                            value = state.sharedFolder,
                            isMono = true,
                        )
                        SettingsRow(
                            label = stringResource(R.string.settings_row_reinstall),
                            value = stringResource(R.string.settings_row_reinstall_desc),
                            isLink = true,
                            onRowClick = onNavigateOnboarding,
                        )
                        SettingsRow(
                            label = stringResource(R.string.settings_row_logs),
                            value = state.logsInfo,
                            actionButtonText = stringResource(R.string.settings_action_export),
                            onActionClick = { onEvent(SettingsEvent.ExportLogs) },
                            showBottomDivider = false,
                        )
                    }
                }

                // Footer version
                TmmText(
                    text = stringResource(R.string.settings_footer_app_version, BuildConfig.VERSION_NAME),
                    style = TmmType.mono(12),
                    color = TmmColors.C9C9486,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                )
            }
        }

        SettingsToastHost(state, onEvent, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SettingsToastHost(state: SettingsState, onEvent: (SettingsEvent) -> Unit, modifier: Modifier) {
    val toastMessage = when (state.toast) {
        SettingsToast.ChooseAccount -> stringResource(R.string.settings_toast_choose_account)
        SettingsToast.FolderPicker -> stringResource(R.string.settings_toast_folder_picker)
        SettingsToast.LogsReady -> stringResource(R.string.settings_toast_logs_ready)
        SettingsToast.Paired -> stringResource(R.string.settings_toast_paired)
        SettingsToast.PairFailed -> stringResource(R.string.settings_toast_pair_failed)
        SettingsToast.UpdateFailed -> stringResource(R.string.settings_toast_update_failed)
        null -> ""
    }
    TmmToast(
        visible = state.toast != null,
        message = toastMessage,
        onDismiss = { onEvent(SettingsEvent.DismissToast) },
        modifier = modifier.padding(16.dp),
    )
}

// Mise a jour depuis GitHub : une ligne, son bouton suit l'etat (verifier, installer, progression).
@Composable
private fun UpdateRow(update: AppUpdateState, onEvent: (SettingsEvent) -> Unit) {
    val value = when (update) {
        AppUpdateState.Checking -> stringResource(R.string.settings_update_checking)
        AppUpdateState.UpToDate -> stringResource(R.string.settings_update_up_to_date)
        AppUpdateState.Failed -> stringResource(R.string.settings_update_failed)
        is AppUpdateState.Available -> stringResource(R.string.settings_update_available_fmt, update.release.version)
        is AppUpdateState.Downloading -> stringResource(R.string.settings_update_downloading_fmt, update.release.version, update.percent)
        is AppUpdateState.Ready -> stringResource(R.string.settings_update_available_fmt, update.release.version)
    }
    val action = when (update) {
        AppUpdateState.Checking, is AppUpdateState.Downloading -> null
        AppUpdateState.UpToDate, AppUpdateState.Failed -> stringResource(R.string.settings_update_check)
        is AppUpdateState.Available, is AppUpdateState.Ready -> stringResource(R.string.settings_update_install)
    }
    SettingsRow(
        label = stringResource(R.string.settings_update_version_fmt, BuildConfig.VERSION_NAME),
        value = value,
        actionButtonText = action,
        onActionClick = {
            onEvent(if (update is AppUpdateState.Available || update is AppUpdateState.Ready) SettingsEvent.InstallUpdate else SettingsEvent.CheckUpdate)
        },
        showBottomDivider = false,
    )
}

@Composable
private fun directSummary(state: SettingsState): String = when (state.directStatus) {
    DirectStatus.Direct -> stringResource(R.string.settings_v2_direct_paired_fmt, state.directUrl)
    DirectStatus.Unreachable -> stringResource(R.string.settings_direct_unreachable)
    DirectStatus.NotPaired -> stringResource(R.string.settings_direct_unpaired)
    DirectStatus.Expired -> stringResource(R.string.settings_direct_expired)
}

/**
 * Sous-ecran « Connexion directe (Tailscale) » : Hub VPS (prioritaire) puis PC en secours, chacun avec
 * son adresse, son code d'appairage genere par l'app Windows, et « Oublier ce PC » une fois associe. Rendu par SettingsScreen (pas de route dediee) :
 * le retour systeme le referme via BackHandler.
 */
@Composable
private fun DirectConnectionPage(state: SettingsState, onEvent: (SettingsEvent) -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onEvent(SettingsEvent.CloseDirect) }
    val backLabel = stringResource(R.string.settings_title)
    Box(modifier = modifier.fillMaxSize().background(TmmColors.paper)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = backLabel) { onEvent(SettingsEvent.CloseDirect) }
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TmmIcon(icon = TmmIconId.ChevronLeft, size = 22.dp, tint = TmmColors.textMuted)
                TmmText(text = backLabel, style = TmmType.sans(15), color = TmmColors.textMuted)
            }
            TmmText(
                text = stringResource(R.string.settings_v2_direct_row),
                style = TmmType.display(30, weight = 800),
                color = TmmColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            TmmText(
                text = directSummary(state),
                style = TmmType.sans(14),
                color = TmmColors.textMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            SettingsList {
                DirectPairingBlock(
                    status = state.directStatus,
                    url = state.directUrl,
                    code = state.pairingCode,
                    pairing = state.pairing,
                    onUrlChange = { onEvent(SettingsEvent.DirectUrlChanged(it)) },
                    onCodeChange = { onEvent(SettingsEvent.PairingCodeChanged(it)) },
                    onPair = { onEvent(SettingsEvent.Pair) },
                    onForget = { onEvent(SettingsEvent.ForgetPc) },
                )
            }
            TmmText(
                text = stringResource(R.string.settings_v2_direct_explain),
                style = TmmType.sans(12, lineHeight = 18f),
                color = TmmColors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            // PC en secours : meme bloc d'appairage, utilise quand le Hub ne repond pas ou est en retard.
            SettingsGroup(title = stringResource(R.string.settings_direct_pc_group)) {
                DirectPairingBlock(
                    status = state.pcDirectStatus,
                    url = state.pcDirectUrl,
                    code = state.pcPairingCode,
                    pairing = state.pcPairing,
                    onUrlChange = { onEvent(SettingsEvent.PcDirectUrlChanged(it)) },
                    onCodeChange = { onEvent(SettingsEvent.PcPairingCodeChanged(it)) },
                    onPair = { onEvent(SettingsEvent.PairPc) },
                    onForget = { onEvent(SettingsEvent.ForgetBackupPc) },
                )
            }
            TmmText(
                text = pcDirectSummary(state),
                style = TmmType.sans(12, lineHeight = 18f),
                color = TmmColors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        SettingsToastHost(state, onEvent, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun pcDirectSummary(state: SettingsState): String = when (state.pcDirectStatus) {
    DirectStatus.Direct -> stringResource(R.string.settings_direct_pc_paired_fmt, state.pcDirectUrl)
    DirectStatus.Unreachable -> stringResource(R.string.settings_direct_pc_unreachable)
    DirectStatus.NotPaired -> stringResource(R.string.settings_direct_pc_unpaired)
    DirectStatus.Expired -> stringResource(R.string.settings_direct_expired)
}

/**
 * Appairage d'un emplacement direct (Hub ou PC en secours) : associe, l'adresse et « Oublier ce PC » ;
 * sinon adresse, code a 6 chiffres et « Associer ».
 */
@Composable
private fun DirectPairingBlock(
    status: DirectStatus,
    url: String,
    code: String,
    pairing: Boolean,
    onUrlChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    onForget: () -> Unit,
) {
    val paired = status == DirectStatus.Direct || status == DirectStatus.Unreachable
    if (paired) {
        SettingsRow(
            label = stringResource(R.string.settings_row_direct_url),
            value = url,
            isMono = true,
            actionButtonText = stringResource(R.string.settings_action_forget),
            onActionClick = onForget,
            showBottomDivider = false,
        )
    } else {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = stringResource(R.string.settings_row_direct_url),
                keyboardType = KeyboardType.Uri,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    placeholder = stringResource(R.string.settings_direct_code_hint),
                    keyboardType = KeyboardType.NumberPassword,
                    modifier = Modifier.weight(1f),
                )
                val enabled = !pairing && code.length == 6 && url.isNotBlank()
                val pairLabel = stringResource(R.string.settings_action_pair)
                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (enabled) TmmColors.ink else TmmColors.track)
                        .clickable(enabled = enabled, role = Role.Button, onClickLabel = pairLabel) {
                            onPair()
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(
                        text = pairLabel,
                        style = TmmType.sans(14, weight = 600),
                        color = if (enabled) TmmColors.paper else TmmColors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TmmText(
            text = title.uppercase(),
            style = TmmType.sans(12, weight = 600, letterSpacingEm = 0.06f),
            color = TmmColors.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
                .background(TmmColors.surface),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsList(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
            .background(TmmColors.surface),
    ) {
        content()
    }
}

// Resume « Avancé » : titre gras, rappel du contenu et chevron qui pivote une fois deplie.
@Composable
private fun AdvancedCard(expanded: Boolean, onToggle: () -> Unit) {
    val title = stringResource(R.string.settings_v2_advanced)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp))
            .background(TmmColors.surface)
            .clickable(role = Role.Button, onClickLabel = title, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TmmText(text = title, style = TmmType.sans(15, weight = 700), color = TmmColors.ink)
            TmmText(
                text = stringResource(R.string.settings_v2_advanced_hint),
                style = TmmType.sans(13),
                color = TmmColors.textMuted,
            )
        }
        TmmIcon(
            icon = TmmIconId.ChevronDown,
            size = 16.dp,
            tint = TmmColors.C9C9486,
            modifier = Modifier.rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TmmColors.paperRaised)
            .border(1.dp, TmmColors.track, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            TmmText(text = placeholder, style = TmmType.sans(14), color = TmmColors.textMuted)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TmmType.mono(14).copy(color = TmmColors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = placeholder },
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isMono: Boolean = false,
    showBottomDivider: Boolean = true,
    actionButtonText: String? = null,
    actionEnabled: Boolean = true,
    onActionClick: (() -> Unit)? = null,
    isToggle: Boolean = false,
    toggleChecked: Boolean = false,
    onToggleChange: ((Boolean) -> Unit)? = null,
    isLink: Boolean = false,
    onRowClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (isLink && onRowClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = label,
            onClick = onRowClick,
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickableModifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TmmText(
                    text = label,
                    style = TmmType.sans(14, weight = 500),
                    color = TmmColors.ink,
                )
                TmmText(
                    text = value,
                    style = if (isMono) TmmType.mono(12) else TmmType.sans(12),
                    color = TmmColors.textMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (actionButtonText != null) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, TmmColors.CD5CDBC, RoundedCornerShape(9.dp))
                        .background(TmmColors.paperRaised)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = actionButtonText,
                            enabled = actionEnabled,
                            onClick = { onActionClick?.invoke() },
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmText(
                        text = actionButtonText,
                        style = TmmType.sans(13, weight = 600),
                        color = TmmColors.ink,
                    )
                }
            } else if (isToggle && onToggleChange != null) {
                TmmSwitch(
                    checked = toggleChecked,
                    onCheckedChange = onToggleChange,
                )
            } else if (isLink) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmIcon(
                        icon = TmmIconId.ChevronRight,
                        size = 16.dp,
                        tint = TmmColors.C9C9486,
                    )
                }
            }
        }

        if (showBottomDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TmmColors.line),
            )
        }
    }
}

@Preview(name = "Settings Screen - Default", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenDefaultPreview() {
    TmmTheme {
        SettingsScreen(
            state = MockupData.settings,
            onEvent = {},
        )
    }
}

@Preview(name = "Settings Screen - Wifi Only On", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenWifiOnlyPreview() {
    TmmTheme {
        SettingsScreen(
            state = MockupData.settings.copy(wifiOnly = true),
            onEvent = {},
        )
    }
}

@Preview(name = "Settings Screen - Toast Active", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenToastPreview() {
    TmmTheme {
        SettingsScreen(
            state = MockupData.settings.copy(toast = SettingsToast.ChooseAccount),
            onEvent = {},
        )
    }
}
