package com.example.smsexpensertracker.data

import androidx.room.TypeConverter
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.Instant

/**
 * Converters for Room to store [OffsetDateTime] as epoch milliseconds.
 */
object Converters {
    @TypeConverter
    @JvmStatic
    fun fromEpochMillis(value: Long?): OffsetDateTime? =
        value?.let { OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

    @TypeConverter
    @JvmStatic
    fun toEpochMillis(dateTime: OffsetDateTime?): Long? = dateTime?.toInstant()?.toEpochMilli()
}
