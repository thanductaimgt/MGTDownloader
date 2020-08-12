package com.mgt.downloader.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mgt.downloader.data_model.DownloadTask
import com.mgt.downloader.helper.ArrayTypeConverter
import com.mgt.downloader.helper.DateTypeConverter

@Database(entities = [DownloadTask::class], version = 1, exportSchema = false)
@TypeConverters(DateTypeConverter::class, ArrayTypeConverter::class)
abstract class IDMDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
}