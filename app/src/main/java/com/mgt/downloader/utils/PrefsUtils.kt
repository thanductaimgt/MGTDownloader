package com.mgt.downloader.utils

import android.content.Context
import android.content.SharedPreferences
import com.mgt.downloader.MyApplication

object Prefs {
    const val KEY_EXTRACT_FIELDS = "EXTRACT_FIELDS_%s"

    private val prefs by lazy {
        MyApplication.appContext.getSharedPreferences(
            Constants.SHARE_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    }

    fun edit(transactions: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply {
            transactions()
            apply()
        }
    }

    fun get(): SharedPreferences = prefs
}