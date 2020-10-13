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
import com.mgt.downloader.rxjava.Disposable
import com.mgt.downloader.rxjava.SingleObservable
import com.mgt.downloader.rxjava.SingleObserver
import com.mgt.downloader.utils.Configurations
import com.mgt.downloader.utils.Statistics
import com.mgt.downloader.utils.Utils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

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
            getHeaders()
        }.subscribe(object :SingleObserver<Map<String, String>>{
            override fun onSubscribe(disposable: Disposable) {

            }

            override fun onSuccess(result: Map<String, String>) {
                Configurations.requestHeaders = result
            }
        })
    }

    private fun getHeaders():Map<String , String>{
        val conn = Utils.openConnection("http://dontpad.com/tdtai/mgtdownloader/requestheaders")

        var reader: BufferedReader? = null
        val streamMap = StringBuilder()
        try {
            reader =
                BufferedReader(InputStreamReader(conn.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                streamMap.append("$line\n")
            }
        } finally {
            reader?.close()
            conn.disconnect()
        }

        val pattern =
            Pattern.compile("<textarea((.|\n)*?)</textarea>")
        val matcher = pattern.matcher(streamMap)
        val headersRaw = if (matcher.find()) {
            matcher.group().let {
                it.substring(it.indexOf('>')+1, it.length - 11)
            }
        } else {
            ""
        }

        return headersRaw.split("\n").fold(HashMap(), { map, header ->
            val entry = header.split(':', limit = 2)
            map[entry[0]] = entry[1]
            map
        })
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