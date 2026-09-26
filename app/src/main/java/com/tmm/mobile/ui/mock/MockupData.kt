package com.tmm.mobile.ui.mock

import com.tmm.mobile.ui.components.StatusTone

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
}
