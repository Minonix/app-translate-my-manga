package com.tmm.mobile.protocol

import kotlinx.serialization.json.Json

// ponytail: instance minimale tiree en avant pour les Converters Room de E5 ;
// le reste du protocole (RequestJson, ResultJson, etc.) arrive en E7.
val ProtocolJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
}
