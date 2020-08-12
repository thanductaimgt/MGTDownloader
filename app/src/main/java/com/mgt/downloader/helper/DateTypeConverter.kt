package com.mgt.downloader.helper

import androidx.room.TypeConverter
import java.sql.Date

class DateTypeConverter {

    @TypeConverter
    fun toDate(time: Long): Date {
        return Date(time)
    }

    @TypeConverter
    fun toLong(date: Date): Long {
        return date.time
    }
}