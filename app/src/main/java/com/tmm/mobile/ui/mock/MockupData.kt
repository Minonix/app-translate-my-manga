package com.tmm.mobile.ui.mock

import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.screens.library.LibraryState
import com.tmm.mobile.ui.screens.main.MainJobType
import com.tmm.mobile.ui.screens.main.MainQueueJob
import com.tmm.mobile.ui.screens.main.MainRecentChapter
import com.tmm.mobile.ui.screens.main.MainState
import com.tmm.mobile.ui.screens.main.PcTone
import com.tmm.mobile.ui.screens.series.SeriesChapter
import com.tmm.mobile.ui.screens.series.SeriesChapterStats
import com.tmm.mobile.ui.screens.series.SeriesState
import com.tmm.mobile.ui.screens.queue.ActiveJob
import com.tmm.mobile.ui.screens.queue.DoneJob
import com.tmm.mobile.ui.screens.queue.DoneJobAlert
import com.tmm.mobile.ui.screens.queue.FailedJob
import com.tmm.mobile.ui.screens.queue.QueueJobType
import com.tmm.mobile.ui.screens.queue.QueueState
import com.tmm.mobile.ui.screens.queue.QueueTab
import com.tmm.mobile.ui.screens.queue.WaitingJob
import com.tmm.mobile.ui.screens.catalog.CatalogState
import com.tmm.mobile.ui.screens.empty.EmptyState
import com.tmm.mobile.ui.screens.newtranslation.NewTranslationState
import com.tmm.mobile.ui.screens.onboarding.OnboardingState
import com.tmm.mobile.ui.screens.settings.SettingsState
import com.tmm.mobile.ui.screens.translationsettings.TranslationSettingsState
import com.tmm.mobile.ui.screens.transfers.LiveTransferItem
import com.tmm.mobile.ui.screens.transfers.SyncFetchItem
import com.tmm.mobile.ui.screens.transfers.SyncSeriesItem
import com.tmm.mobile.ui.screens.transfers.SyncState
import com.tmm.mobile.ui.theme.TmmColors

/**
 * Jeu de donnees de demo recopie tel quel depuis Library.dc.html (const all),
 * utilise pour les @Preview et les captures Roborazzi.
 */
data class MockSeries(
    val title: String,
    val colorHex: String,
    val category: String,
    val frChapters: Int,
    val totalChapters: Int,
    val toSend: Int,
    val toGet: Int,
    val badge: String,
    val tone: StatusTone,
)

object MockupData {
    val series = listOf(
        MockSeries("Blue Lock", "#D8D2E4", "En cours", 205, 214, 1, 1, "1 à envoyer", StatusTone.Send),
        MockSeries("Solo Leveling", "#C9D3DE", "En cours", 200, 201, 0, 0, "Traduction en cours", StatusTone.Busy),
        MockSeries("Frieren", "#E2C6BB", "En cours", 140, 140, 0, 1, "1 à récupérer", StatusTone.Get),
        MockSeries("Kagurabachi", "#CFD8C6", "En cours", 50, 54, 3, 0, "3 à envoyer", StatusTone.Send),
        MockSeries("Dandadan", "#F4F1EA", "À lire", 0, 0, 0, 0, "Non téléchargée", StatusTone.Absent),
        MockSeries("One Piece", "#E6DCC0", "En cours", 1120, 1122, 0, 2, "2 à récupérer", StatusTone.Get),
        MockSeries("Vinland Saga", "#DCCFC4", "Terminé", 214, 214, 0, 0, "À jour", StatusTone.Ok),
        MockSeries("Sakamoto Days", "#C9D3DE", "À lire", 0, 12, 12, 0, "12 à envoyer", StatusTone.Send),
        MockSeries("Chainsaw Man", "#E2C6BB", "Terminé", 97, 97, 0, 0, "À jour", StatusTone.Ok),
    )

    /** Library.dc.html, onglets de categories Mihon (nom, nombre total demo). */
    val libraryCategories = listOf(
        "Tous" to 142,
        "En cours" to 24,
        "À lire" to 31,
        "Terminé" to 58,
        "Favoris" to 12,
        "Webtoons" to 9,
    )

    /** Main.dc.html, etat initial (page=0, animations des compteurs deja terminees). */
    val main = MainState(
        pcTone = PcTone.Online,
        lastCheckLabel = "il y a 4 min",
        toSendCount = 12,
        toSendSummary = "5 séries · environ 180 Mo",
        toGetCount = 8,
        toGetSummary = "4 séries · vers Mihon/local",
        queueCount = 7,
        queueParallelCount = 4,
        queueMachineName = "PC-BUREAU",
        queueNextCount = 3,
        queueJobs = listOf(
            MainQueueJob("Solo Leveling · 201", TmmColors.CC9D3DE, MainJobType.Translation, 0.64f),
            MainQueueJob("Kagurabachi · 52", TmmColors.CCFD8C6, MainJobType.Translation, 0.26f),
            MainQueueJob("One Piece · 1119", TmmColors.CE6DCC0, MainJobType.Verification, 0.53f),
            MainQueueJob("Blue Lock · 205", TmmColors.CD8D2E4, MainJobType.Retranslation, 0.08f),
        ),
        lastScanLabel = "Dernier scan il y a 4 min",
        recentChapters = listOf(
            MainRecentChapter("Frieren", "Ch. 140", TmmColors.CE2C6BB, inMihon = false, onPc = true),
            MainRecentChapter("Kagurabachi", "Ch. 51 à 54", TmmColors.CCFD8C6, inMihon = true, onPc = false),
            MainRecentChapter("Blue Lock", "Ch. 212", TmmColors.CD8D2E4, inMihon = false, onPc = true),
            MainRecentChapter("One Piece", "Ch. 1120", TmmColors.CE6DCC0, inMihon = true, onPc = false),
        ),
    )

    /** Library.dc.html, etat initial (aucune categorie/vue/selection active). */
    val library = LibraryState(
        categories = libraryCategories,
        allSeries = series,
    )

    /** Series.dc.html, etat initial (detail de Blue Lock, chapitres recopies tel quel depuis le JS de la maquette). */
    val seriesDetail = SeriesState(
        title = "Blue Lock",
        categoryLabel = "En cours",
        formatLabel = "Manga · anglais vers français",
        coverColor = TmmColors.CD8D2E4,
        onPhoneCount = 212,
        onPcCount = 213,
        frCount = 205,
        totalChapters = 214,
        chapters = listOf(
            SeriesChapter(214, StatusTone.Send, "Sur le téléphone · à envoyer", TmmColors.dangerText, onPhone = true, onPc = false, onFr = false),
            SeriesChapter(213, StatusTone.Busy, "Traduction en cours · 45 %", TmmColors.ink, onPhone = false, onPc = true, onFr = false, translationProgress = 0.45f),
            SeriesChapter(
                212, StatusTone.Get, "Traduit · à récupérer", TmmColors.info, onPhone = true, onPc = true, onFr = true,
                stats = SeriesChapterStats(pagesTotal = 22, pagesTranslated = 22, bubblesTranslated = 184, bubblesUntranslated = 3, sfxKept = 12, verifiedDate = null),
            ),
            SeriesChapter(
                211, StatusTone.Ok, "À jour partout", TmmColors.success, onPhone = true, onPc = true, onFr = true,
                stats = SeriesChapterStats(pagesTotal = 20, pagesTranslated = 20, bubblesTranslated = 161, bubblesUntranslated = 0, sfxKept = 8, verifiedDate = "24/09"),
            ),
            SeriesChapter(
                210, StatusTone.Ok, "À jour · 1 page restée en VO", TmmColors.absentText, onPhone = true, onPc = true, onFr = true,
                stats = SeriesChapterStats(pagesTotal = 19, pagesTranslated = 18, bubblesTranslated = 142, bubblesUntranslated = 6, sfxKept = 9, verifiedDate = null),
            ),
            SeriesChapter(
                209, StatusTone.Ok, "À jour partout", TmmColors.success, onPhone = true, onPc = true, onFr = true,
                stats = SeriesChapterStats(pagesTotal = 21, pagesTranslated = 21, bubblesTranslated = 170, bubblesUntranslated = 0, sfxKept = 15, verifiedDate = "24/09"),
            ),
            SeriesChapter(23, StatusTone.Gap, "Manquant partout", TmmColors.dangerText, onPhone = false, onPc = false, onFr = false),
        ),
        selected = setOf(214, 212, 210),
        expandedStats = setOf(212),
    )

    /** Sync.dc.html, etat initial (Transferts). */
    val sync = SyncState(
        pcName = "PC-BUREAU",
        upCount = 12,
        downCount = 8,
        liveTransfers = listOf(
            LiveTransferItem(
                title = "Blue Lock · Ch. 214",
                info = "envoi 38 %",
                progress = 0.38f,
                isUpload = true,
            ),
        ),
        upSeries = listOf(
            SyncSeriesItem(
                title = "Kagurabachi",
                chapters = "Ch. 52 à 54",
                color = TmmColors.CCFD8C6,
                size = "41 Mo",
            ),
            SyncSeriesItem(
                title = "Blue Lock",
                chapters = "Ch. 214",
                color = TmmColors.CD8D2E4,
                size = "13 Mo",
            ),
            SyncSeriesItem(
                title = "Sakamoto Days",
                chapters = "Ch. 1 à 12",
                color = TmmColors.CC9D3DE,
                size = "126 Mo",
            ),
        ),
        otherSeriesCount = 3,
        otherChaptersCount = 8,
        autoTranslate = true,
        downSeries = listOf(
            SyncFetchItem(
                title = "Frieren",
                chapters = "Ch. 140",
                color = TmmColors.CE2C6BB,
                size = "14 Mo",
                isDone = false,
            ),
            SyncFetchItem(
                title = "One Piece",
                chapters = "Ch. 1121 à 1122",
                color = TmmColors.CE6DCC0,
                size = "31 Mo",
                isDone = false,
            ),
            SyncFetchItem(
                title = "Blue Lock",
                chapters = "Ch. 212",
                color = TmmColors.CD8D2E4,
                size = "12 Mo",
                isDone = false,
            ),
        ),
        scanning = false,
        scannedAgo = "il y a 4 min",
        missingCount = 2,
    )

    val queue = QueueState(
        activeTab = QueueTab.Active,
        slotsCount = 4,
        slotsMax = 4,
        pcName = "PC-BUREAU",
        focusedJobIndex = 0,
        activeJobs = listOf(
            ActiveJob(
                title = "Solo Leveling · Ch. 201",
                color = TmmColors.CC9D3DE,
                type = QueueJobType.Translation,
                currentPage = 14,
                totalPages = 22,
                currentStepIndex = 3,
                extra = "Gemini",
            ),
            ActiveJob(
                title = "Kagurabachi · Ch. 52",
                color = TmmColors.CCFD8C6,
                type = QueueJobType.Translation,
                currentPage = 6,
                totalPages = 19,
                currentStepIndex = 2,
                extra = "Gemini",
            ),
            ActiveJob(
                title = "One Piece · Ch. 1119",
                color = TmmColors.CE6DCC0,
                type = QueueJobType.Verification,
                currentPage = 11,
                totalPages = 17,
                currentStepIndex = 4,
                extra = "7 bulles à revoir",
            ),
            ActiveJob(
                title = "Blue Lock · Ch. 205",
                color = TmmColors.CD8D2E4,
                type = QueueJobType.Retranslation,
                currentPage = 3,
                totalPages = 24,
                currentStepIndex = 1,
                extra = "Gemini",
            ),
        ),
        waitingJobs = listOf(
            WaitingJob(
                id = "k53",
                position = "05",
                title = "Kagurabachi · Ch. 53",
                color = TmmColors.CCFD8C6,
                source = "Traduction · depuis le téléphone",
                isPhone = true,
            ),
            WaitingJob(
                id = "b213",
                position = "06",
                title = "Blue Lock · Ch. 213",
                color = TmmColors.CD8D2E4,
                source = "Traduction · original déjà sur le PC",
                isPhone = false,
            ),
            WaitingJob(
                id = "v1",
                position = "07",
                title = "Vinland Saga · 214 ch.",
                color = TmmColors.CDCCFC4,
                source = "Vérification · série entière",
                isPhone = false,
            ),
        ),
        doneJobs = listOf(
            DoneJob(
                title = "Frieren · Ch. 140",
                whenText = "Aujourd’hui 14:02",
                stateText = "À récupérer",
                stateColor = TmmColors.C2B4C7E,
                color = TmmColors.CE2C6BB,
                totalPages = 18,
                translatedPages = 18,
                totalBubbles = 131,
                untranslatedBubbles = 0,
                sfxCount = 7,
                alerts = emptyList(),
                isExpanded = false,
            ),
            DoneJob(
                title = "Blue Lock · Ch. 212",
                whenText = "Aujourd’hui 13:40",
                stateText = "À récupérer",
                stateColor = TmmColors.C2B4C7E,
                color = TmmColors.CD8D2E4,
                totalPages = 22,
                translatedPages = 22,
                totalBubbles = 184,
                untranslatedBubbles = 3,
                sfxCount = 12,
                alerts = listOf(
                    DoneJobAlert(page = 7, code = "source_text"),
                    DoneJobAlert(page = 15, code = "overflow"),
                ),
                isExpanded = true,
            ),
            DoneJob(
                title = "One Piece · Ch. 1122",
                whenText = "Aujourd’hui 11:15",
                stateText = "À récupérer",
                stateColor = TmmColors.C2B4C7E,
                color = TmmColors.CE6DCC0,
                totalPages = 17,
                translatedPages = 17,
                totalBubbles = 203,
                untranslatedBubbles = 0,
                sfxCount = 21,
                alerts = emptyList(),
                isExpanded = false,
            ),
            DoneJob(
                title = "Kagurabachi · Ch. 51",
                whenText = "Hier 22:10",
                stateText = "Dans Mihon",
                stateColor = TmmColors.C2F7D4F,
                color = TmmColors.CCFD8C6,
                totalPages = 19,
                translatedPages = 18,
                totalBubbles = 142,
                untranslatedBubbles = 6,
                sfxCount = 9,
                alerts = listOf(
                    DoneJobAlert(page = 11, code = "source_copy"),
                ),
                isExpanded = false,
            ),
            DoneJob(
                title = "Solo Leveling · Ch. 200",
                whenText = "Hier 21:48",
                stateText = "Dans Mihon",
                stateColor = TmmColors.C2F7D4F,
                color = TmmColors.CC9D3DE,
                totalPages = 21,
                translatedPages = 21,
                totalBubbles = 170,
                untranslatedBubbles = 0,
                sfxCount = 15,
                alerts = emptyList(),
                isExpanded = false,
            ),
        ),
        failedJob = FailedJob(
            title = "Frieren · Ch. 139",
            color = TmmColors.CE2C6BB,
            page = 9,
            message = "moteur indisponible",
            keptInfo = "Il y a 20 min · les pages 1 à 8 sont conservées",
        ),
        isRetried = false,
    )

    val catalog = CatalogState()

    val newTranslation = NewTranslationState()

    val settings = SettingsState()

    val translationSettings = TranslationSettingsState()

    val onboarding = OnboardingState()

    val empty = EmptyState()
}
