package com.tmm.mobile.core

// ponytail: tampon en memoire seulement (pas de fichier tournant) ; le fichier reel avec
// export/masquage PII arrive en E10 (ecran journal). Ne jamais y ecrire d'en-tete ni d'URL
// de session (regle du protocole Drive, 5.10).
object AppLog {
    private const val MAX_LINES = 2000
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun d(tag: String, message: String) {
        lines.addLast("$tag: $message")
        if (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")
}
