package com.tmm.mobile.ui.screens.catalog

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tmm.mobile.R
import com.tmm.mobile.ui.components.TmmText
import com.tmm.mobile.ui.components.dashedBorder
import com.tmm.mobile.ui.icons.TmmIcon
import com.tmm.mobile.ui.icons.TmmIconId
import com.tmm.mobile.ui.mock.MockupData
import com.tmm.mobile.ui.theme.TmmColors
import com.tmm.mobile.ui.theme.TmmTheme
import com.tmm.mobile.ui.theme.TmmType
import com.tmm.mobile.ui.theme.halftone

/**
 * Écran Catalogue (Catalog.dc.html, E4.6).
 * Présente l'état de la synchronisation avec la sauvegarde automatique Mihon (.tachibk),
 * les métriques globales et l'arborescence des catégories en lecture seule.
 */
@Composable
fun CatalogScreen(
    state: CatalogState,
    onEvent: (CatalogEvent) -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenCategory: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TmmColors.paper),
    ) {
        CatalogHeader()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CatalogBackupCard(state = state, onEvent = onEvent)
            CatalogStatsSection(state = state, onOpenLibrary = onOpenLibrary)
            CatalogCategoriesSection(state = state, onOpenCategory = onOpenCategory)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CatalogHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TmmText(
            text = stringResource(R.string.catalog_title),
            style = TmmType.display(36, weight = 800),
            color = TmmColors.ink,
            modifier = Modifier.semantics { heading() },
        )
        TmmText(
            text = stringResource(R.string.catalog_subtitle),
            style = TmmType.sans(13),
            color = TmmColors.textMuted,
        )
    }
}

@Composable
private fun CatalogBackupCard(
    state: CatalogState,
    onEvent: (CatalogEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TmmColors.C16161A),
    ) {
        // Decorative tone circle top-right
        Box(
            modifier = Modifier
                .size(140.dp)
                .offset(x = 30.dp, y = (-30).dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(TmmColors.CC8391F.copy(alpha = 0.25f))
                .halftone(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // File icon + file title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TmmColors.C2E2D29),
                    contentAlignment = Alignment.Center,
                ) {
                    TmmIcon(
                        icon = TmmIconId.File,
                        tint = TmmColors.CF4F1EA,
                        size = 20.dp,
                        strokeWidth = 1.8f,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TmmText(
                        text = stringResource(R.string.catalog_backup_label),
                        style = TmmType.sans(12),
                        color = TmmColors.textOnInkMuted,
                    )
                    TmmText(
                        text = state.backupFileName,
                        style = TmmType.mono(13),
                        color = TmmColors.CF4F1EA,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TmmIcon(
                    icon = TmmIconId.CheckSimple,
                    tint = TmmColors.C9BD3AE,
                    size = 16.dp,
                    strokeWidth = 2.4f,
                )
                TmmText(
                    text = stringResource(R.string.catalog_imported_fmt, state.importedWhen),
                    style = TmmType.sans(13),
                    color = TmmColors.textOnInkMuted2,
                )
            }

            // Progress if busy
            if (state.isBusy) {
                val stepLabel = when (state.importStep) {
                    CatalogImportStep.ReadingFile -> stringResource(R.string.catalog_step_reading_file)
                    CatalogImportStep.Decompressing -> stringResource(R.string.catalog_step_decompression)
                    CatalogImportStep.ReadingSeries -> stringResource(R.string.catalog_step_reading_series_fmt, state.totalSeries)
                    CatalogImportStep.ReadingCategories -> stringResource(R.string.catalog_step_categories)
                    CatalogImportStep.Comparing -> stringResource(R.string.catalog_step_comparison)
                    CatalogImportStep.Idle -> ""
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(TmmColors.C2E2D29),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = state.importStep.progressFraction)
                                .height(6.dp)
                                .background(TmmColors.accent),
                        )
                    }
                    TmmText(
                        text = stepLabel,
                        style = TmmType.mono(12),
                        color = TmmColors.textOnInkMuted,
                    )
                }
            }

            // Reimport button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, TmmColors.CF4F1EA, RoundedCornerShape(10.dp))
                    .clickable(
                        enabled = !state.isBusy,
                        onClick = { onEvent(CatalogEvent.Reimport) },
                        role = Role.Button,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TmmIcon(
                        icon = TmmIconId.Refresh,
                        tint = TmmColors.CF4F1EA,
                        size = 16.dp,
                        strokeWidth = 2.0f,
                    )
                    TmmText(
                        text = stringResource(R.string.catalog_reimport_button),
                        style = TmmType.sans(14, weight = 600),
                        color = TmmColors.CF4F1EA,
                    )
                }
            }

            TmmText(
                text = stringResource(R.string.catalog_backup_info),
                style = TmmType.sans(12),
                color = TmmColors.CA9A293,
            )
        }
    }
}

@Composable
private fun CatalogStatsSection(
    state: CatalogState,
    onOpenLibrary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Col 1 : Séries
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(TmmColors.surface)
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TmmText(
                    text = state.totalSeries.toString(),
                    style = TmmType.display(26, weight = 800),
                    color = TmmColors.ink,
                )
                TmmText(
                    text = stringResource(R.string.catalog_stat_series),
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                )
            }
        }

        // Col 2 : Non téléchargées (dashed border, clickable)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(TmmColors.surface)
                .dashedBorder(color = TmmColors.CB9B0A0, width = 1.dp, cornerRadius = 12.dp)
                .clickable(onClick = onOpenLibrary, role = Role.Button)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TmmText(
                    text = state.absentSeries.toString(),
                    style = TmmType.display(26, weight = 800),
                    color = TmmColors.ink,
                )
                TmmText(
                    text = stringResource(R.string.catalog_stat_absent),
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                )
            }
        }

        // Col 3 : Catégories
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(TmmColors.surface)
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TmmText(
                    text = state.totalCategories.toString(),
                    style = TmmType.display(26, weight = 800),
                    color = TmmColors.ink,
                )
                TmmText(
                    text = stringResource(R.string.catalog_stat_categories),
                    style = TmmType.sans(12),
                    color = TmmColors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun CatalogCategoriesSection(
    state: CatalogState,
    onOpenCategory: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            TmmText(
                text = stringResource(R.string.catalog_categories_title),
                style = TmmType.display(19, weight = 800),
                color = TmmColors.ink,
                modifier = Modifier.semantics { heading() },
            )
            TmmText(
                text = stringResource(R.string.catalog_readonly_hint),
                style = TmmType.sans(12),
                color = TmmColors.textMuted,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TmmColors.surface)
                .border(1.dp, TmmColors.lineStrong, RoundedCornerShape(16.dp)),
        ) {
            state.categories.forEachIndexed { i, cat ->
                val openCd = stringResource(R.string.catalog_open_category_cd_fmt, cat.name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 56.dp)
                        .clickable(
                            onClick = { onOpenCategory(cat.name) },
                            role = Role.Button,
                        )
                        .semantics { contentDescription = openCd }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TmmText(
                                text = cat.name,
                                style = TmmType.sans(14, weight = 500),
                                color = TmmColors.ink,
                            )
                            TmmText(
                                text = cat.count.toString(),
                                style = TmmType.mono(13),
                                color = TmmColors.textMuted,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TmmColors.CECE7DC),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = cat.progressFraction)
                                    .height(4.dp)
                                    .background(TmmColors.C16161A),
                            )
                        }
                    }

                    TmmIcon(
                        icon = TmmIconId.ChevronRight,
                        tint = TmmColors.C9C9486,
                        size = 16.dp,
                        strokeWidth = 2.0f,
                    )
                }
                if (i < state.categories.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TmmColors.CECE7DC),
                    )
                }
            }
        }
    }
}

@Preview(name = "Catalog Screen - Default", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CatalogScreenDefaultPreview() {
    TmmTheme {
        CatalogScreen(
            state = MockupData.catalog,
            onEvent = {},
        )
    }
}

@Preview(name = "Catalog Screen - Busy", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CatalogScreenBusyPreview() {
    TmmTheme {
        CatalogScreen(
            state = MockupData.catalog.copy(importStep = CatalogImportStep.ReadingSeries),
            onEvent = {},
        )
    }
}
