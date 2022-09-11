package com.mgt.downloader.utils

import com.mgt.downloader.di.DI.prefs
import com.mgt.downloader.ui.MainActivity

class DownloadConfig {
    var maxConcurDownloadNum = DEFAULT_MAX_CONCUR_DOWNLOAD_NUM
        get() = prefs.getMaxConcurDownloadNum()
        set(value) {
            field = value
            prefs.setMaxConcurDownloadNum(value)
        }

    var multiThreadDownloadNum = DEFAULT_MULTI_THREAD_DOWNLOAD_NUM
        get() = prefs.getMultiThreadDownloadNum()
        set(value) {
            field = value
            prefs.setMultiThreadDownloadNum(value)
        }

    fun setMaxConcurDownloadNum(value: Int, mainActivity: MainActivity) {
        maxConcurDownloadNum = value
        mainActivity.applyCurrentConfigs()
    }

    companion object {
        const val DEFAULT_MAX_CONCUR_DOWNLOAD_NUM = 4
        const val DEFAULT_MULTI_THREAD_DOWNLOAD_NUM = 4

        const val MAX_CONCUR_DOWNLOAD_NUM_LOWER_BOUND = 1
        const val MAX_CONCUR_DOWNLOAD_NUM_UPPER_BOUND = 8

        const val MULTI_THREAD_DOWNLOAD_NUM_LOWER_BOUND = 2
        const val MULTI_THREAD_DOWNLOAD_NUM_UPPER_BOUND = 8
    }
}
