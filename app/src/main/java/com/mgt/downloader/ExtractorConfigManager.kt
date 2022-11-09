package com.mgt.downloader

import com.mgt.downloader.di.DI.config
import com.mgt.downloader.di.DI.gson
import com.mgt.downloader.di.DI.utils
import com.mgt.downloader.serialize_model.ExtractorConfig
import com.mgt.downloader.utils.*
import kotlin.reflect.KClass

class ExtractorConfigManager {
    private val memCacheExtractorConfig = HashMap<String, ExtractorConfig>()

    fun <T : ExtractorConfig> getConfig(
        extractorName: String,
        extractorConfigClass: KClass<T>
    ): T? {
        getMemCached(extractorName, extractorConfigClass)?.let {
            logD(TAG, "getExtractorConfig: from mem, value: $it")
            return it
        }
        getFileCache(extractorName, extractorConfigClass)?.let {
            logD(TAG, "getExtractorConfig: from file, value: $it")
            updateMemCache(extractorName, it)
            return it
        }
        getDefault(extractorName, extractorConfigClass)?.let {
            logD(TAG, "getExtractorConfig: from default, value: $it")
            updateMemCache(extractorName, it)
            updateFileCache(extractorName, it)
            return it
        }
        return null
    }

    fun updateMemCache(extractorName: String, extractorConfig: ExtractorConfig) {
        memCacheExtractorConfig[extractorName] = extractorConfig
    }

    fun updateFileCache(extractorName: String, extractorConfig: ExtractorConfig) {
        runCatching {
            val cacheFile = utils.getCacheFile(App.instance, "extractor_config", extractorName)
            utils.writeOutputStream(cacheFile.outputStream(), extractorConfig.toJson())
        }.onFailure {
            recordNonFatalException(it)
        }
    }

    private fun <T : ExtractorConfig> getMemCached(
        extractorName: String,
        extractorConfigClass: KClass<T>
    ): T? {
        return memCacheExtractorConfig[extractorName] as? T
    }

    private fun <T : ExtractorConfig> getFileCache(
        extractorName: String,
        extractorConfigClass: KClass<T>
    ): T? {
        return runCatching {
            val cacheFile = utils.getCacheFile(App.instance, "extractor_config", extractorName)
            val cachedConfigString = utils.readInputStream(cacheFile.inputStream())
            if (cachedConfigString.isEmpty()) {
                return null
            }
            extractorConfigClass.fromJson(cachedConfigString)
        }.getOrNull()
    }

    private fun <T : ExtractorConfig> getDefault(
        extractorName: String,
        extractorConfigClass: KClass<T>
    ): T? {
        return runCatching {
            extractorConfigClass.fromJson(
                utils.readInputStream(App.instance.assets.open("extractor_config/$extractorName.json"))
            )
        }.getOrNull()
    }

    fun <T : ExtractorConfig> getRemote(
        extractorName: String,
        extractorConfigClass: KClass<T>
    ): T? {
        return runCatching {
            val json =
                utils.getDontpadContent(config.getDontpadExtractorConfigUrl(extractorName))
            gson.fromJson(json, extractorConfigClass.java)
        }.getOrNull()
    }
}
