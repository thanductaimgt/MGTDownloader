package com.mgt.downloader.utils

import com.mgt.downloader.ui.MainActivity

object Configurations {
    const val MAX_CONCUR_DOWNLOAD_NUM_KEY = "MAX_CONCUR_DOWNLOAD_NUM_KEY"
    const val MULTI_THREAD_DOWNLOAD_NUM_KEY = "MULTI_THREAD_DOWNLOAD_NUM_KEY"

    const val DEFAULT_MAX_CONCUR_DOWNLOAD_NUM = 4
    const val DEFAULT_MULTI_THREAD_DOWNLOAD_NUM = 4

    const val MAX_CONCUR_DOWNLOAD_NUM_LOWER_BOUND = 1
    const val MAX_CONCUR_DOWNLOAD_NUM_UPPER_BOUND = 8

    const val MULTI_THREAD_DOWNLOAD_NUM_LOWER_BOUND = 2
    const val MULTI_THREAD_DOWNLOAD_NUM_UPPER_BOUND = 8

    var maxConcurDownloadNum = DEFAULT_MAX_CONCUR_DOWNLOAD_NUM
    var multiThreadDownloadNum = DEFAULT_MULTI_THREAD_DOWNLOAD_NUM

    var requestHeaders: Map<String, String> = HashMap()

    fun setMaxConcurDownloadNum(value: Int, mainActivity: MainActivity) {
        maxConcurDownloadNum = value
        Utils.getSharePreference(mainActivity).edit().apply {
            putInt(
                MAX_CONCUR_DOWNLOAD_NUM_KEY,
                maxConcurDownloadNum
            )
            apply()
        }
        mainActivity.applyCurrentConfigs()
    }

    fun setMultiThreadDownloadNum(value: Int, mainActivity: MainActivity) {
        multiThreadDownloadNum = value
        Utils.getSharePreference(mainActivity).edit().apply {
            putInt(
                MULTI_THREAD_DOWNLOAD_NUM_KEY,
                multiThreadDownloadNum
            )
            apply()
        }
    }
}