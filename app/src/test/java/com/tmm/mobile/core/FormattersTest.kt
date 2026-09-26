package com.tmm.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    private val now = 1_000_000_000L

    @Test fun relativeFr_instant() = assertEquals("à l'instant", Formatters.relativeFr(now - 59_000, now))

    @Test fun relativeFr_minutes() = assertEquals("il y a 1 min", Formatters.relativeFr(now - 60_000, now))

    @Test fun relativeFr_hours() = assertEquals("il y a 59 min", Formatters.relativeFr(now - 59 * 60_000, now))

    @Test fun relativeFr_hoursBoundary() = assertEquals("il y a 1 h", Formatters.relativeFr(now - 60 * 60_000, now))

    @Test fun relativeFr_dayBoundary() = assertEquals("il y a 23 h", Formatters.relativeFr(now - 23 * 3_600_000L, now))

    @Test fun relativeFr_oneDay() = assertEquals("il y a 1 jour", Formatters.relativeFr(now - 24 * 3_600_000L, now))

    @Test fun relativeFr_twoDays() = assertEquals("il y a 2 jours", Formatters.relativeFr(now - 48 * 3_600_000L, now))

    @Test fun sinceFr_minutes() = assertEquals("depuis 1 min", Formatters.sinceFr(now - 60_000, now))

    @Test fun sinceFr_hours() = assertEquals("depuis 1 h", Formatters.sinceFr(now - 3_600_000L, now))

    @Test fun sinceFr_days() = assertEquals("depuis 2 jours", Formatters.sinceFr(now - 48 * 3_600_000L, now))

    @Test fun sizeFr_belowGo() = assertEquals("500 Mo", Formatters.sizeFr(500_000_000L))

    @Test fun sizeFr_justBelowGoRoundsUp() = assertEquals("1000 Mo", Formatters.sizeFr(999_999_999L))

    @Test fun sizeFr_atGo() = assertEquals("1,0 Go", Formatters.sizeFr(1_000_000_000L))

    @Test fun sizeFr_withDecimal() = assertEquals("1,3 Go", Formatters.sizeFr(1_250_000_000L))

    @Test fun chapterRangeFr_single() = assertEquals("Ch. 12", Formatters.chapterRangeFr(listOf(12f)))

    @Test fun chapterRangeFr_singleDecimal() = assertEquals("Ch. 12.5", Formatters.chapterRangeFr(listOf(12.5f)))

    @Test fun chapterRangeFr_multiple() = assertEquals("Ch. 1 à 5", Formatters.chapterRangeFr(listOf(1f, 2f, 3f, 4f, 5f)))

    @Test fun langLabelFr_known() {
        assertEquals("détection auto", Formatters.langLabelFr("auto"))
        assertEquals("japonais", Formatters.langLabelFr("JPN"))
        assertEquals("coréen", Formatters.langLabelFr("KOR"))
        assertEquals("chinois", Formatters.langLabelFr("CHS"))
        assertEquals("français", Formatters.langLabelFr("FRA"))
        assertEquals("anglais", Formatters.langLabelFr("ENG"))
        assertEquals("espagnol", Formatters.langLabelFr("SPA"))
        assertEquals("allemand", Formatters.langLabelFr("DEU"))
    }
}
