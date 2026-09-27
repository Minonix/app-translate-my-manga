package com.tmm.mobile.core

import java.time.LocalDate

// ponytail: tampon en memoire seulement (pas de fichier tournant) ; l'export reel avec
// masquage PII (E10.4, PLAN_GEMINI.md 5.7/7.8) lit entries() et passe par journalExportText().
// Ne jamais y ecrire d'en-tete ni d'URL de session (regle du protocole Drive, 5.10) - le masquage
// ci-dessous est une seconde barriere, pas la seule.
object AppLog {
    data class Entry(val atMillis: Long, val tag: String, val message: String)

    private const val MAX_LINES = 2000
    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun d(tag: String, message: String) {
        entries.addLast(Entry(System.currentTimeMillis(), tag, message))
        if (entries.size > MAX_LINES) entries.removeFirst()
    }

    @Synchronized
    fun dump(): String = entries.joinToString("\n") { "${it.tag}: ${it.message}" }

    @Synchronized
    fun entries(): List<Entry> = entries.toList()
}

private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val AUTH_HEADER_REGEX = Regex("(?i)(authorization\\s*[:=]\\s*)\\S+")

// PLAN_GEMINI.md E10.4 : nom du fichier exporte, aaaa-mm-jj (LocalDate.toString() est deja ISO-8601).
fun journalExportFileName(today: LocalDate): String = "tmm-journaux-$today.txt"

// PLAN_GEMINI.md E10.4 : contenu des 7 derniers jours de AppLog, email et Authorization masques.
fun journalExportText(entries: List<AppLog.Entry>, nowMillis: Long, days: Long = 7): String {
    val cutoffMillis = nowMillis - days * 24 * 60 * 60 * 1000
    val body = entries.filter { it.atMillis >= cutoffMillis }.joinToString("\n") { "${it.tag}: ${it.message}" }
    return maskSensitive(body)
}

fun maskSensitive(text: String): String =
    text.replace(EMAIL_REGEX, "***@***").replace(AUTH_HEADER_REGEX) { "${it.groupValues[1]}***" }
