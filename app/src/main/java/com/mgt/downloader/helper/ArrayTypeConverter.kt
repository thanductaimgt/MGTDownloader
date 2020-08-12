package com.mgt.downloader.helper

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ArrayTypeConverter {

    @TypeConverter
    fun fromArrayList(arrayList: ArrayList<Long>): String {
        return Gson().toJson(arrayList)
    }

    @TypeConverter
    fun fromString(json: String): ArrayList<Long> {
        return Gson().fromJson(json, object : TypeToken<ArrayList<Long>>() {}.type)
    }
}