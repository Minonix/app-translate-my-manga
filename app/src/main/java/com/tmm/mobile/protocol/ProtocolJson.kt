package com.tmm.mobile.protocol

import kotlinx.serialization.json.Json

val ProtocolJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}
