package com.tmm.mobile.core

import java.util.Locale
import kotlin.math.roundToInt

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS
private const val GO = 1_000_000_000.0
private const val MO = 1_000_000.0

object Formatters {

    fun relativeFr(t: Long, now: Long): String {
        val delta = now - t
        return when {
            delta < MINUTE_MS -> "à l'instant"
            delta < HOUR_MS -> "il y a ${delta / MINUTE_MS} min"
            delta < DAY_MS -> "il y a ${delta / HOUR_MS} h"
            else -> {
                val days = delta / DAY_MS
                "il y a $days jour${if (days > 1) "s" else ""}"
            }
        }
    }

    // ponytail: pas de cas "instant" documente pour sinceFr, on garde le meme seuil que
    // relativeFr et on affiche "depuis moins d'une min" en dessous de 60s.
    fun sinceFr(t: Long, now: Long): String {
        val delta = now - t
        return when {
            delta < MINUTE_MS -> "depuis moins d'une min"
            delta < HOUR_MS -> "depuis ${delta / MINUTE_MS} min"
            delta < DAY_MS -> "depuis ${delta / HOUR_MS} h"
            else -> {
                val days = delta / DAY_MS
                "depuis $days jour${if (days > 1) "s" else ""}"
            }
        }
    }

    fun sizeFr(bytes: Long): String {
        return if (bytes < GO) {
            "${(bytes / MO).roundToInt()} Mo"
        } else {
            val go = bytes / GO
            String.format(Locale.FRANCE, "%.1f Go", go)
        }
    }

    fun chapterRangeFr(numbers: List<Float>): String {
        if (numbers.isEmpty()) return ""
        fun fmt(n: Float) = if (n == n.toInt().toFloat()) n.toInt().toString() else n.toString()
        val min = numbers.min()
        val max = numbers.max()
        return if (numbers.size == 1 || min == max) "Ch. ${fmt(min)}" else "Ch. ${fmt(min)} à ${fmt(max)}"
    }

    fun langLabelFr(code: String): String = when (code) {
        "auto" -> "détection auto"
        "JPN" -> "japonais"
        "KOR" -> "coréen"
        "CHS" -> "chinois"
        "FRA" -> "français"
        "ENG" -> "anglais"
        "SPA" -> "espagnol"
        "DEU" -> "allemand"
        else -> code
    }
}
