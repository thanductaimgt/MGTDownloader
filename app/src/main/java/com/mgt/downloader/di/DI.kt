package com.mgt.downloader.di

import androidx.room.Room
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.gson.GsonBuilder
import com.mgt.downloader.App
import com.mgt.downloader.ExtractContentManager
import com.mgt.downloader.ExtractorConfigManager
import com.mgt.downloader.factory.ExtractorProvider
import com.mgt.downloader.forbiddenzone.ForbiddenZone
import com.mgt.downloader.helper.ConnectionLiveData
import com.mgt.downloader.repository.IDMDatabase
import com.mgt.downloader.utils.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object DI {
    val config by lazy { Config() }

    val app by lazy { App.instance }

    val prefs by lazy { Prefs() }

    val utils by lazy { Utils() }

    val extractorProvider by lazy { ExtractorProvider() }

    val extractContentManager by lazy { ExtractContentManager() }

    val extractorConfigManager by lazy { ExtractorConfigManager() }

    val gson by lazy {
        GsonBuilder()
            .setLenient()
            .create()
    }

    val statistics by lazy { Statistics() }

    val downloadConfig by lazy { DownloadConfig() }

    val forbiddenZone by lazy { ForbiddenZone() }

    val reviewManager by lazy { ReviewManagerFactory.create(app) }

    val database by lazy {
        Room.databaseBuilder(
            app,
            IDMDatabase::class.java,
            config.getAppDbName()
        ).build()
    }

    val liveConnection by lazy { ConnectionLiveData(app) }

    lateinit var boundExecutorService: ThreadPoolExecutor

    val unboundExecutorService = ThreadPoolExecutor(
        Int.MAX_VALUE,
        Int.MAX_VALUE,
        1,
        TimeUnit.SECONDS,
        LinkedBlockingDeque()
    ).apply { allowCoreThreadTimeOut(true) }

    val perlStringHelper by lazy { PerlStringHelper() }
}
