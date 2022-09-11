package com.mgt.downloader.utils

import android.content.Context
import com.mgt.downloader.di.DI.app
import com.mgt.downloader.di.DI.config

class Prefs {
    private val envDepPrefs by lazy {
        app.getSharedPreferences(config.getEnvDependentPrefsName(), Context.MODE_PRIVATE)
    }

    private val envIndepPrefs by lazy {
        app.getSharedPreferences(config.getEnvIndependentPrefsName(), Context.MODE_PRIVATE)
    }

    fun getEnv(): String? {
        val envString = envIndepPrefs.getString(KEY_ENV, "")
        if (envString == null || envString.isEmpty()) {
            return null
        }
        return envString
    }

    fun setEnv(env: String) {
        envIndepPrefs.edit().putString(KEY_ENV, env).commit()
    }

    fun getMaxConcurDownloadNum(): Int {
        return envDepPrefs.getInt(
            MAX_CONCUR_DOWNLOAD_NUM_KEY,
            DownloadConfig.DEFAULT_MAX_CONCUR_DOWNLOAD_NUM
        )
    }

    fun setMaxConcurDownloadNum(value: Int) {
        envDepPrefs.edit()
            .putInt(
                MAX_CONCUR_DOWNLOAD_NUM_KEY,
                value
            )
            .apply()
    }

    fun getMultiThreadDownloadNum(): Int {
        return envDepPrefs.getInt(
            MULTI_THREAD_DOWNLOAD_NUM_KEY,
            DownloadConfig.DEFAULT_MULTI_THREAD_DOWNLOAD_NUM
        )
    }

    fun setMultiThreadDownloadNum(value: Int) {
        envDepPrefs.edit()
            .putInt(
                MULTI_THREAD_DOWNLOAD_NUM_KEY,
                value
            ).apply()
    }

    fun getTotalDownloadSize(): Long {
        return envDepPrefs.getLong(TOTAL_DOWNLOAD_SIZE_KEY, 0)
    }

    fun setTotalDownloadSize(value: Long) {
        envDepPrefs.edit()
            .putLong(TOTAL_DOWNLOAD_SIZE_KEY, value)
            .apply()
    }

    fun getSuccessDownloadNum(): Int {
        return envDepPrefs.getInt(SUCCESS_DOWNLOAD_NUM_KEY, 0)
    }

    fun setSuccessDownloadNum(value: Int) {
        envDepPrefs.edit()
            .putInt(SUCCESS_DOWNLOAD_NUM_KEY, value)
            .apply()
    }

    fun getCanceledOrFailDownloadNum(): Int {
        return envDepPrefs.getInt(CANCEL_OR_FAIL_DOWNLOAD_NUM_KEY, 0)
    }

    fun setCanceledOrFailDownloadNum(value: Int) {
        envDepPrefs.edit()
            .putInt(CANCEL_OR_FAIL_DOWNLOAD_NUM_KEY, value)
            .apply()
    }

    companion object {
        // env dependent
        const val MAX_CONCUR_DOWNLOAD_NUM_KEY = "MAX_CONCUR_DOWNLOAD_NUM_KEY"
        const val MULTI_THREAD_DOWNLOAD_NUM_KEY = "MULTI_THREAD_DOWNLOAD_NUM_KEY"
        const val SUCCESS_DOWNLOAD_NUM_KEY = "SUCCESS_DOWNLOAD_NUM_KEY"
        const val CANCEL_OR_FAIL_DOWNLOAD_NUM_KEY = "CANCEL_OR_FAIL_DOWNLOAD_NUM_KEY"
        const val TOTAL_DOWNLOAD_SIZE_KEY = "TOTAL_DOWNLOAD_SIZE_KEY"

        // env independent
        const val KEY_ENV = "KEY_ENV"
    }
}