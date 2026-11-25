package com.tmm.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.data.CleanupReport
import com.tmm.mobile.data.PcCleanupState
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.V2Button
import com.tmm.mobile.ui.components.V2ButtonStyle
import com.tmm.mobile.ui.components.V2IconButton
import com.tmm.mobile.ui.components.V2Sheet
import com.tmm.mobile.ui.components.px
import com.tmm.mobile.ui.components.V2SheetHeader
import com.tmm.mobile.ui.components.v2CardItem
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmType

private val C = TmmColors.V2

/** Feuille « Nettoyer » (Reglages, Maintenance) : apercu des comptes, confirmation, puis bilan. */
@Composable
internal fun CleanupSheetHost(sheet: CleanupSheet?, onEvent: (SettingsEvent) -> Unit) {
    val close = { onEvent(SettingsEvent.CloseCleanup) }
    val title = stringResource(
        when (sheet) {
            is CleanupSheet.Preview -> R.string.cleanup_title_preview
            CleanupSheet.Running -> R.string.cleanup_title_running
            is CleanupSheet.Done -> R.string.cleanup_title_done
            else -> R.string.cleanup_title_scanning
        },
    )
    V2Sheet(visible = sheet != null, onDismiss = close, paneTitle = title) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { V2SheetHeader(kick = stringResource(R.string.cleanup_kick), title = title) }
            if (sheet != CleanupSheet.Running) V2IconButton(TmmIconId.V2X, stringResource(R.string.cleanup_close_cd), close)
        }
        val report = when (sheet) {
            is CleanupSheet.Preview -> sheet.report
            is CleanupSheet.Done -> sheet.report
            else -> null
        }
        if (report != null) ReportLines(report)
        if (sheet is CleanupSheet.Preview) {
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.blueSoft)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TmmIcon(TmmIconId.V2Info, size = 20.dp, tint = C.blue)
                TmmText(
                    text = stringResource(R.string.cleanup_warning),
                    style = TmmType.sans(13).px(13.5f, lineHeight = 1.4f),
                    color = C.blue,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.padding(top = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V2Button(
                    label = stringResource(R.string.common_cancel),
                    onClick = close,
                    modifier = Modifier.weight(1f),
                    style = V2ButtonStyle.Ghost,
                )
                V2Button(
                    label = stringResource(R.string.cleanup_confirm),
                    onClick = { onEvent(SettingsEvent.ConfirmCleanup) },
                    modifier = Modifier.weight(1f),
                    icon = TmmIconId.V2Trash,
                )
            }
        }
        if (sheet is CleanupSheet.Done) {
            V2Button(
                label = stringResource(R.string.cleanup_done),
                onClick = close,
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth(),
                style = V2ButtonStyle.Ink,
            )
        }
    }
}

@Composable
private fun ReportLines(report: CleanupReport) {
    val phone = report.phone
    val phoneLines = if (phone == null) {
        listOf(stringResource(R.string.cleanup_phone_unavailable))
    } else {
        buildList {
            add(pluralStringResource(R.plurals.cleanup_tmp_count, phone.tmpRemoved, phone.tmpRemoved))
            val fixed = phone.duplicatesRenamed + phone.duplicatesRemoved
            add(pluralStringResource(R.plurals.cleanup_duplicates_count, fixed, fixed))
            if (phone.duplicatesToCheck > 0) {
                add(pluralStringResource(R.plurals.cleanup_to_check_count, phone.duplicatesToCheck, phone.duplicatesToCheck))
            }
            if (phone.errors > 0) add(pluralStringResource(R.plurals.cleanup_errors_count, phone.errors, phone.errors))
        }
    }
    val drive = report.drive
    val driveLines = buildList {
        if (drive.unavailable) {
            add(stringResource(R.string.cleanup_drive_unavailable))
        } else {
            add(pluralStringResource(R.plurals.cleanup_drive_count, drive.folders, drive.folders))
        }
        if (drive.failed > 0) add(pluralStringResource(R.plurals.cleanup_errors_count, drive.failed, drive.failed))
    }
    val pc = report.pc
    val pcLine = when (pc.state) {
        PcCleanupState.WILL_ASK -> stringResource(R.string.cleanup_pc_will_ask)
        PcCleanupState.NEVER_SEEN -> stringResource(R.string.cleanup_pc_never_seen)
        PcCleanupState.WAITING -> stringResource(R.string.cleanup_pc_waiting)
        PcCleanupState.DONE -> (pc.forgotten ?: 0).let { pluralStringResource(R.plurals.cleanup_pc_done, it, it) }
        PcCleanupState.OUTDATED -> stringResource(R.string.cleanup_pc_outdated)
        PcCleanupState.REJECTED -> stringResource(R.string.cleanup_pc_rejected)
    }
    Column(Modifier.padding(top = 16.dp)) {
        Section(stringResource(R.string.cleanup_section_phone), phoneLines, isFirst = true, isLast = false)
        Section(stringResource(R.string.cleanup_section_drive), driveLines, isFirst = false, isLast = false)
        Section(stringResource(R.string.cleanup_section_pc), listOf(pcLine), isFirst = false, isLast = true)
    }
}

@Composable
private fun Section(label: String, lines: List<String>, isFirst: Boolean, isLast: Boolean) {
    Column(
        Modifier
            .v2CardItem(isFirst = isFirst, isLast = isLast)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        TmmText(text = label, style = TmmType.sans(15, weight = 600), color = C.ink)
        lines.forEach { line ->
            TmmText(
                text = line,
                style = TmmType.sans(13).px(13.5f),
                color = C.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
