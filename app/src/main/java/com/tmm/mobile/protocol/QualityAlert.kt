package com.tmm.mobile.protocol

import kotlinx.serialization.Serializable

// CdC 7.2 : kind ∈ source_copy, source_text, overflow, too_small. page en base 0.
@Serializable
data class QualityAlert(
    val kind: String,
    val page: Int,
    val label: String = "",
    val repairable: Boolean = false,
)
