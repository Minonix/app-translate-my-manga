package com.tmm.mobile.ui.screens.series

import org.junit.Assert.assertEquals
import org.junit.Test

class DragSelectionTest {
    private val order = listOf("1", "2", "3", "4", "5", "6")
    private val all = order.toSet()
    private val none: (String) -> Set<String> = { emptySet() }

    private fun drag(base: Set<String>, anchor: String, current: String, adding: Boolean, selectable: Set<String> = all, same: (String) -> Set<String> = none) =
        dragSelection(order, selectable, same, base, anchor, current, adding)

    @Test
    fun dragDown_selectsEveryChapterPassedOver() {
        assertEquals(setOf("2", "3", "4", "5"), drag(emptySet(), "2", "5", adding = true))
    }

    @Test
    fun dragUp_selectsEveryChapterPassedOver() {
        assertEquals(setOf("2", "3", "4", "5"), drag(emptySet(), "5", "2", adding = true))
    }

    @Test
    fun goingBack_restoresChaptersLeftBehind() {
        // Glisse de 2 a 5 puis revient a 3 : 4 et 5 reprennent leur etat d'avant le geste.
        val base = setOf("5")
        assertEquals(setOf("2", "3", "4", "5"), drag(base, "2", "5", adding = true))
        assertEquals(setOf("2", "3", "5"), drag(base, "2", "3", adding = true))
        // Puis passe au-dessus de l'ancre : 1 et 2 cochés, 3 rendu.
        assertEquals(setOf("1", "2", "5"), drag(base, "2", "1", adding = true))
    }

    @Test
    fun startingOnASelectedChapter_unselectsTheRange() {
        assertEquals(setOf("1", "6"), drag(all, "2", "5", adding = false))
    }

    @Test
    fun lockedChaptersAreSkipped() {
        assertEquals(setOf("2", "4"), drag(emptySet(), "2", "4", adding = true, selectable = all - "3"))
    }

    @Test
    fun keepsOneEditionPerNumber_R2() {
        // 3 et 4 sont deux editions du meme numero : la derniere survolee reste seule cochee.
        val same: (String) -> Set<String> = { if (it == "3" || it == "4") setOf("3", "4") else emptySet() }
        assertEquals(setOf("2", "4", "5"), drag(emptySet(), "2", "5", adding = true, same = same))
    }

    @Test
    fun unknownKey_leavesSelectionUnchanged() {
        assertEquals(setOf("1"), drag(setOf("1"), "2", "missing", adding = true))
    }
}
