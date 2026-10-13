package com.tmm.mobile.ui.mock

import com.tmm.mobile.R
import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.screens.library.LibraryState
import com.tmm.mobile.ui.screens.main.MainFollowedSeries
import com.tmm.mobile.ui.screens.main.MainJobType
import com.tmm.mobile.ui.screens.main.MainQueueJob
import com.tmm.mobile.ui.screens.main.MainRecentChapter
import com.tmm.mobile.ui.screens.main.MainState
import com.tmm.mobile.ui.screens.main.PcTone
import com.tmm.mobile.ui.screens.series.ActionCounts
import com.tmm.mobile.ui.screens.series.ChapterKind
import com.tmm.mobile.ui.screens.series.SeriesChapterStats
import com.tmm.mobile.ui.screens.series.SeriesProgress
import com.tmm.mobile.ui.screens.series.SeriesRow
import com.tmm.mobile.ui.screens.series.SeriesState
import com.tmm.mobile.ui.screens.queue.ActiveJob
import com.tmm.mobile.ui.screens.queue.DoneJob
import com.tmm.mobile.ui.screens.queue.DoneJobAlert
import com.tmm.mobile.ui.screens.queue.FailedJob
import com.tmm.mobile.ui.screens.queue.QueueJobType
import com.tmm.mobile.ui.screens.queue.QueueState
import com.tmm.mobile.ui.screens.queue.QueueTab
import com.tmm.mobile.ui.screens.queue.TransferRow
import com.tmm.mobile.ui.screens.queue.WaitingJob
import com.tmm.mobile.ui.screens.empty.EmptyState
import com.tmm.mobile.ui.screens.offline.OfflineState
import com.tmm.mobile.ui.screens.permission.PermissionState
import com.tmm.mobile.ui.screens.onboarding.OnboardingState
import com.tmm.mobile.ui.screens.settings.SettingsState
import com.tmm.mobile.ui.screens.translationsettings.TranslationSettingsState
import com.tmm.mobile.ui.screens.translate.TranslateChapter
import com.tmm.mobile.ui.screens.translate.TranslateSeries
import com.tmm.mobile.ui.screens.translate.TranslateState
import com.tmm.mobile.ui.theme.TmmColors
import kotlinx.serialization.Serializable

/**
 * Jeu de donnees de demo recopie tel quel depuis Library.dc.html (const all),
 * utilise pour les @Preview et les captures Roborazzi.
 */
// Serializable : la liste de la Bibliotheque est gardee sur disque (LibraryCache) pour s'afficher des le lancement.
@Serializable
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
    // Ajoutes pour le branchement reel (E10) : la selection/recherche/filtre de LibraryState
    // restent indexes par `title` (inchange, cf. LibraryScreenBehaviorTest), seriesKey/thumbnailUrl
    // ne servent qu'a la navigation et a l'affichage de la vignette pour les vraies series.
    val seriesKey: String = title,
    val thumbnailUrl: String? = null,
    // Chapitres presents sur le telephone / originaux sur le PC : filtres "Telechargees" et "Sur le PC".
    val onPhone: Int = 0,
    val onPc: Int = 0,
    // Bouton "Traduire"/"Retraduire" (planTranslation) : cibles, dont celles deja traduites.
    val toTranslate: Int = 0,
    val toRetranslate: Int = 0,
    // Date d'ajout Mihon (SeriesEntity.dateAdded) : tri "Ajoutees recemment".
    val dateAdded: Long = 0L,
)

object MockupData {
    // La maquette compte "Retraduire" sur les chapitres FR (s.fr) : memes cibles ici.
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
    ).map { it.copy(toTranslate = it.frChapters, toRetranslate = it.frChapters) }

    /** Library.dc.html, onglets de categories Mihon (nom, nombre total demo). */
    val libraryCategories = listOf(
        "Tous" to 142,
        "En cours" to 24,
        "À lire" to 31,
        "Terminé" to 58,
        "Favoris" to 12,
        "Webtoons" to 9,
    )

    /** Maquette v2, ecran 1 (Accueil) : valeurs recopiees de maquettes/v2-simplifiee/index.html. */
    val main = MainState(
        pcTone = PcTone.Online,
        lastCheckLabel = "il y a 4 min",
        toSendCount = 11,
        toSendSeriesCount = 4,
        toGetCount = 2,
        toGetSummary = "Frieren, One Piece",
        queueCount = 7,
        queueParallelCount = 4,
        queueMachineName = "PC-BUREAU",
        queueNextCount = 5,
        queueJobs = listOf(
            MainQueueJob("Solo Leveling · ch. 201", TmmColors.CC9D3DE, MainJobType.Translation, 0.73f, page = 16, pages = 22),
            MainQueueJob("Kagurabachi · ch. 52", TmmColors.CCFD8C6, MainJobType.Translation, 0.42f, page = 9, pages = 21),
        ),
        lastScanLabel = "",
        recentChapters = listOf(
            MainRecentChapter("Frieren", "ch. 140", TmmColors.CE2C6BB, inMihon = true, onPc = false, seriesKey = "frieren"),
            MainRecentChapter("Blue Lock", "ch. 212", TmmColors.CD8D2E4, inMihon = true, onPc = false, seriesKey = "blue-lock"),
            MainRecentChapter("One Piece", "ch. 1120", TmmColors.CE6DCC0, inMihon = false, onPc = true, seriesKey = "one-piece"),
        ),
        followedSeries = listOf(
            MainFollowedSeries("blue-lock", "Blue Lock"),
            MainFollowedSeries("kagurabachi", "Kagurabachi"),
            MainFollowedSeries("one-piece", "One Piece"),
        ),
    )

    /** Library.dc.html, etat initial (aucune categorie/vue/selection active). */
    val library = LibraryState(
        categories = libraryCategories,
        allSeries = series,
    )

    /** Maquette v2, ecran 3 : fiche de Blue Lock (chapitres recopies depuis la maquette). */
    val seriesDetail = SeriesState(
        title = "Blue Lock",
        categoryLabel = "En cours",
        subtitle = "Manga · anglais vers français",
        coverColor = TmmColors.CD8D2E4,
        progress = SeriesProgress(inMihon = 205, ready = 2, busy = 1, toTranslate = 1, notDownloaded = 5, total = 214),
        rows = listOf(
            SeriesRow(214, ChapterKind.ToTranslate),
            SeriesRow(213, ChapterKind.Translating(45)),
            SeriesRow(212, ChapterKind.Ready),
            SeriesRow(
                211, ChapterKind.InMihon,
                stats = SeriesChapterStats(pagesTotal = 22, pagesTranslated = 22, bubblesTranslated = 184, bubblesUntranslated = 2, sfxKept = 5, verifiedDate = "3 oct."),
            ),
            SeriesRow(
                210, ChapterKind.InMihon,
                stats = SeriesChapterStats(pagesTotal = 20, pagesTranslated = 20, bubblesTranslated = 161, bubblesUntranslated = 0, sfxKept = 8, verifiedDate = null),
            ),
            SeriesRow(209, ChapterKind.OnPc),
            SeriesRow(208, ChapterKind.Failed(12)),
            SeriesRow(23, ChapterKind.Missing),
            SeriesRow(1, ChapterKind.NotDownloaded, rangeEnd = 4),
        ),
        missing = listOf(23),
        nextExpected = 215,
        expandedStats = setOf(211),
        seriesCounts = ActionCounts(translate = 3, install = 1, verify = 2, retranslate = 2, copy = 1),
        selectionCounts = ActionCounts(translate = 1, install = 1, verify = 1, retranslate = 1, copy = 1),
        toTranslateNumbers = listOf(214),
        selected = setOf(214, 212, 211),
        selectionToDelete = 3,
    )

    /** Maquette v2, écran 7 « Activité ». */
    val queue = QueueState(
        activeTab = QueueTab.Active,
        slotsCount = 3,
        slotsMax = 4,
        pcName = "PC-BUREAU",
        focusedJobIndex = 0,
        activeJobs = listOf(
            ActiveJob("solo", null, "Solo Leveling · ch. 201", TmmColors.CC9D3DE, QueueJobType.Translation, 16, 22, 2, "Gemini"),
            ActiveJob("kagurabachi", null, "Kagurabachi · ch. 52", TmmColors.CCFD8C6, QueueJobType.Translation, 8, 19, 1, "Gemini"),
            ActiveJob("onepiece", null, "One Piece · ch. 1119", TmmColors.CE6DCC0, QueueJobType.Verification, 13, 17, 3, "Gemini"),
        ),
        transfers = listOf(
            TransferRow("Blue Lock · ch. 214", isUpload = true, doneBytes = 98, totalBytes = 100),
            TransferRow("Frieren · ch. 140", isUpload = false, doneBytes = 3L * 1_048_576, totalBytes = 4L * 1_048_576),
        ),
        waitingJobs = listOf(
            WaitingJob("k53", "kagurabachi", null, "Kagurabachi · ch. 53", TmmColors.CCFD8C6, QueueJobType.Translation, auto = true),
            WaitingJob("bl209", "bluelock", null, "Blue Lock · ch. 209", TmmColors.CD8D2E4, QueueJobType.Translation, auto = false),
            WaitingJob("op1121", "onepiece", null, "One Piece · ch. 1121", TmmColors.CE6DCC0, QueueJobType.Verification, auto = false),
        ),
        doneJobs = listOf(
            DoneJob(
                mihonPath = "Mihon/frieren/139", seriesKey = "frieren", coverUrl = null, title = "Frieren · ch. 139",
                whenText = "Il y a 12 min", installed = true, color = TmmColors.CE2C6BB,
                totalPages = 21, translatedPages = 21, totalBubbles = 176, untranslatedBubbles = 1, sfxCount = 4,
                alerts = listOf(DoneJobAlert(4, "source_text", false), DoneJobAlert(17, "overflow", false)),
                isExpanded = true,
            ),
            DoneJob(
                mihonPath = "Mihon/bluelock/212", seriesKey = "bluelock", coverUrl = null, title = "Blue Lock · ch. 212",
                whenText = "Il y a 40 min", installed = false, color = TmmColors.CD8D2E4,
                totalPages = 19, translatedPages = 19, totalBubbles = 143, untranslatedBubbles = 0, sfxCount = 0,
                alerts = emptyList(), isExpanded = false,
            ),
            DoneJob(
                mihonPath = "Mihon/onepiece/1120", seriesKey = "onepiece", coverUrl = null, title = "One Piece · ch. 1120",
                whenText = "Hier", installed = true, color = TmmColors.CE6DCC0,
                totalPages = 17, translatedPages = 17, totalBubbles = 201, untranslatedBubbles = 0, sfxCount = 2,
                alerts = emptyList(), isExpanded = false,
            ),
            DoneJob(
                mihonPath = "Mihon/vagabond/327", seriesKey = "vagabond", coverUrl = null, title = "Vagabond · ch. 327",
                whenText = "Hier", installed = true, color = TmmColors.CDCCFC4,
                totalPages = 24, translatedPages = 24, totalBubbles = 88, untranslatedBubbles = 0, sfxCount = 0,
                alerts = emptyList(), isExpanded = false,
            ),
        ),
        failedJobs = listOf(
            FailedJob(
                jobId = "csm171", seriesKey = "chainsaw", coverUrl = null, title = "Chainsaw Man · ch. 171",
                color = TmmColors.CE2C6BB, page = 12, label = R.string.error_pc_provider_quota,
                keptInfo = "Gemini · les pages 1 à 11 sont conservées", quota = true, resumeAtPage = true,
            ),
        ),
    )

    /** Maquette v2, écran 6 : 8 chapitres cochés sur 11, Kagurabachi déplié. */
    val translate = run {
        fun ch(series: String, n: Int, pages: Int) = TranslateChapter("Mihon/$series/$n", n.toFloat(), pages)
        val series = listOf(
            TranslateSeries("sakamoto", "Sakamoto Days", null, TmmColors.CC9D3DE, (7..11).map { ch("sakamoto", it, 20) }),
            TranslateSeries("kagurabachi", "Kagurabachi", null, TmmColors.CCFD8C6, listOf(ch("kagurabachi", 52, 21), ch("kagurabachi", 53, 22), ch("kagurabachi", 54, 23))),
            TranslateSeries("blue-lock", "Blue Lock", null, TmmColors.CD8D2E4, listOf(ch("blue-lock", 214, 19))),
            TranslateSeries("kaiju", "Kaiju No. 8", null, TmmColors.CE6DCC0, listOf(ch("kaiju", 118, 22), ch("kaiju", 119, 21))),
        )
        val unchecked = setOf("Mihon/kagurabachi/54", "Mihon/kaiju/118", "Mihon/kaiju/119")
        TranslateState(
            series = series,
            selected = series.flatMap { s -> s.chapters.map { it.mihonPath } }.filterNot { it in unchecked }.toSet(),
            expanded = setOf("kagurabachi"),
        )
    }

    val settings = SettingsState()

    val translationSettings = TranslationSettingsState()

    val onboarding = OnboardingState()

    val empty = EmptyState()

    val offline = OfflineState()

    val permission = PermissionState()
}
