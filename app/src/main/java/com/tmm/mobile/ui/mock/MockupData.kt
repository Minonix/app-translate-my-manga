package com.tmm.mobile.ui.mock

import com.tmm.mobile.ui.components.StatusTone
import com.tmm.mobile.ui.screens.main.MainJobType
import com.tmm.mobile.ui.screens.main.MainQueueJob
import com.tmm.mobile.ui.screens.main.MainRecentChapter
import com.tmm.mobile.ui.screens.main.MainState
import com.tmm.mobile.ui.screens.main.PcTone
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
}
