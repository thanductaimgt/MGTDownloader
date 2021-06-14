package com.mgt.downloader.utils


object Statistics {
    var totalDownloadSize: Long = 0
    var successDownloadNum: Int = 0
    var cancelOrFailDownloadNum: Int = 0
    var totalDownloadNum: Int = 0
        get() = successDownloadNum + cancelOrFailDownloadNum
        private set

    const val SUCCESS_DOWNLOAD_NUM_KEY = "SUCCESS_DOWNLOAD_NUM_KEY"
    const val CANCEL_OR_FAIL_DOWNLOAD_NUM_KEY = "CANCEL_OR_FAIL_DOWNLOAD_NUM_KEY"

    const val TOTAL_DOWNLOAD_SIZE_KEY = "TOTAL_DOWNLOAD_SIZE_KEY"

    @Synchronized
    fun increaseDownloadNum(numKey: String) {
        val newNum = when (numKey) {
            SUCCESS_DOWNLOAD_NUM_KEY -> ++successDownloadNum
            else -> ++cancelOrFailDownloadNum
        }

        Prefs.edit {
            putInt(numKey, newNum)
        }
    }

    @Synchronized
    fun increaseTotalDownloadSize(size: Number) {
        totalDownloadSize += size.toLong()

        Prefs.edit {
            putLong(TOTAL_DOWNLOAD_SIZE_KEY, totalDownloadSize)
        }
    }
}