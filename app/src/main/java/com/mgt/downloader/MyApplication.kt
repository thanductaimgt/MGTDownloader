package com.mgt.downloader

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.room.Room
import com.google.android.gms.ads.MobileAds
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.data_model.ZipNode
import com.mgt.downloader.helper.ConnectionLiveData
import com.mgt.downloader.repository.IDMDatabase
import com.mgt.downloader.utils.Configurations
import com.mgt.downloader.utils.Prefs
import com.mgt.downloader.utils.Statistics
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class MyApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        database = Room.databaseBuilder(applicationContext, IDMDatabase::class.java, "IDM_Database")
            .build()

        liveConnection = ConnectionLiveData(this)

        loadConfigurations()
        loadStatistics()
        initDownloadExecutorService()

        MobileAds.initialize(this)
        reviewManager = ReviewManagerFactory.create(this)
    }

    companion object {
        lateinit var appContext: Context
        lateinit var reviewManager: ReviewManager
        lateinit var database: IDMDatabase
        val zipTreeCaches = HashMap<String, ZipNode>()
        val fileInfoCaches = HashMap<String, FilePreviewInfo>()
        lateinit var liveConnection: ConnectionLiveData

        lateinit var boundExecutorService: ThreadPoolExecutor

        val unboundExecutorService = ThreadPoolExecutor(
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            1,
            TimeUnit.SECONDS,
            LinkedBlockingDeque()
        ).apply { allowCoreThreadTimeOut(true) }

        fun resetDownloadExecutorService() {
            boundExecutorService.shutdownNow()
            initDownloadExecutorService()
        }

        fun initDownloadExecutorService() {
            boundExecutorService = ThreadPoolExecutor(
                Configurations.maxConcurDownloadNum,
                Configurations.maxConcurDownloadNum,
                Long.MAX_VALUE,
                TimeUnit.MINUTES,
                LinkedBlockingDeque()
            )
        }

        fun loadConfigurations() {
            Configurations.maxConcurDownloadNum =
                Prefs.get()
                    .getInt(Configurations.MAX_CONCUR_DOWNLOAD_NUM_KEY, 4)
        }

        fun loadStatistics() {
            val prefs = Prefs.get()

            Statistics.apply {
                totalDownloadSize = prefs.getLong(TOTAL_DOWNLOAD_SIZE_KEY, 0)
                successDownloadNum = prefs.getInt(SUCCESS_DOWNLOAD_NUM_KEY, 0)
                cancelOrFailDownloadNum = prefs.getInt(CANCEL_OR_FAIL_DOWNLOAD_NUM_KEY, 0)
            }
        }
    }
}