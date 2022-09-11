package com.mgt.downloader.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mgt.downloader.serialize_model.DownloadTask

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM DownloadTask where state = :state")
    fun getAllTasksWithState(state: Int): List<DownloadTask>

    @Query("SELECT * FROM DownloadTask")
    fun getAllTasks(): List<DownloadTask>

    @Query("SELECT * FROM DownloadTask where fileName = :fileName")
    fun getDownloadTask(fileName: String): DownloadTask

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDownloadTask(downloadTask: DownloadTask)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDownloadTasks(downloadTasks: List<DownloadTask>)

    @Query("DELETE FROM DownloadTask where fileName = :fileName")
    fun deleteDownloadTask(fileName: String)
}