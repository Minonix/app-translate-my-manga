package com.tmm.mobile.data

import com.tmm.mobile.protocol.MihonPath

// Room stocke le mihonPath aplati ("<sourceDir>/<seriesDir>/<chapterDir>"), comme DownloadScanner
// et JobEntity/PcIndexChapterEntity/DownloadedChapterEntity ; MihonPath (protocole) reste structure.
fun MihonPath.flatten(): String = "$source/$series/$chapter"
