package com.tmm.mobile.data.db

import androidx.room.TypeConverter
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.QualityAlert
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class Converters {
    @TypeConverter
    fun fromIntList(value: List<Int>?): String? =
        value?.let { ProtocolJson.encodeToString(ListSerializer(Int.serializer()), it) }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? =
        value?.let { ProtocolJson.decodeFromString(ListSerializer(Int.serializer()), it) }

    @TypeConverter
    fun fromQualityAlertList(value: List<QualityAlert>?): String? =
        value?.let { ProtocolJson.encodeToString(ListSerializer(QualityAlert.serializer()), it) }

    @TypeConverter
    fun toQualityAlertList(value: String?): List<QualityAlert>? =
        value?.let { ProtocolJson.decodeFromString(ListSerializer(QualityAlert.serializer()), it) }
}
