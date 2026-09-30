package com.application.alfaflix.data.local.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Converters for the few non-primitive columns.
 *
 * JSON is used rather than a delimiter because genre names can legitimately contain commas,
 * which would corrupt a naive `split(",")` round trip.
 *
 * Decoding is defensive: a row written by an older schema must degrade to an empty list
 * rather than crash the app on read.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun toStringList(value: String): List<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), value)
    }.getOrDefault(emptyList())

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
