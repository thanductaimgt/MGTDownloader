package com.mgt.downloader

import android.content.Context
import android.util.Log
import androidx.multidex.MultiDexApplication
import androidx.room.Room
import com.google.android.gms.ads.MobileAds
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mgt.downloader.data_model.FilePreviewInfo
import com.mgt.downloader.data_model.ZipNode
import com.mgt.downloader.helper.ConnectionLiveData
import com.mgt.downloader.repository.IDMDatabase
import com.mgt.downloader.rxjava.Disposable
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.*
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

        loadConfigurations(applicationContext)
        loadStatistics(applicationContext)
        initDownloadExecutorService()

        checkUpdateRequestHeaders()

        MobileAds.initialize(this)
        reviewManager = ReviewManagerFactory.create(this)
    }

    private fun checkUpdateRequestHeaders() {
        SingleObservable.fromCallable(unboundExecutorService) {
            getRequestHeaders()
        }.subscribe(object : SingleObserver<Map<String, String>> {
            override fun onSubscribe(disposable: Disposable) {

            }

            override fun onSuccess(result: Map<String, String>) {
                Log.d(TAG, "Obtained headers: $result")
                Configurations.requestHeaders = result
            }
        })
    }

    private fun getRequestHeaders(): Map<String, String> {
        val streamMap = Utils.getContent(Constants.API_GENERAL_HEADERS)

        val json = streamMap.findValue("<textarea(.*?)>", "</textarea>", "", false).unescapeHtml()
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        return Gson().fromJson(json, mapType)
    }

    companion object {
        lateinit var appContext: Context
        lateinit var reviewManager: ReviewManager
        lateinit var database: IDMDatabase
        var isLogEnabled = true
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
            boundExecutorService.shutdown()
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

        fun loadConfigurations(context: Context) {
            Configurations.maxConcurDownloadNum =
                Utils.getSharePreference(context)
                    .getInt(Configurations.MAX_CONCUR_DOWNLOAD_NUM_KEY, 4)
        }

        fun loadStatistics(context: Context) {
            val prefs = Utils.getSharePreference(context)

            Statistics.apply {
                totalDownloadSize = prefs.getLong(TOTAL_DOWNLOAD_SIZE_KEY, 0)
                successDownloadNum = prefs.getInt(SUCCESS_DOWNLOAD_NUM_KEY, 0)
                cancelOrFailDownloadNum = prefs.getInt(CANCEL_OR_FAIL_DOWNLOAD_NUM_KEY, 0)
            }
        }
    }
}