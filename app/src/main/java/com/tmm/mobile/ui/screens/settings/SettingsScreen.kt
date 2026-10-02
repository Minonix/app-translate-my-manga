package com.tmm.mobile.ui.screens.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onNavigateCatalog: () -> Unit = {},
    onNavigateOnboarding: () -> Unit = {},
    onNavigateTranslationSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pcStatusCd = stringResource(R.string.settings_pc_status_cd)
    val backCd = stringResource(R.string.settings_back_cd)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header: bouton retour
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = backCd,
                            onClick = onNavigateBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmIcon(
                        icon = TmmIconId.ChevronLeft,
                        size = 22.dp,
                        tint = TmmColors.ink,
                        contentDescription = backCd,
                    )
                }
            }

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
                                text = state.pcVersionProtocol,
                                style = TmmType.mono(12),
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
                                    if (state.isPcOnline) R.string.settings_pc_online else R.string.home_pc_offline
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

                    TmmText(
                        text = state.pcLastSignalText,
                        style = TmmType.sans(12),
                        color = TmmColors.CCFC8B8,
                    )
                }

                // Connexion directe (Tailscale) : saisie du code d'appairage genere par l'app Windows.
                SettingsGroup(title = stringResource(R.string.settings_group_direct)) {
                    val paired = state.directStatus == DirectStatus.Direct || state.directStatus == DirectStatus.Unreachable
                    SettingsRow(
                        label = stringResource(R.string.settings_row_direct_state),
                        value = stringResource(
                            when (state.directStatus) {
                                DirectStatus.Direct -> R.string.settings_direct_on
                                DirectStatus.Unreachable -> R.string.settings_direct_unreachable
                                DirectStatus.NotPaired -> R.string.settings_direct_unpaired
                                DirectStatus.Expired -> R.string.settings_direct_expired
                            },
                        ),
                    )
                    if (paired) {
                        SettingsRow(
                            label = stringResource(R.string.settings_row_direct_url),
                            value = state.directUrl,
                            isMono = true,
                            actionButtonText = stringResource(R.string.settings_action_forget),
                            onActionClick = { onEvent(SettingsEvent.ForgetPc) },
                            showBottomDivider = false,
                        )
                    } else {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SettingsTextField(
                                value = state.directUrl,
                                onValueChange = { onEvent(SettingsEvent.DirectUrlChanged(it)) },
                                placeholder = stringResource(R.string.settings_row_direct_url),
                                keyboardType = KeyboardType.Uri,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SettingsTextField(
                                    value = state.pairingCode,
                                    onValueChange = { onEvent(SettingsEvent.PairingCodeChanged(it)) },
                                    placeholder = stringResource(R.string.settings_direct_code_hint),
                                    keyboardType = KeyboardType.NumberPassword,
                                    modifier = Modifier.weight(1f),
                                )
                                val enabled = !state.pairing && state.pairingCode.length == 6 && state.directUrl.isNotBlank()
                                val pairLabel = stringResource(R.string.settings_action_pair)
                                Box(
                                    modifier = Modifier
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (enabled) TmmColors.ink else TmmColors.track)
                                        .clickable(enabled = enabled, role = Role.Button, onClickLabel = pairLabel) {
                                            onEvent(SettingsEvent.Pair)
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

                // Groupe 1 : Google Drive
                SettingsGroup(title = stringResource(R.string.settings_group_drive)) {
                    SettingsRow(
                        label = stringResource(R.string.settings_row_account),
                        value = state.googleAccount,
                        actionButtonText = stringResource(R.string.settings_action_change),
                        onActionClick = { onEvent(SettingsEvent.ChangeGoogleAccount) },
                    )
                    SettingsRow(
                        label = stringResource(R.string.settings_row_shared_folder),
                        value = state.sharedFolder,
                        isMono = true,
                    )
                    val retentionLabel = when (state.driveRetention) {
                        DriveRetention.Never -> stringResource(R.string.settings_retention_never)
                        DriveRetention.Days30 -> stringResource(R.string.settings_retention_30_days)
                        DriveRetention.AfterDownload -> stringResource(R.string.settings_retention_after_download)
                    }
                    SettingsRow(
                        label = stringResource(R.string.settings_row_drive_retention),
                        value = retentionLabel,
                        actionButtonText = stringResource(R.string.settings_action_edit),
                        onActionClick = { onEvent(SettingsEvent.CycleDriveRetention) },
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
                        label = stringResource(R.string.settings_row_last_backup),
                        value = state.lastBackupInfo,
                        isLink = true,
                        onRowClick = onNavigateCatalog,
                    )
                    SettingsRow(
                        label = stringResource(R.string.settings_row_reinstall),
                        value = stringResource(R.string.settings_row_reinstall_desc),
                        isLink = true,
                        onRowClick = onNavigateOnboarding,
                        showBottomDivider = false,
                    )
                }

                // Groupe 3 : Traduction
                SettingsGroup(title = stringResource(R.string.settings_group_translation)) {
                    SettingsRow(
                        label = stringResource(R.string.settings_row_pc_translation_settings),
                        value = state.pcTranslationSummary,
                        isLink = true,
                        onRowClick = onNavigateTranslationSettings,
                        showBottomDivider = false,
                    )
                }

                // Groupe 4 : Réseau
                SettingsGroup(title = stringResource(R.string.settings_group_network)) {
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

                // Groupe 5 : Diagnostic
                SettingsGroup(title = stringResource(R.string.settings_group_diagnostic)) {
                    SettingsRow(
                        label = stringResource(R.string.settings_row_logs),
                        value = state.logsInfo,
                        actionButtonText = stringResource(R.string.settings_action_export),
                        onActionClick = { onEvent(SettingsEvent.ExportLogs) },
                        showBottomDivider = false,
                    )
                }

                // Footer version
                TmmText(
                    text = stringResource(R.string.settings_footer_app_version),
                    style = TmmType.mono(12),
                    color = TmmColors.C9C9486,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                )
            }
        }

        // Toast bas d'écran
        val toastMessage = when (state.toast) {
            SettingsToast.ChooseAccount -> stringResource(R.string.settings_toast_choose_account)
            SettingsToast.FolderPicker -> stringResource(R.string.settings_toast_folder_picker)
            SettingsToast.LogsReady -> stringResource(R.string.settings_toast_logs_ready)
            SettingsToast.Paired -> stringResource(R.string.settings_toast_paired)
            SettingsToast.PairFailed -> stringResource(R.string.settings_toast_pair_failed)
            null -> ""
        }

        TmmToast(
            visible = state.toast != null,
            message = toastMessage,
            onDismiss = { onEvent(SettingsEvent.DismissToast) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
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
                    maxLines = 1,
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
