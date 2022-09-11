package com.mgt.downloader

//import com.google.android.gms.ads.MobileAds
import android.app.Application
import androidx.multidex.MultiDexApplication
import com.mgt.downloader.di.DI.boundExecutorService
import com.mgt.downloader.di.DI.downloadConfig
import com.mgt.downloader.di.DI.prefs
import com.mgt.downloader.di.DI.statistics
import com.mgt.downloader.nonserialize_model.FilePreviewInfo
import com.mgt.downloader.nonserialize_model.ZipNode
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class App : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        loadConfigurations()
        loadStatistics()
        initDownloadExecutorService()

//        MobileAds.initialize(this)
    }

    companion object {
        lateinit var instance: Application
        val zipTreeCaches = HashMap<String, ZipNode>()
        val fileInfoCaches = HashMap<String, FilePreviewInfo>()

        fun resetDownloadExecutorService() {
            boundExecutorService.shutdownNow()
            initDownloadExecutorService()
        }

        private fun initDownloadExecutorService() {
            boundExecutorService = ThreadPoolExecutor(
                downloadConfig.maxConcurDownloadNum,
                downloadConfig.maxConcurDownloadNum,
                Long.MAX_VALUE,
                TimeUnit.MINUTES,
                LinkedBlockingDeque()
            )
        }

        fun loadConfigurations() {
            downloadConfig.maxConcurDownloadNum = prefs.getMaxConcurDownloadNum()
        }

        fun loadStatistics() {
            statistics.apply {
                totalDownloadSize = prefs.getTotalDownloadSize()
                successDownloadNum = prefs.getSuccessDownloadNum()
                cancelOrFailDownloadNum = prefs.getCanceledOrFailDownloadNum()
            }
        }
    }
}