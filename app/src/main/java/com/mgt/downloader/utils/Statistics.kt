package com.mgt.downloader.utils

import com.mgt.downloader.di.DI.prefs


class Statistics {
    var totalDownloadSize: Long = 0
    var successDownloadNum: Int = 0
    var cancelOrFailDownloadNum: Int = 0
    val totalDownloadNum: Int
        get() = successDownloadNum + cancelOrFailDownloadNum

    @Synchronized
    fun increaseCanceledOrFailDownloadNum() {
        prefs.setCanceledOrFailDownloadNum(++cancelOrFailDownloadNum)
    }

    @Synchronized
    fun increaseSuccessDownloadNum() {
        prefs.setSuccessDownloadNum(++successDownloadNum)
    }

    @Synchronized
    fun increaseTotalDownloadSize(size: Number) {
        totalDownloadSize += size.toLong()

        prefs.setTotalDownloadSize(totalDownloadSize)
    }
}