package com.tmm.mobile.mihon

import java.security.MessageDigest

// Reproduit data/DiskUtil.kt et download/DownloadProvider.kt de mihonapp/mihon (Apache 2.0),
// commit 532575e29e196ed59a5457fd4b4c6af5e0740287. disallowNonAscii n'est pas repris (pas de
// preference equivalente cote TMM Mobile).
object MihonNaming {
    const val MAX_FILE_NAME_BYTES = 240
    const val TMP_DIR_SUFFIX = "_tmp"

    private val INVALID_CHARS = charArrayOf('|', '\\', '?', '*', '<', '"', ':', '>', '/')

    fun isValidFatFilenameChar(c: Char): Boolean {
        if (c.code in 0x00..0x1f) return false
        return c !in INVALID_CHARS && c.code != 0x7f
    }

    fun buildValidFilename(origName: String, maxBytes: Int = MAX_FILE_NAME_BYTES): String {
        val name = origName.trim('.', ' ')
        if (name.isEmpty()) return "(invalid)"
        val sb = StringBuilder()
        for (c in name) sb.append(if (isValidFatFilenameChar(c)) c else '_')
        return truncateToLength(sb.toString(), maxBytes)
    }

    fun truncateToLength(text: String, maxBytes: Int): String {
        var bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return text
        var end = maxBytes
        // recule tant qu'on coupe au milieu d'un caractere UTF-8 multi-octets
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return String(bytes, 0, end, Charsets.UTF_8)
    }

    fun getSourceDirName(sourceName: String): String = buildValidFilename(sourceName)

    // HttpSource.toString() = "$name (${lang.uppercase()})" : Mihon range ses telechargements sous
    // "MangaDex (EN)", "Comick (PT-BR)", "Komga (ALL)"... alors que la sauvegarde .tachibk ne porte
    // que le nom ("MangaDex"), sans la langue.
    private val LANG_SUFFIX = Regex(""" \(([A-Z]{2,3}(-[A-Z0-9]{2,8})?)\)$""")

    /** Nom de dossier source sans le suffixe de langue Mihon (" (EN)"), ou tel quel s'il n'en a pas. */
    fun stripSourceLang(sourceDirName: String): String = sourceDirName.replace(LANG_SUFFIX, "")

    fun getMangaDirName(title: String): String = buildValidFilename(title)

    // Mihon colle la team au nom par "_" ("unofficial_Ch. 22_14b780"). TMM ecrivait "team: nom", devenu
    // "team_ nom" : aucun chapitre d'une team n'etait reconnu et restait « Pas telecharge ».
    fun getChapterDirName(chapterName: String, scanlator: String?, chapterUrl: String): String =
        chapterDirName(if (!scanlator.isNullOrBlank()) "${scanlator}_${chapterName.ifBlank { "Chapter" }}" else chapterName.ifBlank { "Chapter" }, chapterUrl)

    /**
     * Noms sous lesquels Mihon a pu ranger ce chapitre : le nom actuel, l'ancien separateur "team: nom"
     * (versions de Mihon/TMM precedentes), et les anciens dossiers sans hachage d'URL.
     */
    fun getValidChapterDirNames(chapterName: String, scanlator: String?, chapterUrl: String): Set<String> {
        val current = getChapterDirName(chapterName, scanlator, chapterUrl)
        val names = linkedSetOf(current, current.substringBeforeLast('_'))
        if (!scanlator.isNullOrBlank()) {
            val colon = chapterDirName("$scanlator: $chapterName", chapterUrl)
            names += colon
            names += colon.substringBeforeLast('_')
        }
        return names
    }

    private fun chapterDirName(base: String, chapterUrl: String): String {
        val hash = md5(chapterUrl).take(6)
        val truncated = buildValidFilename(base, MAX_FILE_NAME_BYTES - 11)
        return "${truncated}_$hash"
    }

    fun isIncompleteChapterDirName(dirName: String): Boolean = dirName.endsWith(TMP_DIR_SUFFIX)

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
