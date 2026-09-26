package com.tmm.mobile.protocol

import kotlinx.serialization.Serializable

// ponytail: forme minimale tiree en avant pour ChapterQualityEntity (E5) ; le modele complet
// (kind = "source_copy" | "source_text" | ...) est detaille dans le protocole en E7.
@Serializable
data class QualityAlert(
    val kind: String,
    val page: Int,
)
