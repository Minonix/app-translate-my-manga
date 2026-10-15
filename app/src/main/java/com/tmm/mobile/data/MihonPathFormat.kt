package com.tmm.mobile.data

import com.tmm.mobile.protocol.MihonPath

// Room stocke le mihonPath aplati ("<sourceDir>/<seriesDir>/<chapterDir>"), comme DownloadScanner
// et JobEntity/PcIndexChapterEntity/DownloadedChapterEntity ; MihonPath (protocole) reste structure.
fun MihonPath.flatten(): String = "$source/$series/$chapter"

/** Inverse de flatten() : aucun des trois noms de dossier ne contient de "/" (MihonNaming). */
fun unflattenMihonPath(flat: String): MihonPath? =
    flat.split('/', limit = 3).takeIf { it.size == 3 }?.let { (source, series, chapter) -> MihonPath(source, series, chapter) }
