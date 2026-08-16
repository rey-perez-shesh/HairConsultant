package com.hairconsultant.app.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Room type converters so we can store simple lists/enums without a normalized schema. */
class Converters {

    @TypeConverter
    fun stringListToJson(value: List<String>?): String = Json.encodeToString(value.orEmpty())

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        runCatching { Json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
}
