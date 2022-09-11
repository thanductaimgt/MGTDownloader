package com.mgt.downloader.utils

import com.google.gson.reflect.TypeToken
import com.mgt.downloader.BuildConfig
import com.mgt.downloader.R
import com.mgt.downloader.di.DI.app
import com.mgt.downloader.di.DI.gson
import com.mgt.downloader.di.DI.prefs
import com.mgt.downloader.di.DI.utils
import org.json.JSONObject

class Config {
    var newestVersionCode = utils.getAppVersionCode()
    var requestHeaders = emptyMap<String, String>()

    private fun getBaseManagementUrl(): String {
        return app.getString(
            when (getEnv()) {
                Env.TEST -> R.string.BASE_URL_MANAGEMENT_TEST
                Env.LIVE -> R.string.BASE_URL_MANAGEMENT_LIVE
            }
        )
    }

    fun getDontpadExtractorConfigUrl(extractorName: String): String {
        return "${getBaseManagementUrl()}extractor_config/$extractorName"
    }

    fun getDontpadAppConfigUrl(): String {
        return "${getBaseManagementUrl()}app_config"
    }

    fun getEnv(): Env {
        val envString = prefs.getEnv()
        return if (envString != null) {
            Env.fromString(envString)
        } else {
            getDefaultEnv()
        }
    }

    fun setEnv(env: Env) {
        prefs.setEnv(env.toString())
    }

    private fun getDefaultEnv(): Env {
        return if (BuildConfig.DEBUG) {
            Env.TEST
        } else {
            Env.LIVE
        }
    }

    fun getAppDbName(): String {
        return when (getEnv()) {
            Env.TEST -> "IDM_Database_test"
            Env.LIVE -> "IDM_Database"
        }
    }

    fun getEnvDependentPrefsName(): String {
        return when (getEnv()) {
            Env.TEST -> "IDM Share Preferences Test"
            Env.LIVE -> "IDM Share Preferences"
        }
    }

    fun getEnvIndependentPrefsName(): String {
        return "ENV_INDEP_PREFS"
    }

    fun updateAppConfig(config: String) {
        if (config.isBlank()) return

        val configObj = JSONObject(config)
        newestVersionCode = configObj.optInt("newestVersionCode")

        val headersJson = (configObj.optJSONObject("requestHeaders") ?: JSONObject()).toString()
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        requestHeaders = gson.fromJson(headersJson, mapType) ?: emptyMap()
    }

    enum class Env {
        TEST,
        LIVE;

        companion object {
            fun fromString(value: String): Env {
                return when (value) {
                    TEST.toString() -> TEST
                    else -> LIVE
                }
            }
        }
    }
}