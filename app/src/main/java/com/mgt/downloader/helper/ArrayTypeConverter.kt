package com.mgt.downloader.helper

import androidx.room.TypeConverter
import com.google.gson.reflect.TypeToken
import com.mgt.downloader.di.DI.gson

class ArrayTypeConverter {

    @TypeConverter
    fun fromArrayList(arrayList: ArrayList<Long>): String {
        return gson.toJson(arrayList)
    }

    @TypeConverter
    fun fromString(json: String): ArrayList<Long> {
        return gson.fromJson(json, object : TypeToken<ArrayList<Long>>() {}.type)
    }
}